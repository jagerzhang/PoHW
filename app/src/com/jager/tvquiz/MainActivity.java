package com.jager.tvquiz;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 控制台：悬浮窗授权、守护开关、立即出题、错题库、开机自启设置与题库状态。 */
public class MainActivity extends Activity {

    private static final String NL = "\n";
    private SettingsStore st;
    private TextView status;
    private FrameLayout rootView;
    private Button btnStart, btnStop, btnWrong, btnBoot;
    private PinVerifyView verifyView;
    private Boolean paintedRunning = null;
    private String appVersion = "?";
    private String appVersionCode = "?";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new SettingsStore(this);
        preloadBank();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
        readVersion();
        setContentView(buildView());
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshWrongBook();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    /** 就地验证层显示时，数字键/退格/返回键由本 Activity 统一转发。 */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (verifyView != null && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK) { verifyView.cancel(); return true; }
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                verifyView.key(String.valueOf(code - KeyEvent.KEYCODE_0));
                return true;
            }
            if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
                verifyView.key(String.valueOf(code - KeyEvent.KEYCODE_NUMPAD_0));
                return true;
            }
            if (code == KeyEvent.KEYCODE_DEL) { verifyView.key("退格"); return true; }
        }
        return super.dispatchKeyEvent(e);
    }

    /** 版本一律从 PackageManager 读真实安装包信息，禁止再硬编码。 */
    private void readVersion() {
        try {
            android.content.pm.PackageInfo pi =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersion = pi.versionName == null ? "?" : pi.versionName;
            appVersionCode = String.valueOf(pi.versionCode);
        } catch (Exception e) {
            appVersion = "?";
            appVersionCode = "?";
        }
    }

    /** 首次启动复制 ~4.5MB 题库并初始化错题库，都放到后台线程。 */
    private void preloadBank() {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                QuizDb.open(app);
                WrongBook.open(app);
                BankRepository.installBuiltin(app, st);
                handler.post(new Runnable() {
                    @Override public void run() { render(); }
                });
            }
        }, "pohw-db-preload").start();
    }

    /** 从错题库页面返回后刷新缓存计数。 */
    private void refreshWrongBook() {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                WrongBook w = WrongBook.open(app);
                if (w != null) w.count();
                handler.post(new Runnable() {
                    @Override public void run() { render(); }
                });
            }
        }, "pohw-wrong-refresh").start();
    }

    @Override public void onRequestPermissionsResult(int req, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(req, permissions, grantResults);
        if (req == 100 && grantResults.length > 0
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "未授予通知权限：守护仍会运行，但状态栏通知不可见",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean canDraw() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return Settings.canDrawOverlays(this);
    }

    private void send(String action) {
        Intent i = new Intent(this, QuizOverlayService.class);
        i.setAction(action);
        try {
            if (!QuizOverlayService.isRunning() && Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception e) {
            Toast.makeText(this, "服务启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) return;
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "该设备没有悬浮窗授权入口", Toast.LENGTH_LONG).show(); }
    }

    private Button actionButton(LinearLayout row, String label, int bg, View.OnClickListener l) {
        Button b = Ui.button(this, label, bg, 0xFFFFFFFF, 17);
        b.setOnClickListener(l);
        Ui.focus(b, this, bg, Ui.BLUE, 0xFFFFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(this, 190), Ui.dp(this, 54));
        p.setMargins(Ui.dp(this, 7), 0, Ui.dp(this, 7), 0);
        row.addView(b, p);
        return b;
    }

    private View buildView() {
        rootView = new FrameLayout(this);
        LinearLayout l = Ui.vbox(this);
        l.setBackgroundColor(Ui.BG);
        l.setPadding(Ui.dp(this, 40), Ui.dp(this, 16), Ui.dp(this, 40), Ui.dp(this, 12));

        l.addView(Ui.tv(this, "答题守护  ·  v" + appVersion + " 控制台",
                28, 0xFFFFFFFF), Ui.fixed(this, 42));

        status = Ui.tv(this, "", 15, 0xFFC7D2E2, Gravity.LEFT);
        status.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        status.setBackground(Ui.round(0xFF1B2432, 12, this));
        status.setLineSpacing(0, 1.12f);
        l.addView(status, Ui.fill(1));

        LinearLayout row1 = Ui.hbox(this);
        btnStart = actionButton(row1, "开启守护", GuardState.startColor(false),
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (!canDraw()) { openOverlaySettings(); return; }
                        send(QuizOverlayService.ACTION_START);
                        handler.postDelayed(new Runnable() {
                            @Override public void run() { render(); }
                        }, 300);
                    }
                });
        btnStop = actionButton(row1, "关闭守护", GuardState.stopColor(true),
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (!QuizOverlayService.isRunning()) {
                            Toast.makeText(MainActivity.this, "守护已经是关闭状态", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // 已设密码：在当前界面叠加验证层，不跳转、不退出
                        if (GuardPolicy.requiresPinToStop(st.hasPin())) showVerify();
                        else doStop();
                    }
                });
        actionButton(row1, "立即出题", 0xFFEF6C4D, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!canDraw()) { openOverlaySettings(); return; }
                send(QuizOverlayService.ACTION_QUIZ_NOW);
            }
        });
        l.addView(row1, Ui.fixed(this, 62));

        LinearLayout row2 = Ui.hbox(this);
        actionButton(row2, "家长设置", 0xFF185FA5, new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
        actionButton(row2, "授权悬浮窗", 0xFF243044, new View.OnClickListener() {
            @Override public void onClick(View v) { openOverlaySettings(); }
        });
        actionButton(row2, "检查题库更新", 0xFF6C4F9E, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (st.bankUrl().isEmpty()) {
                    Toast.makeText(MainActivity.this,
                            "当前使用内置离线题库；如需在线源请在「家长设置」自行配置地址",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                BankRepository.sync(getApplicationContext(), st, st.bankUrl(), true, new BankRepository.Callback() {
                    @Override public void onDone(BankRepository.SyncResult r) {
                        Toast.makeText(MainActivity.this, r.message, Toast.LENGTH_LONG).show();
                        if (QuizOverlayService.isRunning()) send(QuizOverlayService.ACTION_RELOAD);
                        render();
                    }
                });
            }
        });
        l.addView(row2, Ui.fixed(this, 62));

        LinearLayout row3 = Ui.hbox(this);
        btnWrong = actionButton(row3, "错题库 · 0 题", 0xFFB06A2C, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, WrongBookActivity.class));
            }
        });
        btnBoot = actionButton(row3, "厂商自启设置", 0xFF2C6E8F, new View.OnClickListener() {
            @Override public void onClick(View v) {
                BootSettings.open(MainActivity.this);
                Toast.makeText(MainActivity.this,
                        "请在厂商设置里允许本应用「自启动」，否则收不到开机广播",
                        Toast.LENGTH_LONG).show();
            }
        });
        l.addView(row3, Ui.fixed(this, 58));

        l.addView(Ui.tv(this,
                "开启守护后按 Home 切到任意应用，到点弹出全屏答题页；Home 键属系统级按键，第三方无法拦截。\n"
                        + "卸载前请先关闭守护；遮罩卡住可在电脑执行 adb shell am force-stop com.jager.tvquiz",
                13, Ui.MUTED), Ui.fixed(this, 44));

        rootView.addView(l, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return rootView;
    }

    // ---------- 就地密码验证（不启动新 Activity、不 finish 本界面） ----------

    private void showVerify() {
        hideVerify();
        verifyView = new PinVerifyView(this, st, new PinVerifyView.Callback() {
            @Override public void onVerified() { doStop(); }
            @Override public void onCancel() {
                hideVerify();
                Toast.makeText(MainActivity.this, "已取消关闭守护", Toast.LENGTH_SHORT).show();
            }
        });
        rootView.addView(verifyView.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        verifyView.requestFocusFirst();
    }

    private void hideVerify() {
        if (verifyView != null) {
            try { rootView.removeView(verifyView.view()); } catch (Exception ignored) { }
            verifyView = null;
        }
    }

    /** 关闭动作：只移除验证层并请求服务停止，界面保持，按钮颜色随后刷新。 */
    private void doStop() {
        hideVerify();
        QuizOverlayService.requestStop(this, true);
        render();
        handler.postDelayed(new Runnable() {
            @Override public void run() { render(); }
        }, 350);
    }

    private void paintToggleButtons(boolean running) {
        paintButton(btnStart, GuardState.startColor(running), GuardState.startLabel(running));
        paintButton(btnStop, GuardState.stopColor(running), GuardState.stopLabel(running));
    }

    private void paintButton(Button b, int bg, String label) {
        b.setText(label);
        Ui.focus(b, this, bg, Ui.BLUE, 0xFFFFFFFF);
        b.setBackground(Ui.round(bg, 10, this));
        b.setTextColor(0xFFFFFFFF);
    }

    private static String ts(long ms) {
        if (ms <= 0) return "-";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }

    private void render() {
        StringBuilder s = new StringBuilder();
        s.append("应用版本：").append(appVersion).append("（build ").append(appVersionCode)
                .append("）  ·  悬浮窗权限：")
                .append(canDraw() ? "已授权 ✓" : "未授权 ✗（点「授权悬浮窗」）").append(NL);
        boolean running = QuizOverlayService.isRunning();
        s.append("守护服务：").append(running ? "运行中" : "未运行");
        if (running) {
            s.append("  ·  ").append(QuizOverlayService.isQuizShowing()
                    ? "答题遮罩已弹出" : "距下次答题 " + QuizOverlayService.remainSec() + " 秒");
        }
        s.append("  ·  家长密码：").append(st.hasPin() ? "已设置 ✓" : "未设置 ✗").append(NL);
        s.append("当前配置：").append(st.gradeLabel()).append(" · 每 ").append(st.intervalSec())
                .append(" 秒一题 · ")
                .append(st.allSubjects() ? "全学科"
                        : ("数/语/英 " + st.weight(0) + "/" + st.weight(1) + "/" + st.weight(2)))
                .append(NL);
        s.append("教材版本：");
        for (int i = 0; i < Subjects.COUNT; i++) {
            if (i > 0) s.append(" / ");
            s.append(Subjects.name(i)).append(" ").append(st.editionFor(i));
        }
        s.append(NL);
        QuizDb qd = QuizDb.peek();
        s.append("内置题库：").append(qd == null ? "正在加载…（首次启动需复制题库）"
                : (qd.count() + " 题 / " + qd.knowledgeCount() + " 知识点 · "
                   + qd.subjectCount() + " 学科 · 本年级 " + qd.countForGrade(st.grade())
                   + " 题 · " + qd.version())).append(NL);
        s.append("错题库：").append(WrongBook.cachedCount()).append(" 题");
        String latest = WrongBook.latestSummary();
        if (!latest.isEmpty()) s.append(" · 最近：").append(latest);
        s.append("  ·  统计：答对 ")
                .append(st.correct()).append(" / ").append(st.questioned()).append(" 题").append(NL);
        s.append("开机自启：").append(st.bootAutostart() ? "开" : "关")
                .append("（自动开启守护：").append(st.autoGuardOnBoot() ? "开" : "关")
                .append("）  ·  答题暂停视频：").append(st.pauseOnQuiz() ? "开" : "关")
                .append(" / 自动恢复：").append(st.resumePlayback() ? "开" : "关").append(NL);
        s.append("题库源：").append(st.bankSource()).append(" · 版本 ").append(st.bankVersion())
                .append(" · 上次检查 ").append(ts(st.bankChecked())).append(NL);
        s.append("同步状态：").append(st.bankMessage());
        status.setText(s.toString());

        if (btnWrong != null) btnWrong.setText("错题库 · " + WrongBook.cachedCount() + " 题");
        if (btnBoot != null) {
            btnBoot.setText(st.bootAutostart() ? "厂商自启设置" : "自启已关闭");
        }

        if (paintedRunning == null || paintedRunning.booleanValue() != running) {
            paintedRunning = Boolean.valueOf(running);
            paintToggleButtons(running);
        }
    }
}
