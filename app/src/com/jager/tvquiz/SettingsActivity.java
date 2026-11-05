package com.jager.tvquiz;

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
 * 家长设置：密码门禁 + 三栏（出题 / 教材版本与自启 / 播放与题库）。
 * 每栏独立滚动，避免新增设置项在 540dp 高的电视屏上被裁掉。
 */
public class SettingsActivity extends Activity {

    private static final String NL = "\n";
    private static final String[] PRESETS = {"", "http://10.0.2.2:8000/", "http://127.0.0.1:8000/"};

    private SettingsStore st;
    private PinGate gate;
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
    private Button rowGrade, rowInterval, rowW0, rowW1, rowW2, rowScope, rowSync;
    private Button[] rowEd = new Button[Subjects.COUNT];
    private Button rowBoot, rowAutoGuard, rowPause, rowResume;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new SettingsStore(this);
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
                "忘记密码请在电脑执行：adb shell pm clear com.jager.tvquiz（会清空全部设置与统计）",
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

    // ---------- 设置表单 ----------

    private LinearLayout addColumn(LinearLayout parent, int rightPadDp) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(0, 0, Ui.dp(this, rightPadDp), 0);
        sv.addView(col);
        parent.addView(sv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return col;
    }

    private void section(LinearLayout parent, String text) {
        parent.addView(Ui.tv(this, text, 15, Ui.AMBER, Gravity.LEFT),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 26)));
    }

    private void note(LinearLayout parent, String text) {
        parent.addView(Ui.tv(this, text, 12, Ui.MUTED, Gravity.LEFT),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private Button addRow(LinearLayout parent, String label, View.OnClickListener l) {
        LinearLayout row = Ui.hbox(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.tv(this, label, 15, 0xFFC7D2E2, Gravity.LEFT);
        row.addView(t, new LinearLayout.LayoutParams(
                Ui.dp(this, 96), ViewGroup.LayoutParams.WRAP_CONTENT));
        Button b = Ui.button(this, "", Ui.BTN_BG, 0xFFF5F7FA, 13);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setEllipsize(null);
        b.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
        b.setOnClickListener(l);
        Ui.focus(b, this, Ui.BTN_BG, 0xFFF5F7FA);
        row.addView(b, new LinearLayout.LayoutParams(Ui.dp(this, 178), Ui.dp(this, 46)));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));
        return b;
    }

    private void addButton(LinearLayout parent, String label, int bg, View.OnClickListener l) {
        Button b = Ui.button(this, label, bg, 0xFFFFFFFF, 15);
        b.setOnClickListener(l);
        Ui.focus(b, this, bg, 0xFFFFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        p.setMargins(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
        parent.addView(b, p);
    }

    private View buildSettings() {
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 36), Ui.dp(this, 10), Ui.dp(this, 36), Ui.dp(this, 8));
        root.addView(Ui.tv(this, "家长设置", 24, 0xFFFFFFFF), Ui.fixed(this, 34));
        pinState = Ui.tv(this, "", 13, Ui.MUTED, Gravity.LEFT);
        root.addView(pinState, Ui.fixed(this, 22));

        LinearLayout content = Ui.hbox(this);
        content.setGravity(Gravity.TOP);
        LinearLayout left = addColumn(content, 16);
        LinearLayout mid = addColumn(content, 16);
        LinearLayout right = addColumn(content, 0);

        // ---- 栏 1：出题 ----
        section(left, "出题");
        rowGrade = addRow(left, "所在年级", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.grade(st.grade() % SettingsStore.MAX_GRADE + 1); refreshRows();
            }
        });
        rowInterval = addRow(left, "出题频率", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int i = (st.intervalIndex() + 1) % SettingsStore.INTERVALS.length;
                st.intervalSec(SettingsStore.INTERVALS[i]);
                refreshRows();
            }
        });
        rowW0 = addRow(left, "数学配比", new View.OnClickListener() {
            @Override public void onClick(View v) { st.weight(0, (st.weight(0) + 1) % 6); refreshRows(); }
        });
        rowW1 = addRow(left, "语文配比", new View.OnClickListener() {
            @Override public void onClick(View v) { st.weight(1, (st.weight(1) + 1) % 6); refreshRows(); }
        });
        rowW2 = addRow(left, "英语配比", new View.OnClickListener() {
            @Override public void onClick(View v) { st.weight(2, (st.weight(2) + 1) % 6); refreshRows(); }
        });
        rowScope = addRow(left, "出题范围", new View.OnClickListener() {
            @Override public void onClick(View v) { st.allSubjects(!st.allSubjects()); refreshRows(); }
        });
        rowSync = addRow(left, "自动更新", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.syncIndex((st.syncIndex() + 1) % SettingsStore.SYNC_HOURS.length);
                refreshRows();
            }
        });
        note(left, "点击每一行的值即可循环切换；配比 0 表示该科不出题。");

        // ---- 栏 2：分学科教材版本 + 开机自启 + 播放 ----
        section(mid, "分学科教材版本");
        for (int s = 0; s < Subjects.COUNT; s++) {
            final int subj = s;
            rowEd[s] = addRow(mid, Subjects.name(s) + "教材", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    st.setEditionIndexFor(subj,
                            (st.editionIndexFor(subj) + 1) % SettingsStore.EDITIONS.length);
                    refreshRows();
                }
            });
        }
        note(mid, "内置离线题库按课标纲目组织，不含出版社版本；本设置用于在线题包筛选、题面标注与错题标记。");

        section(mid, "开机与播放");
        rowBoot = addRow(mid, "开机自启", new View.OnClickListener() {
            @Override public void onClick(View v) { st.bootAutostart(!st.bootAutostart()); refreshRows(); }
        });
        rowAutoGuard = addRow(mid, "自动守护", new View.OnClickListener() {
            @Override public void onClick(View v) { st.autoGuardOnBoot(!st.autoGuardOnBoot()); refreshRows(); }
        });
        rowPause = addRow(mid, "暂停视频", new View.OnClickListener() {
            @Override public void onClick(View v) { st.pauseOnQuiz(!st.pauseOnQuiz()); refreshRows(); }
        });
        rowResume = addRow(mid, "恢复播放", new View.OnClickListener() {
            @Override public void onClick(View v) { st.resumePlayback(!st.resumePlayback()); refreshRows(); }
        });
        addButton(mid, "开启厂商自启动设置", 0xFF2C6E8F, new View.OnClickListener() {
            @Override public void onClick(View v) {
                BootSettings.open(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this,
                        "小米等 ROM 需允许「自启动」，否则收不到开机广播",
                        Toast.LENGTH_LONG).show();
            }
        });
        note(mid, "暂停视频是 best-effort：靠音频焦点 + 媒体键请求播放器暂停，不保证所有视频 App 响应。");

        // ---- 栏 3：错题库 + 在线题库源 + 账户 ----
        section(right, "错题库");
        addButton(right, "查看错题库", 0xFFB06A2C, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, WrongBookActivity.class));
            }
        });
        note(right, "同一题答错 " + AnswerPolicy.WRONG_LIMIT + " 次自动换题并记录。");

        section(right, "在线题库源（可选 · 需自行配置）");
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
        right.addView(urlEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42)));

        addButton(right, "填入示例地址", 0xFF243044, new View.OnClickListener() {
            @Override public void onClick(View v) {
                presetIdx = (presetIdx + 1) % PRESETS.length;
                setUrlText(PRESETS[presetIdx], true);
            }
        });

        bankStatus = Ui.tv(this, "", 13, Ui.MUTED, Gravity.LEFT);
        bankStatus.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        bankStatus.setBackground(Ui.round(0xFF1B2432, 10, this));
        right.addView(bankStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 88)));

        addButton(right, "立即检查更新", 0xFF6C4F9E, new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.bankUrl(urlEdit.getText().toString().trim());
                urlDirty = false;
                bankStatus.setText("正在同步题库…");
                BankRepository.sync(getApplicationContext(), st, st.bankUrl(), true,
                        new BankRepository.Callback() {
                            @Override public void onDone(BankRepository.SyncResult r) {
                                updateBankStatus();
                                Toast.makeText(SettingsActivity.this, r.message, Toast.LENGTH_LONG).show();
                                sendReload();
                            }
                        });
            }
        });
        addButton(right, "修改家长密码", 0xFFA32D2D, new View.OnClickListener() {
            @Override public void onClick(View v) {
                gate.startChange();
                enterGate(false);
            }
        });
        addButton(right, "保存并返回", 0xFF1D9E75, new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        note(right, "默认完全离线：内置全量 K12 题库，不联网；只有填写在线源地址后才会按周期联网。");

        // 三个独立 ScrollView 里的按钮默认不会被系统自动横向串起来，
        // 显式连左右邻居，保证 TV 遥控器在任意一行都能跨栏移动焦点。
        wireColumns(left, mid, right);
        root.addView(content, Ui.fill(1));
        return root;
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
     * TV DPAD 跨栏焦点链：给每栏所有可聚焦按钮生成 id，并把左右焦点指向相邻栏的代表按钮。
     * ScrollView 是独立可滚动容器，Android 默认只在同栏内找邻居，不显式连就会卡在单栏里。
     */
    private void wireColumns(LinearLayout... cols) {
        java.util.List<java.util.List<Button>> perCol = new java.util.ArrayList<java.util.List<Button>>();
        for (LinearLayout col : cols) {
            java.util.List<Button> bs = new java.util.ArrayList<Button>();
            for (int i = 0; i < col.getChildCount(); i++) {
                View v = col.getChildAt(i);
                if (v instanceof Button) { ensureId(v); bs.add((Button) v); }
                else if (v instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) v;
                    for (int j = 0; j < g.getChildCount(); j++) {
                        View inner = g.getChildAt(j);
                        if (inner instanceof Button) { ensureId(inner); bs.add((Button) inner); }
                    }
                }
            }
            perCol.add(bs);
        }
        if (perCol.size() < 2) return;
        for (int i = 0; i < perCol.size(); i++) {
            java.util.List<Button> cur = perCol.get(i);
            if (cur.isEmpty()) continue;
            java.util.List<Button> lCol = perCol.get((i - 1 + perCol.size()) % perCol.size());
            java.util.List<Button> rCol = perCol.get((i + 1) % perCol.size());
            int lid = lCol.isEmpty() ? View.NO_ID : ensureId(lCol.get(0));
            int rid = rCol.isEmpty() ? View.NO_ID : ensureId(rCol.get(0));
            for (Button b : cur) {
                if (lid != View.NO_ID) b.setNextFocusLeftId(lid);
                if (rid != View.NO_ID) b.setNextFocusRightId(rid);
            }
        }
        // URL 输入框不是 Button，单独接上中栏，避免按左键时焦点掉出表单。
        java.util.List<Button> middle = perCol.get(1);
        if (urlEdit != null && !middle.isEmpty()) {
            int mid = ensureId(middle.get(0));
            urlEdit.setNextFocusLeftId(mid);
            urlEdit.setNextFocusUpId(mid);
        }
    }

    private static int ensureId(View v) {
        if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        return v.getId();
    }

    private void refreshRows() {
        rowGrade.setText(st.gradeLabel());
        rowInterval.setText(intervalLabel());
        rowW0.setText("数学  " + st.weight(0));
        rowW1.setText("语文  " + st.weight(1));
        rowW2.setText("英语  " + st.weight(2));
        rowScope.setText(st.allSubjects() ? "全学科（含物理/历史等）" : "仅数 / 语 / 英");
        rowSync.setText(SettingsStore.SYNC_LABELS[st.syncIndex()]);
        for (int s = 0; s < Subjects.COUNT; s++) {
            rowEd[s].setText(st.editionFor(s));
        }
        rowBoot.setText(st.bootAutostart() ? "开启（响应开机广播）" : "关闭");
        rowAutoGuard.setText(st.autoGuardOnBoot() ? "开启（开机即守护）" : "关闭（仅恢复上次）");
        rowPause.setText(st.pauseOnQuiz() ? "开启（音频焦点+媒体键）" : "关闭");
        rowResume.setText(st.resumePlayback() ? "开启（尝试恢复）" : "关闭");
        if (!urlDirty) setUrlText(st.bankUrl(), false);
        pinState.setText(st.hasPin() ? "家长密码：已设置 ✓" : "家长密码：未设置 ✗");
        updateBankStatus();
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
