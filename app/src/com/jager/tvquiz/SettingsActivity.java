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
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 家长设置：SHA-256+salt 密码门禁、年级/教材版本/频率/学科配比、在线题库源与更新。 */
public class SettingsActivity extends Activity {

    private static final String NL = "\n";
    private static final String[] PRESETS = {"", "http://10.0.2.2:8000/", "http://192.168.1.100:8000/"};

    private SettingsStore st;
    private PinGate gate;
    private int presetIdx = 0;
    private boolean inSettings = false;

    private View gateView, settingsView;
    private TextView gateTitle, gateMask, gateMsg, gateState, bankStatus, pinState;
    private Button firstGateBtn;
    private EditText urlEdit;
    private Button rowGrade, rowEdition, rowInterval, rowW0, rowW1, rowW2, rowScope, rowSync;

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
                // 触屏模式下 focusableInTouchMode 会让首次点击行为因设备而异，门禁按钮统一关掉
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
        // TV 上显式给初始焦点，否则刚进页面时按确定键可能落不到任何按钮
        if (firstGateBtn != null) firstGateBtn.requestFocus();
        refreshGate();
    }

    private void enterSettings() { inSettings = true; setContentView(settingsView); refreshRows(); }

    // ---------- 设置表单 ----------

    private Button addRow(LinearLayout parent, String label, View.OnClickListener l) {
        LinearLayout row = Ui.hbox(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.tv(this, label, 17, 0xFFC7D2E2, Gravity.LEFT);
        row.addView(t, new LinearLayout.LayoutParams(
                Ui.dp(this, 140), ViewGroup.LayoutParams.WRAP_CONTENT));
        Button b = Ui.button(this, "", Ui.BTN_BG, 0xFFF5F7FA, 16);
        b.setOnClickListener(l);
        Ui.focus(b, this, Ui.BTN_BG, 0xFFF5F7FA);
        row.addView(b, new LinearLayout.LayoutParams(Ui.dp(this, 230), Ui.dp(this, 42)));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
        return b;
    }

    private void addButton(LinearLayout parent, String label, int bg, View.OnClickListener l) {
        Button b = Ui.button(this, label, bg, 0xFFFFFFFF, 16);
        b.setOnClickListener(l);
        Ui.focus(b, this, bg, 0xFFFFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        p.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        parent.addView(b, p);
    }

    private View buildSettings() {
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 36), Ui.dp(this, 12), Ui.dp(this, 36), Ui.dp(this, 10));
        root.addView(Ui.tv(this, "家长设置", 26, 0xFFFFFFFF), Ui.fixed(this, 38));
        pinState = Ui.tv(this, "", 14, Ui.MUTED, Gravity.LEFT);
        root.addView(pinState, Ui.fixed(this, 26));

        LinearLayout content = Ui.hbox(this);
        content.setGravity(Gravity.TOP);
        LinearLayout left = Ui.vbox(this), right = Ui.vbox(this);
        left.setPadding(0, 0, Ui.dp(this, 18), 0);
        right.setPadding(Ui.dp(this, 18), 0, 0, 0);

        rowGrade = addRow(left, "所在年级", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.grade(st.grade() % SettingsStore.MAX_GRADE + 1); refreshRows();
            }
        });
        rowEdition = addRow(left, "教材版本", new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.editionIndex((st.editionIndex() + 1) % SettingsStore.EDITIONS.length);
                refreshRows();
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
        left.addView(Ui.tv(this, "点击每一行的值即可循环切换；配比 0 表示该科不出题",
                13, Ui.MUTED, Gravity.LEFT), Ui.fixed(this, 34));

        right.addView(Ui.tv(this, "在线题库源（可选 · 需自行配置）", 16,
                0xFFC7D2E2, Gravity.LEFT), Ui.fixed(this, 28));
        urlEdit = new EditText(this);
        urlEdit.setSingleLine(true);
        urlEdit.setHint("https://your-server/  (index.json 所在目录)");
        urlEdit.setTextSize(15);
        urlEdit.setTextColor(0xFFF5F7FA);
        urlEdit.setHintTextColor(0xFF7C8AA3);
        urlEdit.setBackground(Ui.round(0xFF1B2432, 8, this));
        urlEdit.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        urlEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        right.addView(urlEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        addButton(right, "填入示例地址", 0xFF243044, new View.OnClickListener() {
            @Override public void onClick(View v) {
                presetIdx = (presetIdx + 1) % PRESETS.length;
                urlEdit.setText(PRESETS[presetIdx]);
            }
        });

        bankStatus = Ui.tv(this, "", 14, Ui.MUTED, Gravity.LEFT);
        bankStatus.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        bankStatus.setBackground(Ui.round(0xFF1B2432, 10, this));
        right.addView(bankStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 104)));

        addButton(right, "立即检查更新", 0xFF6C4F9E, new View.OnClickListener() {
            @Override public void onClick(View v) {
                st.bankUrl(urlEdit.getText().toString());
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

        LinearLayout.LayoutParams col = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        content.addView(left, col);
        content.addView(right, new LinearLayout.LayoutParams(col));
        root.addView(content, Ui.fill(1));
        root.addView(Ui.tv(this,
                "默认完全离线：内置全量 K12 题库，不联网；只有填写在线源地址后才会按周期联网。",
                13, Ui.MUTED), Ui.fixed(this, 26));
        return root;
    }

    private String intervalLabel() {
        int sec = st.intervalSec();
        for (int i = 0; i < SettingsStore.INTERVALS.length; i++) {
            if (SettingsStore.INTERVALS[i] == sec) return SettingsStore.INTERVAL_LABELS[i];
        }
        return sec + " 秒";
    }

    private void refreshRows() {
        rowGrade.setText(st.gradeLabel());
        rowEdition.setText(st.edition());
        rowInterval.setText(intervalLabel());
        rowW0.setText("数学  " + st.weight(0));
        rowW1.setText("语文  " + st.weight(1));
        rowW2.setText("英语  " + st.weight(2));
        rowScope.setText(st.allSubjects() ? "全学科（含物理/历史等）" : "仅数 / 语 / 英");
        rowSync.setText(SettingsStore.SYNC_LABELS[st.syncIndex()]);
        urlEdit.setText(st.bankUrl());
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
        st.bankUrl(urlEdit.getText().toString());
        sendReload();
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
