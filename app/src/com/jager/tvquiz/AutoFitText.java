package com.jager.tvquiz;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * 按真实可用宽高测量后自动降字号：只用 API 1 就存在的 StaticLayout，
 * 低配包 minSdk 14 的 dex 不会引入高版本 API。
 *
 * <p>逐档用同一个 TextPaint 真实排版，取第一个「行数不超上限且总高度不超
 * 可用高度」的字号。View 需已完成首次 layout；由 OnPreDrawListener 触发。
 */
public final class AutoFitText {

    public static final int MIN_SP = 12;

    private AutoFitText() {}

    /**
     * @param v         已完成首次 layout 的 TextView / Button
     * @param maxSp     起始（最大）字号，通常由 QuizTextSizer 给出
     * @param maxLines  允许的最大行数
     * @param spacing   行距倍数，与 View 上 setLineSpacing(0, x) 保持一致
     * @return 实际采用的 sp 字号；宽高尚未确定时返回 0（由 OnPreDraw 再跑）
     */
    public static int fit(TextView v, int maxSp, int maxLines, float spacing) {
        if (v == null) return 0;
        CharSequence cs = v.getText();
        int w = v.getWidth() - v.getPaddingLeft() - v.getPaddingRight();
        int h = v.getHeight() - v.getPaddingTop() - v.getPaddingBottom();
        if (cs == null || cs.length() == 0 || w <= 0 || h <= 0) return 0;
        float density = v.getResources().getDisplayMetrics().density;
        if (density <= 0) return 0;
        int max = Math.max(MIN_SP, maxSp);
        int n = max - MIN_SP + 1;
        int[] ladder = new int[n];
        int[] lines = new int[n];
        int[] heights = new int[n];
        TextPaint paint = new TextPaint(v.getPaint());
        for (int i = 0; i < n; i++) {
            int sp = max - i;
            paint.setTextSize(sp * density);
            StaticLayout layout = new StaticLayout(cs, paint, w,
                    Layout.Alignment.ALIGN_NORMAL, spacing, 0f, false);
            ladder[i] = sp;
            lines[i] = layout.getLineCount();
            heights[i] = (int) Math.ceil(layout.getHeight() / density);
        }
        int availableDp = (int) Math.floor(h / density);
        int sp = TextFit.choose(ladder, lines, heights, availableDp, maxLines);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return sp;
    }
}
