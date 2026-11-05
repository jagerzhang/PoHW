package com.jager.tvquiz;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 错题库浏览页：TV 遥控器友好的单题翻页卡片。
 * 展示题面、四个选项（标出正确答案与最后一次选错的选项）、解析、错误次数；
 * 支持删除本题与二次确认后清空。
 */
public class WrongBookActivity extends Activity {

    private WrongBook book;
    private List<WrongBook.Entry> items = new ArrayList<WrongBook.Entry>();
    private int pos = 0;
    private boolean clearArmed = false;

    private TextView title, detail;
    private Button btnPrev, btnNext, btnDelete, btnClear, btnBack;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        book = WrongBook.open(this);
        reload();
        setContentView(buildView());
        if (btnPrev != null) btnPrev.requestFocus();
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        reload();
        render();
    }

    private void reload() {
        items = (book == null) ? new ArrayList<WrongBook.Entry>() : book.list();
        if (pos >= items.size()) pos = 0;
        clearArmed = false;
        if (btnClear != null) btnClear.setText("清空错题库");
    }

    private Button addButton(LinearLayout row, String label, int bg,
                             View.OnClickListener l) {
        Button b = Ui.button(this, label, bg, 0xFFFFFFFF, 16);
        b.setOnClickListener(l);
        Ui.focus(b, this, bg, Ui.BLUE, 0xFFFFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                Ui.dp(this, 158), Ui.dp(this, 48));
        p.setMargins(Ui.dp(this, 5), 0, Ui.dp(this, 5), 0);
        row.addView(b, p);
        return b;
    }

    private View buildView() {
        LinearLayout l = Ui.vbox(this);
        l.setBackgroundColor(Ui.BG);
        l.setPadding(Ui.dp(this, 40), Ui.dp(this, 14), Ui.dp(this, 40), Ui.dp(this, 10));

        l.addView(Ui.tv(this, "错题库  ·  Proof of Homework", 26, 0xFFFFFFFF),
                Ui.fixed(this, 40));
        title = Ui.tv(this, "", 15, Ui.AMBER, Gravity.LEFT);
        l.addView(title, Ui.fixed(this, 26));

        detail = Ui.tv(this, "", 17, 0xFFE6EDF7, Gravity.LEFT);
        detail.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        detail.setBackground(Ui.round(0xFF1B2432, 12, this));
        detail.setLineSpacing(0, 1.22f);
        ScrollView detailScroll = new ScrollView(this);
        detailScroll.setFillViewport(true);
        detailScroll.addView(detail);
        l.addView(detailScroll, Ui.fill(1));

        LinearLayout row = Ui.hbox(this);
        btnPrev = addButton(row, "◀ 上一题", 0xFF243044, new View.OnClickListener() {
            @Override public void onClick(View v) { prev(); }
        });
        btnNext = addButton(row, "下一题 ▶", 0xFF243044, new View.OnClickListener() {
            @Override public void onClick(View v) { next(); }
        });
        btnDelete = addButton(row, "删除本题", 0xFFA32D2D, new View.OnClickListener() {
            @Override public void onClick(View v) { deleteCurrent(); }
        });
        btnClear = addButton(row, "清空错题库", 0xFF6C4F9E, new View.OnClickListener() {
            @Override public void onClick(View v) { clearAll(); }
        });
        btnBack = addButton(row, "返回控制台", 0xFF1D9E75, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        l.addView(row, Ui.fixed(this, 56));

        l.addView(Ui.tv(this,
                "遥控器方向键选中按钮后按确定；同一题答错 " + AnswerPolicy.WRONG_LIMIT
                        + " 次会自动换题并记录到这里。",
                13, Ui.MUTED), Ui.fixed(this, 24));
        return l;
    }

    private void prev() {
        if (items.isEmpty()) return;
        pos = (pos - 1 + items.size()) % items.size();
        render();
    }

    private void next() {
        if (items.isEmpty()) return;
        pos = (pos + 1) % items.size();
        render();
    }

    private void deleteCurrent() {
        if (book == null || items.isEmpty()) return;
        long id = items.get(pos).id;
        book.delete(id);
        reload();
        render();
        Toast.makeText(this, "已删除这道错题", Toast.LENGTH_SHORT).show();
    }

    private void clearAll() {
        if (book == null || items.isEmpty()) return;
        if (!clearArmed) {
            clearArmed = true;
            btnClear.setText("再按一次确认清空");
            Toast.makeText(this, "再按一次「清空错题库」确认", Toast.LENGTH_SHORT).show();
            return;
        }
        book.clear();
        reload();
        render();
        Toast.makeText(this, "错题库已清空", Toast.LENGTH_SHORT).show();
    }

    private static String ts(long ms) {
        if (ms <= 0) return "-";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }

    private void render() {
        if (items.isEmpty()) {
            title.setText("共 0 题 · 错题库为空");
            detail.setText("还没有错题。\n\n"
                    + "答题时同一题累计答错 " + AnswerPolicy.WRONG_LIMIT
                    + " 次会自动换一道新题，并把原题记入错题库。\n"
                    + "这里可以随时查看正确答案、解析和你当时选错的选项。");
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);
            btnDelete.setEnabled(false);
            btnClear.setEnabled(false);
            return;
        }

        btnPrev.setEnabled(items.size() > 1);
        btnNext.setEnabled(items.size() > 1);
        btnDelete.setEnabled(true);
        btnClear.setEnabled(true);

        WrongBook.Entry e = items.get(pos);
        title.setText("错题库 · 共 " + items.size() + " 题 · 当前第 " + (pos + 1) + " 题");

        StringBuilder s = new StringBuilder();
        s.append("答错 ").append(e.wrongCount).append(" 次   ·   最近 ").append(ts(e.lastWrongAt))
                .append("\n");
        s.append(e.gradeLabel).append(" · ").append(e.subject).append(" · ")
                .append(e.edition).append(" · ").append(e.topic).append("\n\n");
        s.append("题目：").append(e.text).append("\n\n");
        for (int i = 0; i < 4; i++) {
            String mark = i == e.answerIndex ? "✓ 正确"
                    : (e.lastPick.equals(e.options[i]) ? "✗ 你选的" : "      ");
            s.append(mark).append("  ").append((char) ('A' + i)).append(". ")
                    .append(e.options[i]).append('\n');
        }
        s.append("\n解析：").append(e.explain.isEmpty() ? "（暂无）" : e.explain);
        detail.setText(s.toString());
    }
}
