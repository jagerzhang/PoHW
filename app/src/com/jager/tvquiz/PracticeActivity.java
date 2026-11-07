package com.jager.tvquiz;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 练习模式：小孩自己练答题的独立全屏页。
 *
 * 与守护完全解耦：不复用 WindowManager 遮罩，不引入计时、冷却、PIN 或连对门槛；
 * 只复用 QuizEngine.nextFrom(subject) 抽题与 QuizFlow 判题。参数（学科/年级/难度）
 * 存在 PracticeConfig 里，不影响家长设置的守护参数。
 *
 * 进入时给 QuizOverlayService 打「练习挂起」标志，练习期间不弹守护题；**服务不停止**，
 * 退出或进程被强杀后标志随进程消失，守护自动恢复，避免练习模式变成零成本绕过口子。
 */
public class PracticeActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PracticeConfig cfg = new PracticeConfig();
    private final QuizFlow flow = new QuizFlow();
    private final Button[] opts = new Button[4];

    private QuizDb db;
    private QuizEngine engine;
    private QuizEngine.Question current;
    private boolean alive = false;
    private boolean autoAdvancing = false;

    private Button rowSubject, rowGrade, rowDiff;
    private TextView meta, text, result, explain, stats;

    private final Runnable nextTask = new Runnable() {
        @Override public void run() { autoAdvancing = false; drawQuestion(true); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        alive = true;
        QuizOverlayService.setPracticeMode(true);
        setContentView(buildView());
        db = QuizDb.open(this);
        rebuildEngine();
        drawQuestion(true);
    }

    @Override protected void onDestroy() {
        alive = false;
        handler.removeCallbacks(nextTask);
        QuizOverlayService.setPracticeMode(false);
        super.onDestroy();
    }

    @Override public void onBackPressed() { finish(); }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_4) {
            submit(keyCode - KeyEvent.KEYCODE_1);
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_1 && keyCode <= KeyEvent.KEYCODE_NUMPAD_4) {
            submit(keyCode - KeyEvent.KEYCODE_NUMPAD_1);
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    private View buildView() {
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16));

        TextView banner = Ui.tv(this, "练习模式 · 守护已暂停弹题，返回后自动恢复", 15, Ui.AMBER);
        root.addView(banner, Ui.fixed(this, 26));
        TextView title = Ui.tv(this, "自由答题练习", 26, 0xFFFFFFFF);
        root.addView(title, Ui.fixed(this, 38));

        LinearLayout setup = Ui.hbox(this);
        rowSubject = setupButton(setup, "学科");
        rowGrade = setupButton(setup, "年级");
        rowDiff = setupButton(setup, "难度");
        root.addView(setup, Ui.fixed(this, 50));

        rowSubject.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cfg.cycleSubject(); rebuildEngine(); drawQuestion(false); }
        });
        rowGrade.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cfg.cycleGrade(); rebuildEngine(); drawQuestion(false); }
        });
        rowDiff.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cfg.cycleDifficulty(); rebuildEngine(); drawQuestion(false); }
        });

        LinearLayout card = Ui.vbox(this);
        card.setBackground(Ui.round(Ui.CARD, 18, this));
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));

        meta = Ui.tv(this, "", 14, Ui.MUTED);
        card.addView(meta, Ui.fixed(this, 24));
        text = Ui.tv(this, "", 22, Ui.TEXT);
        text.setMinHeight(Ui.dp(this, 66));
        // 题干必须拿到正的权重：Ui.fill(0) 在垂直 LinearLayout 中会被测成 0 高度，
        // 整张题目卡片会显示成「没有内容」（v1.2.4 修复）。
        card.addView(text, Ui.fill(3));

        for (int r = 0; r < 2; r++) {
            LinearLayout row = Ui.hbox(this);
            for (int c = 0; c < 2; c++) {
                final int idx = r * 2 + c;
                Button b = Ui.button(this, "", 0xFFE6F1FB, Ui.TEXT, 18);
                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1);
                lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
                b.setLayoutParams(lp);
                Ui.focus(b, this, 0xFFE6F1FB, Ui.TEXT);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { submit(idx); }
                });
                opts[idx] = b;
                row.addView(b);
            }
            // 按钮自身 56dp + 上下各 4dp margin = 64dp，行必须有真实高度。
            card.addView(row, Ui.fixed(this, 64));
        }

        result = Ui.tv(this, "", 20, Ui.GREEN);
        card.addView(result, Ui.fixed(this, 28));
        explain = Ui.tv(this, "", 15, Ui.MUTED);
        explain.setMinHeight(Ui.dp(this, 30));
        card.addView(explain, Ui.fill(1));
        root.addView(card, Ui.fill(1));

        LinearLayout bottom = Ui.hbox(this);
        stats = Ui.tv(this, "", 16, 0xFFFFFFFF);
        bottom.addView(stats, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        Button skip = Ui.button(this, "换一题", 0xFF243044, 0xFFFFFFFF, 16);
        skip.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 130), Ui.dp(this, 44)));
        Ui.focus(skip, this, 0xFF243044, 0xFFFFFFFF);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { drawQuestion(false); }
        });
        bottom.addView(skip);
        Button back = Ui.button(this, "返回控制台", Ui.BLUE, 0xFFFFFFFF, 16);
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 160), Ui.dp(this, 44)));
        Ui.focus(back, this, Ui.BLUE, 0xFFFFFFFF);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bottom.addView(back);
        root.addView(bottom, Ui.fixed(this, 50));
        return root;
    }

    private Button setupButton(LinearLayout parent, String label) {
        Button b = Ui.button(this, label, 0xFF243044, 0xFFFFFFFF, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1);
        lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        b.setLayoutParams(lp);
        Ui.focus(b, this, 0xFF243044, 0xFFFFFFFF);
        parent.addView(b);
        return b;
    }

    private void rebuildEngine() {
        engine = new QuizEngine(cfg.grade(), "", 1, 1, 1, 0);
        engine.setDb(db);
        engine.setDifficultyLevel(cfg.difficulty());
        rowSubject.setText("学科：" + cfg.subjectLabel());
        rowGrade.setText("年级：" + cfg.grade() + " 年级");
        rowDiff.setText("难度：" + cfg.difficultyLabel());
    }

    private void drawQuestion(boolean counted) {
        if (!alive) return;
        handler.removeCallbacks(nextTask);
        autoAdvancing = false;
        flow.reset();
        if (counted) cfg.onAsked();
        current = engine == null ? null : engine.nextFrom(cfg.subjectForDraw());
        if (current == null) {
            meta.setText("");
            text.setText("题库暂不可用，请返回控制台检查内置题库。");
            for (Button b : opts) { b.setText(""); b.setEnabled(false); }
            result.setText("");
            explain.setText("");
            stats.setText(cfg.summary());
            return;
        }
        meta.setText(Subjects.name(current.subject) + " · " + cfg.grade() + " 年级 · "
                + (current.topic == null ? "" : current.topic));
        text.setText(current.text);
        String[] labels = {"A", "B", "C", "D"};
        for (int i = 0; i < 4; i++) {
            String o = current.options != null && i < current.options.length
                    ? current.options[i] : "";
            opts[i].setText(labels[i] + "  " + o);
            opts[i].setEnabled(true);
        }
        result.setText("");
        explain.setText("");
        stats.setText(cfg.summary());
        opts[0].requestFocus();
    }

    private void submit(int idx) {
        if (!alive || current == null || autoAdvancing || idx < 0 || idx > 3) return;
        int outcome = flow.submit(idx, current.answerIndex);
        if (outcome == QuizFlow.IGNORE) return;
        if (outcome == QuizFlow.CORRECT) {
            cfg.onCorrect();
            result.setTextColor(Ui.GREEN);
            result.setText("✓ 答对啦！自动出下一题");
            explain.setText(current.explain == null ? "" : current.explain);
            for (Button b : opts) b.setEnabled(false);
            stats.setText(cfg.summary());
            autoAdvancing = true;
            handler.postDelayed(nextTask, 800);
            return;
        }
        cfg.onWrong();
        opts[idx].setEnabled(false);
        result.setTextColor(Ui.RED);
        result.setText("✗ 再想想");
        stats.setText(cfg.summary());
        if (outcome == QuizFlow.SWAP) {
            autoAdvancing = true;
            handler.postDelayed(nextTask, 700);
        }
    }
}
