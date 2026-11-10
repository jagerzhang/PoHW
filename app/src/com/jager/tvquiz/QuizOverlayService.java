package com.jager.tvquiz;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.List;

/**
 * 常驻前台服务：倒计时到点后直接由 WindowManager 添加 TYPE_APPLICATION_OVERLAY 全屏窗口。
 * 关键：加窗口不是 startActivity，因此不受 Android 10+ 后台启动 Activity(BAL) 限制。
 *
 * 审查加固（v1.0.0 首个正式版）：
 *  - FGS 契约：onStartCommand 开头无条件 startForeground()，覆盖 STOP/拒绝/权限不足/
 *    startForeground 抛异常等所有分支，避免 Android 8-11 ANR 与 12+ DidNotStartInTime。
 *  - STOP 授权：不再信任客户端传来的 verified boolean，改由本进程签发一次性令牌。
 *  - 窗口焦点：遮罩失焦时有限次 updateViewLayout 重抢 + 一次 remove/add 重建，
 *    修复「第二次弹题遥控器选不中」在通知栏/系统对话框抢焦点后的等价路径。
 *  - 判题：答题状态统一由 QuizFlow 推进，服务不再单独维护 wrongAttempts。
 */
public class QuizOverlayService extends Service {

    public static final String ACTION_START = "com.jager.tvquiz.START";
    public static final String ACTION_STOP = "com.jager.tvquiz.STOP";
    public static final String ACTION_QUIZ_NOW = "com.jager.tvquiz.QUIZ_NOW";
    public static final String ACTION_RELOAD = "com.jager.tvquiz.RELOAD";
    public static final String ACTION_SYNC = "com.jager.tvquiz.SYNC";
    /** 一次性 STOP 令牌；绝不把「已验证」布尔值作为授权依据。 */
    public static final String EXTRA_STOP_TOKEN = "com.jager.tvquiz.STOP_TOKEN";
    /** 仅用于诊断：记录本次守护由哪条路径拉起（boot/revive/job/accessibility/taskRemoved）。 */
    public static final String EXTRA_VIA = "com.jager.tvquiz.VIA";

    private static final String TAG = "PoHW.Guard";
    private static final int NOTIF_ID = 1;
    private static final String CHANNEL = "tvquiz_guard";
    private static final int FOCUS_RETRY_MAX = 6;

    private static volatile boolean running = false;
    private static volatile boolean quizShowing = false;
    private static volatile int remainSec = 0;

    /**
     * v1.3.0 未发布：供 ReviveReceiver / GuardWatchdog / 无障碍服务复用的安全拉起入口。
     * 家长主动关闭守护后（guardEnabled=false）不会被任何兜底擅自重新开启。
     */
    public static void ensureRunning(Context c, String via) {
        if (isRunning()) return;
        SettingsStore st = new SettingsStore(c);
        if (!st.guardEnabled()) return;
        // v1.3.1：兜底拉起同样要求已设置家长密码。
        if (!GuardPolicy.canStartGuard(st.hasPin())) return;
        boolean canDrawC = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
        if (OverlayChannel.pick(canDrawC, GuardAccessibilityService.canHost(),
                Build.VERSION.SDK_INT) == OverlayChannel.NONE) return;
        Intent i = new Intent(c, QuizOverlayService.class).setAction(ACTION_START);
        if (via != null) i.putExtra(EXTRA_VIA, via);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
            st.lastStartVia(via == null ? "unknown" : via);
        } catch (Exception e) {
            // SCREEN_ON / USER_PRESENT 不在 Android 12+ 后台启动 FGS 豁免白名单里，
            // 被拒是预期行为：静默等下一次 Job / Alarm 兜底，不弹崩溃提示。
            Log.w(TAG, "revive refused: " + e.getClass().getSimpleName());
        }
    }

    public static boolean isRunning() { return running; }
    public static boolean isQuizShowing() { return quizShowing; }
    public static int remainSec() { return remainSec; }

    /**
     * 统一关闭入口。verified=true 表示调用方已完成家长密码门禁；
     * 此时由本进程签发一次性令牌，服务侧消费后即作废，客户端无法伪造。
     */
    public static void requestStop(Context c, boolean verified) {
        Intent i = new Intent(c, QuizOverlayService.class);
        i.setAction(ACTION_STOP);
        if (verified) {
            i.putExtra(EXTRA_STOP_TOKEN, new SettingsStore(c).issueStopToken());
        }
        try {
            // API 26+ 用 startForegroundService，避免 App 处于后台时 startService 被拒；
            // 服务侧任何分支都会先 startForeground()，满足 5 秒契约。
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception e) {
            Toast.makeText(c, "关闭守护失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private WindowManager wm;
    private OverlayQuizView quizView;
    /** v1.3.3：当前答题窗口通道；remove / rebuild 必须沿用同一通道。 */
    private int quizChannel = OverlayChannel.NONE;
    private static volatile QuizOverlayService serviceInstance = null;
    private MediaPauseController media;
    private QuizEngine engine;
    private QuizEngine.Question current;
    private SettingsStore st;
    /** 练习模式挂起标志：练习页可见时不弹题，退出/进程重启自动恢复守护。 */
    private static volatile boolean practiceMode = false;
    public static void setPracticeMode(boolean v) { practiceMode = v; }
    public static boolean isPracticeMode() { return practiceMode; }

    private int interval = 30;
    private String lastKey = "";
    private long nextSyncAt = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ---- v1.1.0 可选加固：四个开关默认全关，默认路径与 v1.0.2 逐字等价 ----
    private UnlockPolicy unlock = new UnlockPolicy(UnlockPolicy.DEFAULT_STREAK);
    /** v1.3.0 未发布：按组答题策略对象，跨题保存在 Service 层。 */
    private int roundMode = SettingsStore.ROUND_GROUP;
    private RoundPolicy round = new RoundPolicy(RoundPolicy.DEFAULT_TARGET,
            RoundPolicy.DEFAULT_PASS_PERCENT);
    private final java.util.HashSet<String> usedInRound = new java.util.HashSet<String>();
    private String pendingSwapHint = null;
    private final CooldownPolicy cooldown = new CooldownPolicy();
    private boolean strictOneShot = false;
    private boolean cooldownEnabled = false;
    private boolean knowledgeOnly = false;
    private int difficultyLevel = DifficultyPolicy.LEVEL_ALL;
    private boolean autoContinue = true;
    private boolean pendingSwap = false;
    private final Runnable pendingSwapTask = new Runnable() {
        @Override public void run() {
            pendingSwap = false;
            if (quizShowing && quizView != null) {
                String h = pendingSwapHint;
                pendingSwapHint = null;
                swapQuestion(h);
            }
        }
    };

    // 窗口焦点重抢状态机
    private int focusRetry = 0;
    private boolean focusRecovering = false;
    private boolean overlayRebuilt = false;

    /**
     * v1.3.0 未发布：HOME 无法被第三方拦截（KEYCODE_HOME 由系统保留），
     * 但系统按下时会广播 ACTION_CLOSE_SYSTEM_DIALOGS。收到后立即重建遮罩窗口，
     * 让 WMS 重新评估层级并抢回遥控器焦点；倒计时在 Service 的 tick 里，不受影响。
     */
    private final BroadcastReceiver homeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!quizShowing || quizView == null) return;
            handler.post(new Runnable() {
                @Override public void run() {
                    if (quizShowing && quizView != null) rebuildOverlay();
                }
            });
        }
    };
    private boolean homeReceiverRegistered = false;

    private final OverlayQuizView.Listener listener = new OverlayQuizView.Listener() {
        @Override public void onAnswer(int idx) { answer(idx); }
        @Override public void onContinue() { finishQuiz(); }
        @Override public void onWindowFocus(boolean hasFocus) { onQuizWindowFocus(hasFocus); }
    };

    private final Runnable focusCheck = new Runnable() {
        @Override public void run() {
            if (!quizShowing || quizView == null) return;
            if (quizView.hasWindowFocus()) {
                focusRetry = 0;
                focusRecovering = false;
                quizView.focusFirst();
                return;
            }
            focusRecovering = true;
            if (focusRetry < FOCUS_RETRY_MAX) {
                focusRetry++;
                // 重新下发 LayoutParams 会强制 WMS 重新评估 focused window；
                // 仅 requestFocus() 只能改 View 树焦点，抢不回窗口焦点。
                try { updateQuizViewLayout(quizView.view()); }
                catch (Exception e) { Log.w(TAG, "focus updateViewLayout failed", e); }
                handler.postDelayed(this, 200);
                return;
            }
            if (!overlayRebuilt) {
                overlayRebuilt = true;
                rebuildOverlay();
                focusRetry = 0;
                handler.postDelayed(this, 300);
                return;
            }
            // 有限重试 + 一次重建都无法夺回窗口焦点：停止自旋，保留遮罩。
            // 不自动放行答题（否则拉下通知栏即可绕过守护），触摸/鼠标点击仍可作答。
            Log.w(TAG, "cannot regain overlay window focus; keeping quiz visible");
            focusRecovering = false;
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            // 无障碍服务被系统关闭时立即撤下降级遮罩，避免残留窗口卡住界面。
            if (quizShowing && quizChannel == OverlayChannel.ACCESSIBILITY
                    && !GuardAccessibilityService.canHost()) hideQuiz();
            if (!quizShowing) {
                remainSec--;
                if (remainSec <= 0) showQuiz();
            }
            long now = System.currentTimeMillis();
            if (now >= nextSyncAt && !st.bankUrl().isEmpty()) {
                nextSyncAt = now + st.syncIntervalMs();
                syncNow(false);
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        serviceInstance = this;
        st = new SettingsStore(this);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        media = new MediaPauseController(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // FGS 5 秒契约：只要是被 startForegroundService() 拉起（含 ACTION_STOP、开机自启），
        // 无论最终走哪个分支，都必须先 startForeground()。把 startForeground 放在
        // startGuard() 里会导致「权限不足 / 拒绝关闭 / 构造通知失败」分支裸奔。
        boolean fgReady = enterForeground();

        String action = intent == null ? ACTION_START : intent.getAction();
        if (action == null) action = ACTION_START;

        if (ACTION_STOP.equals(action)) {
            String token = intent == null ? null : intent.getStringExtra(EXTRA_STOP_TOKEN);
            boolean verified = st.consumeStopToken(token);
            if (!GuardPolicy.allowStop(st.hasPin(), verified)) {
                Toast.makeText(this, "已设置家长密码：请在 APP 内验证后才能关闭守护",
                        Toast.LENGTH_LONG).show();
                if (!running) { leaveForeground(); stopSelf(); }
                return running ? START_STICKY : START_NOT_STICKY;
            }
            stopAll();
            return START_NOT_STICKY;
        }

        if (!fgReady) {
            Log.e(TAG, "startForeground failed, guard cannot stay resident");
            Toast.makeText(this, "前台服务启动失败，答题守护无法常驻", Toast.LENGTH_LONG).show();
            st.guardEnabled(false);
            leaveForeground();
            stopSelf();
            return START_NOT_STICKY;
        }

        // v1.3.1：无密码一律不允许开启守护。覆盖开机广播 / Job / Alarm /
        // 无障碍 / 任务被划掉等所有旁路，杜绝「没设密码也能直接用」。
        if (!GuardPolicy.canStartGuard(st.hasPin())) {
            Toast.makeText(this, "首次使用请先在 APP 内设置家长密码，再开启守护",
                    Toast.LENGTH_LONG).show();
            st.guardEnabled(false);
            leaveForeground();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!running) startGuard();
        if (!running) { leaveForeground(); stopSelf(); return START_NOT_STICKY; }

        if (ACTION_QUIZ_NOW.equals(action)) {
            showQuiz();
        } else if (ACTION_RELOAD.equals(action)) {
            reload(false);
            if (!quizShowing) remainSec = Math.min(remainSec, interval);
        } else if (ACTION_SYNC.equals(action)) {
            syncNow(true);
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    /** 最近任务划掉时部分系统会连带停止服务，2 秒后由前台服务通道拉回。 */
    @Override public void onTaskRemoved(Intent rootIntent) {
        GuardWatchdog.scheduleRestart(this, 2000L);
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        serviceInstance = null;
        if (homeReceiverRegistered) {
            try { unregisterReceiver(homeReceiver); } catch (Exception ignored) { }
            homeReceiverRegistered = false;
        }
        handler.removeCallbacks(tick);
        handler.removeCallbacks(focusCheck);
        handler.removeCallbacks(pendingSwapTask);
        hideQuiz();
        if (media != null) media.release();
        running = false;
        // 系统/force-stop 路径不会走 stopAll()，这里兜底显式退出前台。
        leaveForeground();
        super.onDestroy();
    }

    /** 所有分支共用的 FGS 进入点；失败返回 false，由调用方决定立即停止。 */
    private boolean enterForeground() {
        try {
            startForeground(NOTIF_ID, buildNotification());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
            return false;
        }
    }

    private void leaveForeground() {
        try { stopForeground(true); } catch (Exception e) { Log.w(TAG, "stopForeground failed", e); }
    }

    private boolean canDraw() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return Settings.canDrawOverlays(this);
    }

    private void startGuard() {
        if (OverlayChannel.pick(canDraw(), GuardAccessibilityService.canHost(),
                Build.VERSION.SDK_INT) == OverlayChannel.NONE) {
            // v1.3.3：权限被撤时保留家长「已开启守护」的意图，权限恢复后自愈；
            // 不再清 guardEnabled，也不再静默失败。
            st.guardEnabled(true);
            st.lastStartVia("blocked_no_channel");
            GuardNotice.overlayRevoked(this);
            Toast.makeText(this, "暂无可用弹题通道：请重新授权悬浮窗或开启无障碍服务",
                    Toast.LENGTH_LONG).show();
            return;
        }
        running = true;
        st.guardEnabled(true);
        if (!homeReceiverRegistered) {
            try {
                registerReceiver(homeReceiver,
                        new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                homeReceiverRegistered = true;
            } catch (Exception e) { Log.w(TAG, "home receiver register failed", e); }
        }
        GuardWatchdog.schedule(this);
        GuardWatchdog.scheduleAlarm(this);
        reload(true);
        // 冷却截止时间戳在进程重启后仍生效：把剩余秒数接回倒计时，
        // 不让 force-stop / 重启直接洗掉惩罚（仅当家长开启冷却）。
        int pendingCooldown = restoreCooldown();
        remainSec = Math.max(interval, pendingCooldown);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000);
        Toast.makeText(this, "答题守护已开启，切到其他应用也会按时弹题", Toast.LENGTH_SHORT).show();
    }

    private void stopAll() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(focusCheck);
        hideQuiz();
        if (media != null) media.release();
        running = false;
        st.guardEnabled(false);
        leaveForeground();
        stopSelf();
        Toast.makeText(this, "答题守护已关闭", Toast.LENGTH_SHORT).show();
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    getString(R.string.guard_channel), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.guard_notification_text));
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_quiz)
                .setContentTitle(getString(R.string.guard_notification_title))
                .setContentText(getString(R.string.guard_notification_text))
                .setOngoing(true);
        return b.build();
    }

    /** 教材版本概念已移除：在线题包统一按「通用(课标)」处理，不再按版本筛选。 */
    private String[] editions() {
        String[] out = new String[Subjects.COUNT];
        for (int i = 0; i < out.length; i++) out[i] = "通用(课标)";
        return out;
    }

    private void reload(boolean first) {
        if (first) BankRepository.installBuiltin(this, st);
        interval = st.intervalSec();
        unlock = new UnlockPolicy(st.unlockStreak());
        roundMode = st.roundMode();
        round = new RoundPolicy(st.questionsPerRound(), st.passPercent());
        strictOneShot = st.strictOneShot();
        cooldownEnabled = st.cooldownEnabled();
        knowledgeOnly = st.questionSource() == SettingsStore.SOURCE_KNOWLEDGE_ONLY;
        difficultyLevel = st.difficultyLevel();
        autoContinue = st.autoContinueOnCorrect();
        cooldown.reset();
        engine = new QuizEngine(st.grade(), st.edition(),
                st.weight(Subjects.MATH), st.weight(Subjects.CHINESE),
                st.weight(Subjects.ENGLISH), st.weight(Subjects.OTHER));
        engine.setAllSubjects(st.allSubjects());
        engine.setKnowledgeOnly(knowledgeOnly);
        engine.setDifficultyLevel(difficultyLevel);
        engine.setStudyScope(SyllabusAssets.get(this), st.termIndex(), st.unitLimit());
        QuizDb cached = QuizDb.peek();
        if (cached != null) {
            engine.setDb(cached);
        } else {
            // 首启复制 ~4.5MB 题库、创建错题库，都放到后台线程。
            final Context app = getApplicationContext();
            new Thread(new Runnable() {
                @Override public void run() {
                    final QuizDb opened = QuizDb.open(app);
                    WrongBook.open(app);
                    handler.post(new Runnable() {
                        @Override public void run() {
                            if (running && engine != null) engine.setDb(opened);
                        }
                    });
                }
            }, "pohw-db-open").start();
        }
        List<QuizEngine.Question> qs = BankRepository.load(this, st.grade(), editions());
        engine.setBank(qs);
        nextSyncAt = System.currentTimeMillis() + (first ? 5000L : st.syncIntervalMs());
    }

    private void syncNow(final boolean force) {
        BankRepository.sync(getApplicationContext(), st, st.bankUrl(), force,
                new BankRepository.Callback() {
                    @Override public void onDone(BankRepository.SyncResult r) {
                        if (running) reload(false);
                    }
                });
    }

    /** 每次弹题都换一道与上一题不同的题；单题题库会退回同一题，由 answer() 处理。 */
    private QuizEngine.Question drawFresh() {
        for (int i = 0; i < 12; i++) {
            if (engine == null) break;
            QuizEngine.Question q = engine.next();
            if (q == null) break;
            String k = q.key();
            if (k.equals(lastKey)) continue;
            // 选项过长在遥控器四选一里必然被固定高度裁切，直接跳过换一道。
            if (!QuizTextSizer.optionsFitTv(q.options)) continue;
            if (!QuizTextSizer.questionFitTv(q.text)) continue;
            lastKey = k;
            return q;
        }
        return null;
    }

    private String metaFor(QuizEngine.Question q) {
        String gate;
        if (roundMode == SettingsStore.ROUND_STREAK) {
            int n = unlock.required();
            gate = n > 1 ? ("连续答对 " + n + " 题解锁 " + interval + " 秒")
                         : ("答对解锁 " + interval + " 秒");
        } else if (roundMode == SettingsStore.ROUND_SINGLE) {
            gate = "答对解锁 " + interval + " 秒";
        } else {
            gate = "一组 " + round.target() + " 题 · 需答对 " + round.minCorrect()
                    + " 题 · 当前第 " + Math.min(round.answered() + 1, round.target()) + " 题";
        }
        return st.gradeLabel() + " · " + Subjects.name(q.subject) + " · " + gate;
    }

    private void showQuiz() {
        if (practiceMode) { remainSec = interval; return; }
        if (quizShowing) return;
        if (OverlayChannel.pick(canDraw(), GuardAccessibilityService.canHost(),
                Build.VERSION.SDK_INT) == OverlayChannel.NONE) {
            // 不清 guardEnabled、不 stopAll：权限恢复后由 ReviveReceiver / APP 自愈。
            st.lastStartVia("blocked_no_channel");
            GuardNotice.overlayRevoked(this);
            remainSec = interval;
            return;
        }
        if (engine == null) reload(false);
        current = drawFresh();
        if (current == null) {
            remainSec = interval;
            Toast.makeText(this, "题库暂不可用，稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        st.bumpQuestioned();

        // 不复用 View：反复 add/remove 会让窗口焦点状态不可预测。
        final OverlayQuizView view = new OverlayQuizView(this, listener);
        view.setStrict(strictOneShot);
        unlock.reset();
        round.reset();
        usedInRound.clear();
        usedInRound.add(current.key());
        pendingSwapHint = null;
        cancelPendingSwap();
        view.bind(current, metaFor(current));
        quizView = view;
        focusRetry = 0;
        focusRecovering = false;
        overlayRebuilt = false;
        handler.removeCallbacks(focusCheck);
        try {
            if (!addQuizView(view.view())) throw new IllegalStateException("no overlay channel");
            quizShowing = true;
            view.view().post(new Runnable() {
                @Override public void run() {
                    if (quizShowing && quizView == view) view.focusFirst();
                }
            });
        } catch (Exception e) {
            // addView 成功后若出异常，必须无条件移除，否则遮罩会永久盖住系统界面
            quizShowing = false;
            removeOverlayNow();
            remainSec = interval;
            Toast.makeText(this, "无法显示答题遮罩：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (media != null) media.quizShown(st);
    }

    private WindowManager.LayoutParams buildParams(int channel) {
        int type;
        if (channel == OverlayChannel.ACCESSIBILITY) {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);
        // 显式声明窗口可聚焦（new LayoutParams 默认即不带 NOT_FOCUSABLE）。
        // 注意：这行不「保证」WMS 一定把 focused window 给遮罩——通知栏/系统对话框仍可抢走，
        // 真正的兜底是 onQuizWindowFocus() + focusCheck 的有限次重抢与窗口重建。
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.dimAmount = 0.88f;
        return lp;
    }

    private void onQuizWindowFocus(boolean hasFocus) {
        if (!quizShowing || quizView == null) return;
        if (hasFocus) {
            handler.removeCallbacks(focusCheck);
            focusRetry = 0;
            focusRecovering = false;
            quizView.focusFirst();
            return;
        }
        // focusCheck 执行期间（updateViewLayout / remove+add）自身会触发 false 回调，
        // 用 focusRecovering 过滤嵌套回调，避免无限自旋。
        if (focusRecovering) return;
        handler.removeCallbacks(focusCheck);
        handler.postDelayed(focusCheck, 200);
    }

    /** remove + add 同一个 View 实例，保留 QuizFlow 状态，同时让 WMS 重建 ViewRootImpl。 */
    private void rebuildOverlay() {
        if (wm == null || quizView == null) return;
        final View v = quizView.view();
        try { removeQuizViewImmediate(v); } catch (Exception e) { Log.w(TAG, "rebuild remove failed", e); }
        try {
            if (!addQuizView(v)) throw new IllegalStateException("no overlay channel");
            v.post(new Runnable() {
                @Override public void run() {
                    if (quizShowing && quizView != null) quizView.focusFirst();
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "rebuild add failed", e);
        }
    }

    /** 判题与状态迁移统一交给 QuizFlow；bind() 在换题时 reset()，计数自动归零。 */
    private void answer(int idx) {
        if (!quizShowing || current == null || quizView == null) return;
        QuizFlow flow = quizView.flow();
        int outcome = flow.submit(idx, current.answerIndex);
        if (outcome == QuizFlow.IGNORE) return;
        boolean needSwap = (outcome == QuizFlow.SWAP);

        if (outcome == QuizFlow.CORRECT) {
            st.bumpCorrect();
            cancelPendingSwap();
            cooldown.reset();
            st.clearCooldown();
            if (roundMode == SettingsStore.ROUND_STREAK) {
                if (!unlock.onCorrect()) { swapQuestion(unlock.progress()); return; }
            } else if (roundMode == SettingsStore.ROUND_GROUP) {
                int r = round.onAnswer(true);
                if (r == RoundPolicy.CONTINUE) { swapQuestion(round.progress()); return; }
                if (r == RoundPolicy.FAIL) {
                    String msg = round.result(false);
                    round.reset();
                    usedInRound.clear();
                    swapQuestion(msg);
                    return;
                }
            }
            grantUnlock("✓ 答对啦！");
            return;
        }

        recordWrong(idx);
        if (roundMode == SettingsStore.ROUND_STREAK) unlock.onWrong();
        int wait = applyCooldown();
        String hint = AnswerPolicy.wrongHint(flow.wrongAttempts(), flow.wrongLimit());
        if (needSwap && roundMode == SettingsStore.ROUND_GROUP) {
            int r = round.onAnswer(false);
            if (r == RoundPolicy.CONTINUE) {
                hint = hint + " · " + round.progress();
            } else if (r == RoundPolicy.FAIL) {
                hint = round.result(false);
                round.reset();
                usedInRound.clear();
            } else {
                // 本组最后一道答错，但正确率已达标：仍按通过处理。
                grantUnlock("✓ 本组通过！");
                return;
            }
        }
        if (needSwap && wait > 0) {
            // 冷却只延迟「换题」，不拦截「答对」：遮罩仍在，答对通道始终开放。
            pendingSwap = true;
            pendingSwapHint = hint;
            quizView.cooldown("✗ 答错了，" + wait + " 秒后换新题（答对可直接继续）");
            handler.removeCallbacks(pendingSwapTask);
            handler.postDelayed(pendingSwapTask, wait * 1000L);
            return;
        }
        if (needSwap) { swapQuestion(hint); return; }
        quizView.wrong(idx, hint);
    }

    /** 组任务达标的统一解锁出口；答对自动继续时必须 post 出按键分发栈。 */
    private void grantUnlock(String prefix) {
        if (autoContinue) {
            quizView.correctAuto(prefix + "自动继续观看 " + interval + " 秒", current.explain);
            handler.post(new Runnable() {
                @Override public void run() { if (quizShowing) finishQuiz(); }
            });
        } else {
            quizView.correct(prefix + "继续观看 " + interval + " 秒", current.explain);
        }
    }

    /** 原地换题：窗口不 remove/add，避免再次触发窗口焦点状态机。 */
    private void swapQuestion(String hintMsg) {
        cancelPendingSwap();
        QuizEngine.Question next = drawFresh();
        for (int i = 0; i < 8 && next != null && usedInRound.contains(next.key()); i++) {
            next = drawFresh();
        }
        if (next != null) usedInRound.add(next.key());
        if (next != null && !next.key().equals(current.key())) {
            current = next;
            st.bumpQuestioned();
            quizView.bind(current, metaFor(current));
            if (hintMsg != null) quizView.hint(hintMsg);
            return;
        }
        // 题库只有一题时无法换题：重置状态机，避免永远卡在 SWAP / 全部选项禁用。
        quizView.bind(current, metaFor(current));
        quizView.hint(hintMsg != null ? hintMsg : "题库只有这一题，继续作答吧");
    }

    /** 答错按档位递增冷却并持久化；返回需要等待的秒数（0 = 不延迟换题）。 */
    private int applyCooldown() {
        if (!cooldownEnabled) return 0;
        long nowWall = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();
        long deadline = cooldown.onWrong(nowWall);
        st.saveCooldown(deadline, nowWall, nowElapsed, cooldown.wrongCount());
        return cooldown.remainingSec(nowWall);
    }

    /** 进程重启后恢复冷却：墙钟回拨或 elapsed 归零时按当前档位重计，既不锁死也不放行。 */
    private int restoreCooldown() {
        if (!cooldownEnabled) return 0;
        long deadline = st.cooldownDeadline();
        if (deadline <= 0L) return 0;
        long nowWall = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();
        int sec = CooldownPolicy.safeRemaining(deadline, nowWall,
                st.cooldownSavedWall(), st.cooldownSavedElapsed(), nowElapsed,
                st.cooldownWrongCount());
        cooldown.reset();
        st.clearCooldown();
        return sec;
    }

    private void cancelPendingSwap() {
        pendingSwap = false;
        handler.removeCallbacks(pendingSwapTask);
    }

    private void recordWrong(int picked) {
        if (current == null) return;
        WrongBook.Entry e = WrongBook.of(current, st.grade(), st.gradeLabel(),
                "", picked);
        WrongBook.recordAsync(getApplicationContext(), e);
    }

    private void finishQuiz() {
        if (!quizShowing) return;
        cancelPendingSwap();
        cooldown.reset();
        st.clearCooldown();
        unlock.reset();
        round.reset();
        usedInRound.clear();
        pendingSwapHint = null;
        hideQuiz();
        if (media != null) media.quizFinished(st);
        remainSec = interval;
    }

    private void hideQuiz() {
        cancelPendingSwap();
        handler.removeCallbacks(focusCheck);
        focusRecovering = false;
        quizShowing = false;
        // 不依赖 quizShowing 标志：标志异常时也兜底移除，避免残留窗口卡住卸载流程。
        removeOverlayNow();
    }

    /**
     * 无条件尝试移除答题窗口。removeViewImmediate 在「未 attach」时会抛异常，
     * 此时本就没有残留窗口；但为防「已 attach 却移除失败」，额外 post 一次异步
     * removeView，并始终保留 View 引用到该 Runnable 中，不会因置空而丢失。
     */
    private void removeOverlayNow() {
        if (quizView == null) return;
        final View v = quizView.view();
        quizView = null;
        try {
            removeQuizViewImmediate(v);
        } catch (Exception e) {
            Log.w(TAG, "removeViewImmediate failed, retry async", e);
            handler.post(new Runnable() {
                @Override public void run() {
                    try { removeQuizViewImmediate(v); } catch (Exception ignored) { }
                }
            });
        }
    }

    /** v1.3.3：同一份 OverlayQuizView 按通道挂到系统浮层或无障碍窗口。 */
    private boolean addQuizView(View v) {
        int ch = OverlayChannel.pick(canDraw(), GuardAccessibilityService.canHost(),
                Build.VERSION.SDK_INT);
        if (!OverlayChannel.available(ch)) return false;
        quizChannel = ch;
        WindowManager.LayoutParams lp = buildParams(ch);
        if (ch == OverlayChannel.ACCESSIBILITY) return GuardAccessibilityService.addOverlay(v, lp);
        if (wm == null) return false;
        wm.addView(v, lp);
        return true;
    }

    private void updateQuizViewLayout(View v) {
        WindowManager.LayoutParams lp = buildParams(quizChannel);
        if (quizChannel == OverlayChannel.ACCESSIBILITY) {
            GuardAccessibilityService.updateOverlay(v, lp);
        } else if (wm != null) {
            wm.updateViewLayout(v, lp);
        }
    }

    private void removeQuizViewImmediate(View v) {
        if (quizChannel == OverlayChannel.ACCESSIBILITY) {
            GuardAccessibilityService.removeOverlay(v);
        } else if (wm != null) {
            wm.removeViewImmediate(v);
        }
    }

    /** 无障碍服务被系统关闭时撤下正在显示的降级遮罩。 */
    public static void onOverlayChannelLost() {
        QuizOverlayService s = serviceInstance;
        if (s != null && s.quizShowing) s.hideQuiz();
    }
}
