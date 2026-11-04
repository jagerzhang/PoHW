package com.jager.tvquiz;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 全屏答题遮罩：可聚焦窗口，拦截返回键，支持遥控器 DPAD 操作。 */
public final class OverlayQuizView {

    public interface Listener {
        void onAnswer(int index);
        void onContinue();
    }

    /** 拦住 BACK；Home 属系统级按键，第三方窗口无法拦截。 */
    private static final class Root extends FrameLayout {
        Root(Context c) { super(c); }
        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
            return super.dispatchKeyEvent(e);
        }
    }

    private final Context c;
    private final Root root;
    private final TextView topic, text, result, explain, footer;
    private final Button[] opts = new Button[4];
    private final Button cont;
    private final Listener listener;

    public OverlayQuizView(Context ctx, Listener l) {
        c = ctx;
        listener = l;
        root = new Root(ctx);
        root.setBackgroundColor(0xF30B1018);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        LinearLayout card = Ui.vbox(c);
        card.setBackground(Ui.round(Ui.CARD, 18, c));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cp.setMargins(Ui.dp(c, 88), Ui.dp(c, 22), Ui.dp(c, 88), Ui.dp(c, 22));
        card.setLayoutParams(cp);
        card.setPadding(Ui.dp(c, 26), Ui.dp(c, 12), Ui.dp(c, 26), Ui.dp(c, 8));

        // 顶部是「标题 + 元信息」两行：必须 WRAP_CONTENT。
        // 老版本写死 42dp，18sp 两行约需 46~50dp，导致第二行下半被裁掉。
        topic = Ui.tv(c, "", 16, Ui.BLUE);
        topic.setLineSpacing(0, 1.15f);
        topic.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 6));
        card.addView(topic, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        text = Ui.tv(c, "", 30, Ui.TEXT);
        text.setGravity(Gravity.CENTER);
        text.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), 0);
        card.addView(text, Ui.fill(1));

        LinearLayout grid = Ui.hbox(c);
        grid.setBaselineAligned(false);
        LinearLayout col1 = Ui.vbox(c), col2 = Ui.vbox(c);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button b = Ui.button(c, "", Ui.BLUE_LIGHT, Ui.TEXT, 24);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { listener.onAnswer(idx); }
            });
            Ui.focus(b, c, Ui.BLUE_LIGHT, Ui.BLUE, Ui.TEXT);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            p.setMargins(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
            (i < 2 ? col1 : col2).addView(b, p);
            opts[i] = b;
        }
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        grid.addView(col1, colLp);
        grid.addView(col2, colLp);
        card.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 186)));

        result = Ui.tv(c, "", 21, Ui.GREEN);
        card.addView(result, Ui.fixed(c, 34));
        // 解析可能较长，不再固定 40dp 裁字
        explain = Ui.tv(c, "", 15, 0xFF5F6B7F);
        explain.setMinHeight(Ui.dp(c, 34));
        card.addView(explain, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cont = Ui.button(c, "继续观看", Ui.GREEN, 0xFFFFFFFF, 20);
        cont.setVisibility(View.INVISIBLE);
        cont.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onContinue(); }
        });
        Ui.focus(cont, c, Ui.GREEN, 0xFF0F6E56, 0xFFFFFFFF);
        LinearLayout.LayoutParams nb = new LinearLayout.LayoutParams(Ui.dp(c, 240), Ui.dp(c, 50));
        nb.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(cont, nb);

        footer = Ui.tv(c, "答对才能继续 · 返回键无效 · 家长可在设置里调整", 14, Ui.MUTED);
        card.addView(footer, Ui.fixed(c, 26));

        root.addView(card, cp);
    }

    public View view() { return root; }

    public void focusFirst() {
        root.requestFocus();
        opts[0].requestFocus();
    }

    public void bind(QuizEngine.Question q, String meta) {
        String src = "bank".equals(q.source) ? "在线题库" : "本地生成";
        topic.setText("该答题啦  ·  " + q.topic + "  ·  " + src + "\n" + meta);
        text.setText(q.text);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, QuizTextSizer.sp(q.text));
        result.setText("");
        explain.setText("");
        cont.setVisibility(View.INVISIBLE);
        for (int i = 0; i < 4; i++) {
            opts[i].setText(q.options[i]);
            opts[i].setEnabled(true);
            opts[i].setBackground(Ui.round(Ui.BLUE_LIGHT, 10, c));
            opts[i].setTextColor(Ui.TEXT);
        }
    }

    public void wrong(int idx, String msg) {
        result.setTextColor(Ui.RED);
        result.setText(msg);
        explain.setText("");
        if (idx >= 0 && idx < 4) opts[idx].setEnabled(false);
    }

    public void correct(String msg, String exp) {
        result.setTextColor(Ui.GREEN);
        result.setText(msg);
        explain.setText(exp);
        for (Button b : opts) b.setEnabled(false);
        cont.setVisibility(View.VISIBLE);
        cont.requestFocus();
    }
}
