package com.jager.tvquiz;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 家长设置：密码门禁 + 单列卡片流（瀑布式纵向滚动）。
 * 参照电视端系统设置的信息层级：一个居中窄栏、分组卡片、每行「左标签 / 右取值」，
 * 底部固定操作条常驻，避免长表单把「保存并返回」挤到屏幕外。
 * 可聚焦控件按「行」显式串成上下链，学科复合行内部再串左右链；
 * TV 遥控器不再需要跨栏跳转，也不会出现三栏并排时的文字挤压与底部裁切。
 */
public class SettingsActivity extends Activity {

    private static final String NL = "\n";
    private static final String[] PRESETS = {"", "http://10.0.2.2:8000/", "http://127.0.0.1:8000/"};
    private static final int CARD_BG = 0xFF161E2B;
    /** 居中窄栏的左右留白：卡片与底部操作条共用，保证视觉对齐。 */
    private static final int GUTTER_DP = 88;
    /** 行标签宽度：普通行与学科行分别取不同值，避免右侧取值被压窄。 */
    private static final int LABEL_DP = 150;
    private static final int SUBJ_LABEL_DP = 100;
    /** 卡片内行、按钮、说明共用的上下留白（相邻两行间距 = 上下各一半相加）。 */
    private static final int ROW_GAP_DP = 12;

    private SettingsStore st;
    private PinGate gate;
    /** 按教材版本族隔离的单元进度表。 */
    private Syllabus syllabus;
    private int presetIdx = 0;
    private boolean inSettings = false;
    /** 用户手输过在线源地址后，refreshRows() 不得再用持久化值覆盖掉未保存的输入。 */
    private boolean urlDirty = false;
    /** 程序自身 setText 期间置位，避免 TextWatcher 把回显误判成用户编辑。 */
    private boolean urlProgrammatic = false;

    private View gateView, settingsView;
    private TextView gateTitle, gateMask, gateMsg, gateState, bankStatus, pinState;
    private Button firstGateBtn;
    private EditText urlEdit;
    private Button rowGrade, rowInterval, rowScope, rowSync, rowTerm, rowUnit, rowEdition, rowQr;
    /** v1.1.0 四项可选加固，默认全关。 */
    private Button rowStreak, rowStrict, rowCooldown, rowSource, rowDifficulty, rowAutoContinue;
    /** 在线源并排行：左检查题库更新 / 右自动更新，供行内左右焦点链使用。 */
    private Button[] bankPair;
    private Button[] rowWt = new Button[Subjects.COUNT];
    private Button rowBoot, rowAutoGuard, rowPause, rowResume;
    /** v1.2.5 在线版本更新：自动检查开关 / 状态 / 手动下载安装。 */
    private Button rowUpdate, btnUpdateInstall;
    /** v1.3.0 未发布：按组答题的两项设置。 */
    private Button rowRoundSize, rowPassRate;
    /** issue #37：生效时段与节假日（默认全关，覆盖安装行为不变）。 */
    private Button rowSchedule, rowSchoolWin, rowHomeWin, rowSkipHolidays,
            rowVacation, rowTodayOverride;
    private static final String[] SCHOOL_PRESETS = {
            "17:00-21:00:1111111", "18:00-21:00:1111111",
            "19:00-22:00:1111111", "16:00-20:00:1111111",
            "00:00-23:59:1111111"
    };
    private static final String[] SCHOOL_PRESET_LABELS = {
            "17:00–21:00", "18:00–21:00", "19:00–22:00", "16:00–20:00", "全天（不限）"
    };
    private static final String[] HOME_PRESETS = {
            "09:00-21:00:1111111", "08:00-22:00:1111111",
            "10:00-20:00:1111111", "00:00-23:59:1111111"
    };
    private static final String[] HOME_PRESET_LABELS = {
            "09:00–21:00", "08:00–22:00", "10:00–20:00", "全天（不限）"
    };
    private TextView updateStatus;
    /** 防重入标志：绝不用 setEnabled(false) 禁用已登记焦点链的按钮。 */
    private boolean updateChecking = false, updateDownloading = false;
    /** 单列焦点链：每行一个数组，纵向按加入顺序连接。 */
    private final java.util.List<View[]> focusRows = new java.util.ArrayList<View[]>();
    private Button btnChangePin, btnSave;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new SettingsStore(this);
        syllabus = SyllabusAssets.get(this);
        BankRepository.installBuiltin(this, st);
        gate = new PinGate(st);
        gateView = buildGate();
        settingsView = buildSettings();
        enterGate(false);
    }

    @Override public void onBackPressed() {
        if (inSettings) save();
        else finish();
    }

    /** 遥控器/键盘实体数字键直接输入，避免 TV 上焦点吞掉点击导致密码输不进去。 */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (!inSettings && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            int code = e.getKeyCode();
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                gate.key(String.valueOf(code - KeyEvent.KEYCODE_0));
                refreshGate();
                return true;
            }
            if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
                gate.key(String.valueOf(code - KeyEvent.KEYCODE_NUMPAD_0));
                refreshGate();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DEL) {
                gate.key("退格");
                refreshGate();
                return true;
            }
            // DPAD_CENTER / ENTER 交给按钮自身的 click，不在这里消费，避免双触发
        }
        return super.dispatchKeyEvent(e);
    }

    private static String ts(long ms) {
        if (ms <= 0) return "-";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }

    // ---------- 密码门禁 ----------

    private View buildGate() {
        LinearLayout l = Ui.vbox(this);
        l.setBackgroundColor(Ui.BG);
        l.setPadding(Ui.dp(this, 40), Ui.dp(this, 12), Ui.dp(this, 40), Ui.dp(this, 12));
        l.setGravity(Gravity.CENTER_HORIZONTAL);

        gateTitle = Ui.tv(this, "", 24, 0xFFFFFFFF);
        l.addView(gateTitle, Ui.fixed(this, 38));
        gateMask = Ui.tv(this, "", 30, Ui.AMBER);
        l.addView(gateMask, Ui.fixed(this, 44));
        gateMsg = Ui.tv(this, "", 15, Ui.AMBER);
        l.addView(gateMsg, Ui.fixed(this, 30));
        gateState = Ui.tv(this, "", 13, Ui.MUTED);
        l.addView(gateState, Ui.fixed(this, 28));

        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "清空", "0", "确定"};
        for (int row = 0; row < 4; row++) {
            LinearLayout r = Ui.hbox(this);
            for (int col = 0; col < 3; col++) {
                final String k = keys[row * 3 + col];
                Button btn = Ui.button(this, k, 0xFF243044, 0xFFF5F7FA, 20);
                btn.setFocusableInTouchMode(false);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { onKey(k); }
                });
                Ui.focus(btn, this, 0xFF243044, 0xFFF5F7FA);
                if (firstGateBtn == null) firstGateBtn = btn;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        Ui.dp(this, 120), Ui.dp(this, 46));
                p.setMargins(Ui.dp(this, 6), Ui.dp(this, 3), Ui.dp(this, 6), Ui.dp(this, 3));
                r.addView(btn, p);
            }
            l.addView(r, Ui.fixed(this, 54));
        }
        l.addView(Ui.tv(this,
                "遥控器可直接按数字键输入；也可以点屏幕键盘，输完点「确定」。",
                13, Ui.MUTED), Ui.fixed(this, 26));
        l.addView(Ui.tv(this,
                "忘记密码请在系统「设置 → 应用管理 → PoHW」清除数据（会清空全部设置与错题库）",
                13, Ui.MUTED), Ui.fixed(this, 26));
        return l;
    }

    private void onKey(String k) {
        gate.key(k);
        refreshGate();
    }

    private void refreshGate() {
        gateTitle.setText(gate.title());
        gateMask.setText(gate.mask());
        gateMsg.setText(gate.messageText());
        gateState.setText(st.hasPin()
                ? "当前状态：已设置家长密码（修改密码需先验证旧密码）"
                : "当前状态：尚未设置密码，本次为首次设置");
        String t = gate.consumeToast();
        if (!t.isEmpty()) Toast.makeText(this, t, Toast.LENGTH_SHORT).show();
        if (gate.consumeEnterSettings()) enterSettings();
    }

    private void enterGate(boolean reset) {
        if (reset) gate.backToGate();
        inSettings = false;
        setContentView(gateView);
        if (firstGateBtn != null) firstGateBtn.requestFocus();
        refreshGate();
    }

    private void enterSettings() {
        inSettings = true;
        setContentView(settingsView);
        refreshRows();
        if (rowGrade != null) rowGrade.requestFocus();
    }

    // ---------- 单列卡片流布局 ----------

    /** 分组卡片：顶部「色条 + 序号标题」，内部逐行堆叠，卡片之间留呼吸间距。 */
    private LinearLayout addCard(LinearLayout parent, String title) {
        LinearLayout card = Ui.vbox(this);
        card.setBackground(Ui.round(CARD_BG, 14, this));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 14));
        parent.addView(card, cp);

        LinearLayout head = Ui.hbox(this);
        head.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        View bar = new View(this);
        bar.setBackground(Ui.round(Ui.AMBER, 2, this));
        head.addView(bar, new LinearLayout.LayoutParams(Ui.dp(this, 4), Ui.dp(this, 18)));
        TextView tv = Ui.tv(this, title, 16, 0xFFF5F7FA, Gravity.LEFT);
        tv.setPadding(Ui.dp(this, 10), 0, 0, 0);
        head.addView(tv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // 标题行随系统字体缩放自适应，不写死 28dp 高度。
        head.setMinimumHeight(Ui.dp(this, 28));
        card.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void note(LinearLayout parent, String text) {
        TextView t = Ui.tv(this, text, 12, Ui.MUTED, Gravity.LEFT);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 2));
        parent.addView(t, rowLp());
    }

    /** 把本行可聚焦控件登记进纵向焦点链；说明文字与禁用占位不登记。 */
    private void track(View... views) {
        java.util.List<View> usable = new java.util.ArrayList<View>();
        for (View v : views) {
            if (v != null && v.isEnabled() && v.isFocusable()) usable.add(v);
        }
        if (!usable.isEmpty()) focusRows.add(usable.toArray(new View[0]));
    }

    /** 卡片内所有行、按钮、说明共用的上下留白，避免遥控器视角下按钮连成一片。 */
    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int half = Ui.dp(this, ROW_GAP_DP / 2);
        p.setMargins(0, half, 0, half);
        return p;
    }

    /** 统一行：左侧固定标签，右侧取值按钮按 weight 填满窄栏，不再写死 178dp。 */
    private Button addRow(LinearLayout parent, String label, View.OnClickListener l) {
        LinearLayout row = Ui.hbox(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.tv(this, label, 15, 0xFFC7D2E2, Gravity.LEFT),
                new LinearLayout.LayoutParams(Ui.dp(this, LABEL_DP),
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        Button b = Ui.button(this, "", Ui.BTN_BG, 0xFFF5F7FA, 14);
        b.setSingleLine(false);
        b.setMaxLines(4);
        b.setEllipsize(null);
        b.setMinHeight(Ui.dp(this, 44));
        b.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 2));
        b.setOnClickListener(l);
        Ui.focus(b, this, Ui.BTN_BG, 0xFFF5F7FA);
        // 高度随系统字体缩放自适应，避免大字模式下两行文案被固定 44dp 裁切。
        row.addView(b, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row, rowLp());
        track(b);
        return b;
    }

    private void addButton(LinearLayout parent, String label, int bg, View.OnClickListener l) {
        Button b = Ui.button(this, label, bg, 0xFFFFFFFF, 15);
        b.setOnClickListener(l);
        Ui.focus(b, this, bg, 0xFFFFFFFF);
        b.setMinHeight(Ui.dp(this, 44));
        parent.addView(b, rowLp());
        track(b);
    }

    private View buildSettings() {
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 36), Ui.dp(this, 10), Ui.dp(this, 36), Ui.dp(this, 8));

        root.addView(Ui.tv(this, "家长设置", 24, 0xFFFFFFFF), Ui.fixed(this, 36));
        pinState = Ui.tv(this, "", 13, Ui.MUTED, Gravity.LEFT);
        pinState.setPadding(Ui.dp(this, GUTTER_DP), 0, 0, 0);
        root.addView(pinState, Ui.fixed(this, 22));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(true);
        LinearLayout shell = Ui.vbox(this);
        shell.setPadding(Ui.dp(this, GUTTER_DP), Ui.dp(this, 4),
                Ui.dp(this, GUTTER_DP), Ui.dp(this, 16));
        LinearLayout col = Ui.vbox(this);
        shell.addView(col, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sv.addView(shell, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 01 出题基础 ----
        LinearLayout quiz = addCard(col, "01  出题基础");
        // v1.3.1：二维码入口从最底部的 06 卡片提到第一屏第一行。
        rowQr = addRow(quiz, "手机扫码管理", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, PairActivity.class));
            }
        });
        rowQr.setText("点开扫码 ›");
        note(quiz, "点开即显示二维码：手机与电视连同一 WiFi，扫码后输入家长密码即可管理；"
                + "默认不监听，进入本页后开启，可随时关闭。");
        rowGrade = addRow(quiz, "所在年级", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.grade(st.grade() % SettingsStore.MAX_GRADE + 1); refreshRows();
            }
        });
        rowInterval = addRow(quiz, "出题频率", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int i = (st.intervalIndex() + 1) % SettingsStore.INTERVALS.length;
                st.intervalSec(SettingsStore.INTERVALS[i]);
                refreshRows();
            }
        });
        rowScope = addRow(quiz, "出题范围", new View.OnClickListener() {
            @Override public void onClick(View v) { st.allSubjects(!st.allSubjects()); refreshRows(); }
        });
        rowTerm = addRow(quiz, "学习学期", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.termIndex((st.termIndex() + 1) % Syllabus.TERM_LABELS.length);
                st.unitLimit(Math.min(st.unitLimit(),
                        syllabus.maxUnits(st.grade(), st.termIndex(), st.editionMode())));
                refreshRows();
            }
        });
        rowUnit = addRow(quiz, "已学单元", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int max = syllabus.maxUnits(st.grade(), st.termIndex(), st.editionMode());
                if (max == 0) {
                    Toast.makeText(SettingsActivity.this,
                            "本年级 / 学期暂无单元进度表，仅按学期抽题",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                st.unitLimit((st.unitLimit() + 1) % (max + 1));
                refreshRows();
            }
        });
        rowEdition = addRow(quiz, "教材学制", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int next = (st.editionMode() + 1) % Syllabus.EDITION_LABELS.length;
                st.editionMode(next);
                st.unitLimit(Math.min(st.unitLimit(),
                        syllabus.maxUnits(st.grade(), st.termIndex(), next)));
                refreshRows();
            }
        });
        rowStreak = addRow(quiz, "连续答对", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.unlockStreak(st.unlockStreak() % SettingsStore.STREAK_MAX + 1);
                refreshRows();
            }
        });
        rowStrict = addRow(quiz, "严格模式", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.strictOneShot(!st.strictOneShot()); refreshRows();
            }
        });
        rowCooldown = addRow(quiz, "冷却惩罚", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.cooldownEnabled(!st.cooldownEnabled()); refreshRows();
            }
        });
        rowSource = addRow(quiz, "出题偏好", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.questionSource(st.questionSource() == SettingsStore.SOURCE_ALL
                        ? SettingsStore.SOURCE_KNOWLEDGE_ONLY : SettingsStore.SOURCE_ALL);
                refreshRows();
            }
        });
        rowDifficulty = addRow(quiz, "题目难度", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.difficultyLevel((st.difficultyLevel() + 1) % 4);
                refreshRows();
            }
        });
        rowAutoContinue = addRow(quiz, "答对自动继续", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.autoContinueOnCorrect(!st.autoContinueOnCorrect()); refreshRows();
            }
        });
        rowRoundSize = addRow(quiz, "每组题量", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int[] opts = {3, 5, 8, 10};
                int cur = st.questionsPerRound();
                int next = opts[0];
                for (int i = 0; i < opts.length; i++) {
                    if (opts[i] == cur) next = opts[(i + 1) % opts.length];
                }
                st.questionsPerRound(next);
                refreshRows();
            }
        });
        rowPassRate = addRow(quiz, "通过正确率", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int[] opts = {60, 70, 80, 100};
                int cur = st.passPercent();
                int next = opts[0];
                for (int i = 0; i < opts.length; i++) {
                    if (opts[i] == cur) next = opts[(i + 1) % opts.length];
                }
                st.passPercent(next);
                refreshRows();
            }
        });
        note(quiz, "每组题量与通过正确率仅对「按组答题」生效，默认一组 5 题、答对 60% 通过；"
                + "在「连续答对」行把门槛调回 1 题即可恢复旧版单题解锁。");
        note(quiz, "点击右侧取值即可循环切换，离开设置页时统一保存。");
        note(quiz, "题目难度为倾向加权（约 80% 按所选档抽题），默认「不限」不改变原有抽题行为；站点难度系数存在噪声，不保证绝对分档。");
        note(quiz, "默认：答对 1 题即过关、严格模式关、无冷却惩罚、全部题目、答对自动继续。「严格模式 / 冷却惩罚 / 出题偏好 / 题目难度」是可选开关，不动就和旧版一样。");
        note(quiz, "「答对自动继续」默认开启：达到解锁门槛后直接关闭答题页，不用再按一次「继续观看」；关掉则保留原来的确认按钮。");
        note(quiz, "出题偏好＝仅知识/计算题时，仍按所选学期与已学单元抽题，绝不串到另一册；选不到题时交参数化生成器补位。");
        note(quiz, "题库按真实教材册次与课标知识点组织：先选上 / 下册，再选已学到第几单元；"
                + "「教材学制」可切自动 / 六三制 / 五四制，每套学制有独立单元表，按版本族隔离，绝不串题。"
                + "选不到题时依次放宽单元 → 学期 → 年级，始终不换学科。默认自动 / 全学年 / 不限。");

        // ---- 02 学科设置 ----
        LinearLayout subjects = addCard(col, "02  学科设置 · 题目配比");
        for (int s = 0; s < Subjects.COUNT; s++) addSubjectRow(subjects, s);
        note(subjects, "配比按四科权重随机抽题，0 表示该科不出题；「其他学科」配比仅在「出题范围 = 全学科」且题库确有其他学科题时生效，"
                + "关闭或题库不可用时显示「未启用」/ 自动跳过，但仍可预先设置。内置离线题库按课标纲目组织。");

        // ---- 03 开机与播放 ----
        LinearLayout boot = addCard(col, "03  开机与播放");
        rowBoot = addRow(boot, "开机自启", new View.OnClickListener() {
            @Override public void onClick(View v) { st.bootAutostart(!st.bootAutostart()); refreshRows(); }
        });
        rowAutoGuard = addRow(boot, "自动守护", new View.OnClickListener() {
            @Override public void onClick(View v) { st.autoGuardOnBoot(!st.autoGuardOnBoot()); refreshRows(); }
        });
        rowPause = addRow(boot, "暂停视频", new View.OnClickListener() {
            @Override public void onClick(View v) { st.pauseOnQuiz(!st.pauseOnQuiz()); refreshRows(); }
        });
        rowResume = addRow(boot, "恢复播放", new View.OnClickListener() {
            @Override public void onClick(View v) { st.resumePlayback(!st.resumePlayback()); refreshRows(); }
        });
        addButton(boot, "开机自启引导", 0xFF2C6E8F, new View.OnClickListener() {
            @Override public void onClick(View v) {
                BootSettings.showGuide(SettingsActivity.this);
            }
        });
        addButton(boot, "授权悬浮窗", 0xFF243044, new View.OnClickListener() {
            @Override public void onClick(View v) { openOverlaySettings(); }
        });
        note(boot, "「启动即守护」默认开启：打开 APP 即自动进入守护；「开机自启」默认关闭，"
                + "需家长自行开启并允许厂商自启动权限后，才会随开机拉起。");
        note(boot, BootGuide.pathFor(android.os.Build.MANUFACTURER));
        note(boot, BootGuide.fallbackHint());
        note(boot, "暂停视频是 best-effort：靠音频焦点 + 媒体键请求播放器暂停，不保证所有视频 App 响应。");
        note(boot, "「授权悬浮窗」原在控制台，现移至本卡片；未授权时守护与立即出题都不可用。");

        // ---- 04 错题库 ----
        LinearLayout wrong = addCard(col, "04  错题库");
        addButton(wrong, "查看错题库", 0xFFB06A2C, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, WrongBookActivity.class));
            }
        });
        note(wrong, "同一题答错 " + AnswerPolicy.WRONG_LIMIT + " 次自动换题并记录。");

        // ---- 05 在线题库源 ----
        LinearLayout online = addCard(col, "05  在线题库源（可选 · 需自行配置）");
        urlEdit = new EditText(this);
        urlEdit.setSingleLine(true);
        urlEdit.setHint("https://your-server/  (index.json 所在目录)");
        urlEdit.setTextSize(14);
        urlEdit.setTextColor(0xFFF5F7FA);
        urlEdit.setHintTextColor(0xFF7C8AA3);
        urlEdit.setBackground(Ui.round(0xFF1B2432, 8, this));
        urlEdit.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        urlEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                if (!urlProgrammatic) urlDirty = true;
            }
        });
        online.addView(urlEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42)));
        track(urlEdit);

        addButton(online, "填入示例地址", 0xFF243044, new View.OnClickListener() {
            @Override public void onClick(View v) {
                presetIdx = (presetIdx + 1) % PRESETS.length;
                setUrlText(PRESETS[presetIdx], true);
            }
        });

        bankStatus = Ui.tv(this, "", 13, Ui.MUTED, Gravity.LEFT);
        bankStatus.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        bankStatus.setMinHeight(Ui.dp(this, 72));
        bankStatus.setBackground(Ui.round(0xFF1B2432, 10, this));
        online.addView(bankStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 在线源操作行：左「检查题库更新」触发同步，右「自动更新」循环切换周期。
        // 两个按钮并排且共用同一行焦点链：左右键在本行内切换，上下键仍沿单列链移动。
        LinearLayout bankRow = Ui.hbox(this);
        bankRow.setGravity(Gravity.CENTER_VERTICAL);
        Button btnCheck = Ui.button(this, "检查题库更新", 0xFF6C4F9E, 0xFFFFFFFF, 15);
        btnCheck.setSingleLine(false);
        btnCheck.setMaxLines(4);
        btnCheck.setEllipsize(null);
        btnCheck.setMinHeight(Ui.dp(this, 44));
        btnCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String url = urlEdit.getText().toString().trim();
                if (url.isEmpty()) {
                    Toast.makeText(SettingsActivity.this,
                            "当前使用内置离线题库；如需在线源请先填写地址",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                st.bankUrl(url);
                urlDirty = false;
                bankStatus.setText("正在同步题库…");
                BankRepository.sync(getApplicationContext(), st, url, true,
                        new BankRepository.Callback() {
                            @Override public void onDone(BankRepository.SyncResult r) {
                                updateBankStatus();
                                Toast.makeText(SettingsActivity.this, r.message, Toast.LENGTH_LONG).show();
                                sendReload();
                            }
                        });
            }
        });
        Ui.focus(btnCheck, this, 0xFF6C4F9E, 0xFFFFFFFF);
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        checkLp.setMargins(0, 0, Ui.dp(this, 6), 0);
        bankRow.addView(btnCheck, checkLp);

        rowSync = Ui.button(this, "自动更新", Ui.BTN_BG, 0xFFF5F7FA, 14);
        rowSync.setSingleLine(false);
        rowSync.setMaxLines(4);
        rowSync.setEllipsize(null);
        rowSync.setMinHeight(Ui.dp(this, 44));
        rowSync.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 2));
        rowSync.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.syncIndex((st.syncIndex() + 1) % SettingsStore.SYNC_HOURS.length);
                refreshRows();
            }
        });
        Ui.focus(rowSync, this, Ui.BTN_BG, 0xFFF5F7FA);
        LinearLayout.LayoutParams syncLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        syncLp.setMargins(Ui.dp(this, 6), 0, 0, 0);
        bankRow.addView(rowSync, syncLp);
        online.addView(bankRow, rowLp());
        track(btnCheck, rowSync);
        bankPair = new Button[]{btnCheck, rowSync};
        // 离线说明紧跟「检查题库更新」：它解释的是在线源开关本身，不要和打赏说明挤在一起。
        note(online, "默认完全离线：内置全量 K12 题库，不联网；只有填写在线源地址后才会按周期联网。");
        // 打赏入口：纯静态收款码，不联网、不含支付 SDK。
        addButton(online, "打赏作者 · 请喝一杯咖啡", 0xFFB5762E, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, DonateActivity.class));
            }
        });
        note(online, "如果觉得 PoHW 好用，可以请作者喝一杯咖啡。完全自愿、不影响任何功能；"
                + "点开后可用遥控器左右键切换微信支付 / 支付宝收款码，用手机扫码即可。");

        // ---- 06 版本更新（联网仅用于取清单与下载安装包）----
        LinearLayout upd = addCard(col, "06  版本更新（可选 · 默认开启检查）");
        note(upd, "联网只用于读取版本清单与下载安装包；题库、守护与答题全程离线，不上传任何设备信息。");
        rowUpdate = addRow(upd, "自动检查版本", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.autoCheckUpdate(!st.autoCheckUpdate());
                refreshRows();
            }
        });
        addButton(upd, "立即检查版本", 0xFF185FA5, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (updateChecking) {
                    Toast.makeText(SettingsActivity.this, "正在检查版本，请稍候",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                updateChecking = true;
                updateStatus.setText("正在检查版本…");
                Toast.makeText(SettingsActivity.this, "正在检查版本…",
                        Toast.LENGTH_SHORT).show();
                checkUpdate();
            }
        });
        btnUpdateInstall = Ui.button(this, "下载并安装新版本", 0xFF1D9E75, 0xFFFFFFFF, 15);
        btnUpdateInstall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                UpdateConfig.Info info = UpdateState.pending;
                if (info == null) {
                    Toast.makeText(SettingsActivity.this,
                            "当前已是最新版本；如需重新检查请点「立即检查版本」",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (updateDownloading) {
                    Toast.makeText(SettingsActivity.this, "正在下载，请稍候",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                updateDownloading = true;
                updateStatus.setText("正在下载新版本…");
                Toast.makeText(SettingsActivity.this, "开始下载新版本…",
                        Toast.LENGTH_SHORT).show();
                UpdateInstaller.download(SettingsActivity.this, info, new UpdateInstaller.Listener() {
                    @Override public void onProgress(final String msg) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() { updateStatus.setText(msg); }
                        });
                    }
                    @Override public void onDone(final boolean ok, final String msg) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                updateDownloading = false;
                                updateStatus.setText(msg);
                                Toast.makeText(SettingsActivity.this, msg,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            }
        });
        Ui.focus(btnUpdateInstall, this, 0xFF1D9E75, 0xFFFFFFFF);
        btnUpdateInstall.setMinHeight(Ui.dp(this, 44));
        upd.addView(btnUpdateInstall, rowLp());
        track(btnUpdateInstall);
        updateStatus = Ui.tv(this, "", 13, Ui.MUTED, Gravity.LEFT);
        updateStatus.setLineSpacing(0, 1.15f);
        updateStatus.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        upd.addView(updateStatus, rowLp());
        note(upd, "发现新版本只会提示；下载与安装需家长主动点击，系统安装界面仍需手动确认。");

        // ---- 07 生效时段与节假日（issue #37，默认全关）----
        LinearLayout sched = addCard(col, "07  生效时段与节假日（可选 · 默认关闭）");
        note(sched, "开启后只在这些时段弹题。上学日 / 在家日由内置节假日日历自动判定，"
                + "含法定假日与调休补班（周末要上学）。精确日期与多段时间请在手机管理页填写；"
                + "时段外只拦新题，守护服务与已弹出的题都不受影响。");
        rowSchedule = addRow(sched, "生效时段", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.scheduleEnabled(!st.scheduleEnabled());
                refreshRows();
            }
        });
        rowSchoolWin = addRow(sched, "上学日可答题", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int i = presetIndex(st.schoolWindows(), SCHOOL_PRESETS);
                st.schoolWindows(SCHOOL_PRESETS[(i + 1) % SCHOOL_PRESETS.length]);
                refreshRows();
            }
        });
        rowHomeWin = addRow(sched, "在家日可答题", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int i = presetIndex(st.homeWindows(), HOME_PRESETS);
                st.homeWindows(HOME_PRESETS[(i + 1) % HOME_PRESETS.length]);
                refreshRows();
            }
        });
        rowSkipHolidays = addRow(sched, "智能跳过节假日", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.skipHolidays(!st.skipHolidays());
                refreshRows();
            }
        });
        rowVacation = addRow(sched, "假期模式（寒暑假）", new View.OnClickListener() {
            @Override public void onClick(View v) {
                String[] presets = vacationPresets();
                int i = 0;
                for (int k = 0; k < presets.length; k++) {
                    if (presets[k].equals(st.vacationRange())) { i = k; break; }
                }
                st.vacationRange(presets[(i + 1) % presets.length]);
                refreshRows();
            }
        });
        rowTodayOverride = addRow(sched, "今天临时", new View.OnClickListener() {
            @Override public void onClick(View v) {
                String today = HolidayCalendar.dateKey(java.util.Calendar.getInstance());
                String[] presets = {"", today + ":allow", today + ":block"};
                int i = 0;
                for (int k = 0; k < presets.length; k++) {
                    if (presets[k].equals(st.todayOverride())) { i = k; break; }
                }
                st.todayOverride(presets[(i + 1) % presets.length]);
                refreshRows();
            }
        });

        root.addView(sv, Ui.fill(1));

        // ---- 底部固定操作条：长表单滚动时仍可随时保存 ----
        LinearLayout footer = Ui.hbox(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(Ui.dp(this, GUTTER_DP), 0, Ui.dp(this, GUTTER_DP), 0);
        btnChangePin = Ui.button(this, "修改家长密码", 0xFFA32D2D, 0xFFFFFFFF, 15);
        btnChangePin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { gate.startChange(); enterGate(false); }
        });
        Ui.focus(btnChangePin, this, 0xFFA32D2D, 0xFFFFFFFF);
        btnSave = Ui.button(this, "保存并返回", 0xFF1D9E75, 0xFFFFFFFF, 15);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        Ui.focus(btnSave, this, 0xFF1D9E75, 0xFFFFFFFF);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        fp.setMargins(Ui.dp(this, 5), Ui.dp(this, 4), Ui.dp(this, 5), Ui.dp(this, 4));
        footer.addView(btnChangePin, fp);
        footer.addView(btnSave, fp);
        root.addView(footer, Ui.fixed(this, 54));
        track(btnChangePin, btnSave);
        btnChangePin.setNextFocusRightId(ensureId(btnSave));
        btnSave.setNextFocusLeftId(ensureId(btnChangePin));

        wireVerticalFocus();
        wireSubjectRows();
        return root;
    }


    private static int presetIndex(String value, String[] presets) {
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equals(value)) return i;
        }
        return -1;
    }

    /** 手机端可能写了任意合法窗口，电视端如实显示而不是显示内部字符串。 */
    private String windowLabel(String value, String[] presets, String[] labels) {
        int i = presetIndex(value, presets);
        if (i >= 0) return labels[i];
        java.util.List<SchedulePolicy.Window> ws = SchedulePolicy.parseWindows(value);
        if (ws.isEmpty()) return "未设置（点选预设）";
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < ws.size(); k++) {
            if (k > 0) b.append(" / ");
            SchedulePolicy.Window w = ws.get(k);
            b.append(String.format("%02d:%02d–%02d:%02d",
                    Integer.valueOf(w.start / 60), Integer.valueOf(w.start % 60),
                    Integer.valueOf(w.end / 60), Integer.valueOf(w.end % 60)));
        }
        return b.toString();
    }

    /** 未设置 / 当年暑假 / 跨年寒假；年份动态算，不会写死过期。 */
    private static String[] vacationPresets() {
        int y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        return new String[]{
                "",
                y + "-07-01~" + y + "-08-31",
                y + "-12-20~" + (y + 1) + "-02-28"
        };
    }
    private String intervalLabel() {
        int sec = st.intervalSec();
        for (int i = 0; i < SettingsStore.INTERVALS.length; i++) {
            if (SettingsStore.INTERVALS[i] == sec) return SettingsStore.INTERVAL_LABELS[i];
        }
        return sec + " 秒";
    }

    /** 统一写入 URL 输入框：程序回显不算用户编辑，示例地址算用户编辑。 */
    private void setUrlText(String text, boolean userEdited) {
        if (urlEdit == null) return;
        urlProgrammatic = true;
        try {
            urlEdit.setText(text == null ? "" : text);
            urlEdit.setSelection(urlEdit.getText().length());
        } finally {
            urlProgrammatic = false;
        }
        if (userEdited) urlDirty = true;
    }

    /**
     * 单列纵向焦点链：把每一行的可聚焦控件连到上一行 / 下一行的首个可聚焦控件。
     * ScrollView 内跨卡片嵌套 ViewGroup 时，系统默认找不到邻居，显式连接才不会卡住。
     * 学科行有两个按钮，按「上」回到上一行首值、按「下」去下一行首值，符合遥控器直觉。
     */
    private void wireVerticalFocus() {
        for (int i = 0; i < focusRows.size(); i++) {
            View[] row = focusRows.get(i);
            View up = i > 0 ? firstUsable(focusRows.get(i - 1)) : null;
            View down = i < focusRows.size() - 1 ? firstUsable(focusRows.get(i + 1)) : null;
            for (View v : row) {
                if (v == null || !v.isEnabled() || !v.isFocusable()) continue;
                if (up != null) v.setNextFocusUpId(ensureId(up));
                if (down != null) v.setNextFocusDownId(ensureId(down));
                // 单列布局没有横向邻居：把未显式定义的左右目标指向自身，
                // 阻止系统按几何邻近把焦点甩到上一行 / 下一行。
                // EditText 例外：左右键留给光标移动（输入在线源 URL）。
                if (!(v instanceof EditText)) {
                    int self = ensureId(v);
                    if (v.getNextFocusLeftId() == View.NO_ID) v.setNextFocusLeftId(self);
                    if (v.getNextFocusRightId() == View.NO_ID) v.setNextFocusRightId(self);
                }
            }
        }
    }

    private static View firstUsable(View[] row) {
        for (View v : row) {
            if (v != null && v.isEnabled() && v.isFocusable()) return v;
        }
        return null;
    }

    /**
     * 学科设置行：学科名 + 题目配比。四科都可配权，第 4 槽在全学科关闭时标注「未启用」。
     */
    private void addSubjectRow(LinearLayout parent, final int subj) {
        LinearLayout row = Ui.hbox(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.tv(this, Subjects.name(subj), 14, 0xFFC7D2E2, Gravity.LEFT),
                new LinearLayout.LayoutParams(
                        Ui.dp(this, SUBJ_LABEL_DP), ViewGroup.LayoutParams.WRAP_CONTENT));

        Button wt = Ui.button(this, "", Ui.BTN_BG, 0xFFF5F7FA, 14);
        wt.setSingleLine(false);
        wt.setMaxLines(4);
        wt.setEllipsize(null);
        wt.setMinHeight(Ui.dp(this, 44));
        wt.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
        // 四科都可配权：全学科关闭时第 4 槽仍可预设，只是不参与抽题。
        wt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.weight(subj, (st.weight(subj) + 1) % (SubjectWeights.MAX_WEIGHT + 1));
                refreshRows();
            }
        });
        Ui.focus(wt, this, Ui.BTN_BG, 0xFFF5F7FA);
        row.addView(wt, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        track(wt);

        parent.addView(row, rowLp());
        rowWt[subj] = wt;
    }

    /** 在线源并排行：检查题库更新 ↔ 自动更新，左右键在本行内切换。 */
    private void wireSubjectRows() {
        // 在线源并排行：检查题库更新 ↔ 自动更新，左右键在本行内切换。
        if (bankPair != null && bankPair[0] != null && bankPair[1] != null
                && bankPair[1].isEnabled()) {
            int leftId = ensureId(bankPair[0]);
            int rightId = ensureId(bankPair[1]);
            bankPair[0].setNextFocusRightId(rightId);
            bankPair[1].setNextFocusLeftId(leftId);
        }
    }

    private static int ensureId(View v) {
        if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        return v.getId();
    }

    /** 悬浮窗授权入口（控制台已让位给练习模式，统一放在本卡片）。 */
    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, "此系统版本请在系统设置中允许悬浮窗",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try { startActivity(i); }
        catch (Exception e) {
            Toast.makeText(this, "该设备没有悬浮窗授权入口", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshRows() {
        rowGrade.setText(st.gradeLabel());
        rowInterval.setText(intervalLabel());
        rowStreak.setText(st.unlockStreak() > 1
                ? ("连续答对 " + st.unlockStreak() + " 题") : "答对 1 题即可");
        rowStrict.setText(st.strictOneShot() ? "开启（每题 1 次机会）" : "关闭（每题 2 次机会）");
        rowCooldown.setText(st.cooldownEnabled() ? "开启（答错递增冷却）" : "关闭（默认不惩罚）");
        rowDifficulty.setText(new String[]{"不限（默认）", "低 · 偏易", "中", "高 · 偏难"}[st.difficultyLevel()]);
        rowAutoContinue.setText(st.autoContinueOnCorrect()
                ? "开启（答对直接继续）" : "关闭（需点继续观看）");
        rowRoundSize.setText(st.questionsPerRound() + " 题 / 组（默认 5）");
        rowPassRate.setText(st.passPercent() + "% 答对即通过（默认 60%）");
        rowSource.setText(st.questionSource() == SettingsStore.SOURCE_KNOWLEDGE_ONLY
                ? "仅知识/计算题" : "全部题目");
        rowScope.setText(st.allSubjects() ? "全学科（含物理/历史等）" : "仅数 / 语 / 英");
        rowSync.setText("自动更新 · " + SettingsStore.SYNC_LABELS[st.syncIndex()]);
        rowTerm.setText(Syllabus.TERM_LABELS[st.termIndex()]);
        int maxUnit = syllabus.maxUnits(st.grade(), st.termIndex(), st.editionMode());
        int lim = st.unitLimit();
        if (maxUnit == 0) {
            rowUnit.setText("本册暂无进度表 · 仅学期过滤");
        } else if (lim == 0) {
            rowUnit.setText("不限（整册 " + maxUnit + " 单元）");
        } else {
            rowUnit.setText("已学到 " + syllabus.unitLabel(st.grade(), st.termIndex(),
                    Subjects.MATH, Math.min(lim, maxUnit), st.editionMode()));
        }
        int em = st.editionMode();
        String fam = syllabus.editionFor(st.grade(), st.termIndex(), Subjects.MATH, em);
        String edLabel = Syllabus.EDITION_LABELS[em];
        if (em != Syllabus.EDITION_AUTO) {
            // 全学年没有「某一册」的代表族，不能据此误报本册无题。
            if (st.termIndex() == Syllabus.TERM_ALL) {
                edLabel += "（全学年）";
            } else {
                edLabel += fam.isEmpty() ? "（本册暂无该学制题）" : (" · " + fam);
            }
        }
        rowEdition.setText(edLabel);
        for (int s = 0; s < Subjects.COUNT; s++) {
            String w = "配比 " + st.weight(s);
            if (s == Subjects.OTHER && !st.allSubjects()) w += " · 未启用";
            rowWt[s].setText(w);
        }
        rowBoot.setText(st.bootAutostart() ? "开启（响应开机广播）" : "关闭（需家长手动开启）");
        rowAutoGuard.setText(st.autoGuardOnBoot() ? "开启（打开 APP 时自动守护）" : "关闭（仅恢复上次）");
        rowPause.setText(st.pauseOnQuiz() ? "开启（音频焦点+媒体键）" : "关闭");
        rowResume.setText(st.resumePlayback() ? "开启（尝试恢复）" : "关闭");
        if (!urlDirty) setUrlText(st.bankUrl(), false);
        pinState.setText(st.hasPin() ? "家长密码：已设置 ✓" : "家长密码：未设置 ✗");
        updateBankStatus();
        if (rowSchedule != null) {
            HolidayCalendar hc = HolidayAssets.get(this);
            rowSchedule.setText(st.scheduleEnabled()
                    ? "开启（仅生效时段内弹题）" : "关闭（全天可弹题 · 默认）");
            rowSchoolWin.setText(windowLabel(st.schoolWindows(),
                    SCHOOL_PRESETS, SCHOOL_PRESET_LABELS));
            rowHomeWin.setText(windowLabel(st.homeWindows(),
                    HOME_PRESETS, HOME_PRESET_LABELS));
            rowSkipHolidays.setText(st.skipHolidays()
                    ? ("开启（日历至 " + hc.lastCoveredYear() + " 年）") : "关闭（仅按星期）");
            String vr = st.vacationRange();
            rowVacation.setText(vr.isEmpty() ? "未设置（点选寒暑假）" : vr.replace("~", " 至 "));
            String ov = st.todayOverride();
            rowTodayOverride.setText(ov.isEmpty() ? "无（按日程）"
                    : (ov.endsWith(":allow") ? "今天全天放行（隔天自动失效）"
                                            : "今天全天屏蔽（隔天自动失效）"));
        }
        if (rowUpdate != null) {
            rowUpdate.setText(st.autoCheckUpdate()
                    ? "开启（每 24 小时检查一次）" : "关闭（仅手动检查）");
            String um = UpdateState.message;
            UpdateConfig.Info p = UpdateState.pending;
            updateStatus.setText((um == null || um.length() == 0
                    ? "尚未检查版本" : um)
                    + (p == null ? "" : NL + "更新内容：" + p.notes));
            btnUpdateInstall.setText(p == null
                      ? "下载并安装新版本（暂无更新）"
                      : "下载并安装新版本 v" + p.versionName);
              // 状态变化后按当前 enabled 重建纵向链，避免焦点卡在某一行。
              wireVerticalFocus();
        }
    }


    /** 家长手动点「立即检查版本」：与冷启动共用 UpdateState，结果只提示不自动下载。 */
    private void checkUpdate() {
        final int localCode;
        try {
            localCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            updateChecking = false;
            updateStatus.setText("无法读取当前版本");
            Toast.makeText(this, "无法读取当前版本", Toast.LENGTH_LONG).show();
            return;
        }
        st.lastUpdateCheckMs(System.currentTimeMillis());
        final String channel = getString(R.string.update_channel);
        new Thread(new Runnable() {
            @Override public void run() {
                final UpdateConfig.Info info = UpdateChecker.fetch(channel);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        UpdateState.checkedAt = System.currentTimeMillis();
                        if (info == null) {
                            UpdateState.message = "版本检查未完成，已忽略（离线功能不受影响）";
                        } else {
                            boolean has = UpdateConfig.hasUpdate(localCode, info.versionCode,
                                    st.skipUpdateCode());
                            UpdateState.pending = has ? info : null;
                            UpdateState.message = has
                                    ? "发现新版本 " + info.versionName + "（build "
                                      + info.versionCode + "）"
                                    : "已是最新版本 " + info.versionName;
                        }
                        updateChecking = false;
                          refreshRows();
                          Toast.makeText(SettingsActivity.this, UpdateState.message,
                                  Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "pohw-update-check").start();
    }

    private void updateBankStatus() {
        bankStatus.setText("题库来源：" + st.bankSource() + NL
                + "版本 " + st.bankVersion() + " · " + st.bankQuestions()
                + " 题 / " + st.bankPacks() + " 包" + NL
                + "资源更新 " + st.bankUpdated() + " · 上次检查 " + ts(st.bankChecked()) + NL
                + st.bankMessage());
    }

    private void sendReload() {
        if (!QuizOverlayService.isRunning()) return;
        Intent i = new Intent(this, QuizOverlayService.class);
        i.setAction(QuizOverlayService.ACTION_RELOAD);
        try { startService(i); } catch (Exception ignored) { }
    }

    private void save() {
        st.bankUrl(urlEdit.getText().toString().trim());
        urlDirty = false;
        sendReload();
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
