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

/**
 * 全屏答题遮罩。
 *
 * 遥控器输入不依赖 Android 的 View 焦点树：所有按键在窗口根视图的
 * dispatchKeyEvent 中由本类自己路由（方向键移动高亮、确定键提交）。
 * 老实现复用同一个 View 实例反复 addView/removeView，答对时焦点留在
 * 「继续观看」按钮上，第二次挂载时窗口焦点状态不确定，会出现选项选不中。
 * 现在由 QuizOverlayService 每次弹题新建本视图，且按键路径完全自主，
 * 与窗口是否拿到 View 焦点无关；同时显式清掉 FLAG_NOT_FOCUSABLE。
 */
public final class OverlayQuizView {

    public interface Listener {
        void onAnswer(int index);
        void onContinue();
    }

    private static final class Root extends FrameLayout {
        private OverlayQuizView owner;

        Root(Context c) { super(c); }

        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            if (owner != null && owner.onKey(e)) return true;
            return super.dispatchKeyEvent(e);
        }
    }

    /** 2x2 选项矩阵的方向键邻接表：LEFT / RIGHT / UP / DOWN。 */
    private static final int[][] NEXT = {
            {0, 1, 0, 1},
            {2, 3, 2, 3},
            {0, 0, 2, 2},
            {1, 1, 3, 3},
    };

    private final Context c;
    private final Root root;
    private final TextView topic, text, result, explain, footer;
    private final Button[] opts = new Button[4];
    private final Button cont;
    private final Listener listener;

    private final boolean[] disabled = new boolean[4];
    private int selected = 0;
    private boolean answered = false;

    public OverlayQuizView(Context ctx, Listener l) {
        c = ctx;
        listener = l;

        root = new Root(ctx);
        root.owner = this;
        root.setBackgroundColor(0xF30B1018);
        // 根视图自己持有窗口焦点；子按钮不参与焦点树，避免 add/remove 后焦点残留。
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        LinearLayout card = Ui.vbox(c);
        card.setBackground(Ui.round(Ui.CARD, 18, c));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cp.setMargins(Ui.dp(c, 88), Ui.dp(c, 22), Ui.dp(c, 88), Ui.dp(c, 22));
        card.setLayoutParams(cp);
        card.setPadding(Ui.dp(c, 26), Ui.dp(c, 12), Ui.dp(c, 26), Ui.dp(c, 8));

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
            b.setFocusable(false);
            b.setFocusableInTouchMode(false);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { submit(idx); }
            });
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
        explain = Ui.tv(c, "", 15, 0xFF5F6B7F);
        explain.setMinHeight(Ui.dp(c, 34));
        card.addView(explain, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cont = Ui.button(c, "继续观看", Ui.GREEN, 0xFFFFFFFF, 20);
        cont.setFocusable(false);
        cont.setFocusableInTouchMode(false);
        cont.setVisibility(View.INVISIBLE);
        cont.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (answered) listener.onContinue(); }
        });
        LinearLayout.LayoutParams nb = new LinearLayout.LayoutParams(Ui.dp(c, 240), Ui.dp(c, 50));
        nb.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(cont, nb);

        footer = Ui.tv(c,
                "方向键选择 · 确定/数字键 1-4 作答 · 答错 " + AnswerPolicy.WRONG_LIMIT
                        + " 次自动换题 · 返回键无效 · 答对才能继续",
                14, Ui.MUTED);
        card.addView(footer, Ui.fixed(c, 26));

        root.addView(card, cp);
        paint();
    }

    public View view() { return root; }

    /** addView 之后 post 调用，确保窗口已完成首次 attach/traversal。 */
    public void focusFirst() {
        root.requestFocus();
        paint();
    }

    public void bind(QuizEngine.Question q, String meta) {
        String src = "bank".equals(q.source) ? "在线题库" : "本地生成";
        topic.setText("该答题啦  ·  " + q.topic + "  ·  " + src + "\n" + meta);
        text.setText(q.text);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, QuizTextSizer.sp(q.text));
        result.setText("");
        explain.setText("");
        cont.setVisibility(View.INVISIBLE);
        answered = false;
        for (int i = 0; i < 4; i++) {
            disabled[i] = false;
            opts[i].setText(q.options != null && i < q.options.length
                    ? (q.options[i] == null ? "" : q.options[i]) : "");
        }
        selected = 0;
        paint();
    }

    /** 答错一次：禁用被选错的选项，高亮自动移到下一个可选项。 */
    public void wrong(int idx, String msg) {
        if (idx >= 0 && idx < 4) disabled[idx] = true;
        result.setTextColor(Ui.RED);
        result.setText(msg);
        explain.setText("");
        selectEnabled();
        paint();
    }

    /** 换题后给一句提示，不禁用任何选项。 */
    public void hint(String msg) {
        result.setTextColor(Ui.AMBER);
        result.setText(msg);
        explain.setText("");
    }

    public void correct(String msg, String exp) {
        answered = true;
        result.setTextColor(Ui.GREEN);
        result.setText(msg);
        explain.setText(exp);
        cont.setVisibility(View.VISIBLE);
        paint();
    }

    // ---------- 自主键盘路由：不依赖子 View 焦点 ----------

    private boolean onKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_UP) {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        } else {
            // 抬起事件一并消费，避免落到下层应用
            return true;
        }

        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN
                || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false; // 音量键交给系统
        }
        if (k == KeyEvent.KEYCODE_BACK) return true; // 拦截返回键

        // 答题期间吞掉媒体键，防止用遥控器把后台视频重新播放起来
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE
                || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_NEXT
                || k == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || k == KeyEvent.KEYCODE_MEDIA_REWIND) {
            return true;
        }

        boolean confirm = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (confirm && e.getRepeatCount() > 0) return true;

        if (answered) {
            if (confirm) listener.onContinue();
            return true;
        }

        if (confirm) { submit(selected); return true; }

        if (k == KeyEvent.KEYCODE_DPAD_LEFT) { move(0); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { move(1); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_UP) { move(2); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_DOWN) { move(3); return true; }

        if (k >= KeyEvent.KEYCODE_1 && k <= KeyEvent.KEYCODE_4) {
            submit(k - KeyEvent.KEYCODE_1);
            return true;
        }
        if (k >= KeyEvent.KEYCODE_NUMPAD_1 && k <= KeyEvent.KEYCODE_NUMPAD_4) {
            submit(k - KeyEvent.KEYCODE_NUMPAD_1);
            return true;
        }
        // 其余按键全部消费，答题期间不把按键透给底层应用
        return true;
    }

    private void move(int dir) {
        int target = NEXT[dir][selected];
        if (disabled[target]) {
            for (int i = 1; i < 4; i++) {
                int cand = (target + i) % 4;
                if (!disabled[cand]) { target = cand; break; }
            }
        }
        selected = target;
        paint();
    }

    private void submit(int idx) {
        if (answered || idx < 0 || idx > 3 || disabled[idx]) return;
        selected = idx;
        paint();
        listener.onAnswer(idx);
    }

    private void selectEnabled() {
        if (!disabled[selected]) return;
        for (int i = 0; i < 4; i++) {
            if (!disabled[i]) { selected = i; return; }
        }
    }

    /** 手动绘制高亮，不使用 OnFocusChangeListener。 */
    private void paint() {
        for (int i = 0; i < 4; i++) {
            Button b = opts[i];
            b.setEnabled(!disabled[i] && !answered);
            if (disabled[i]) {
                b.setBackground(Ui.round(0xFFE3CFD4, 10, c));
                b.setTextColor(0xFF9A7C86);
            } else if (answered) {
                b.setBackground(Ui.round(0xFFDDE6F2, 10, c));
                b.setTextColor(0xFF6A7891);
            } else if (i == selected) {
                b.setBackground(Ui.round(Ui.BLUE, 10, c));
                b.setTextColor(0xFFFFFFFF);
            } else {
                b.setBackground(Ui.round(Ui.BLUE_LIGHT, 10, c));
                b.setTextColor(Ui.TEXT);
            }
        }
    }
}
