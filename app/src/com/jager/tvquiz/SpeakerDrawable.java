package com.jager.tvquiz;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * 代码绘制的喇叭图标：零图片资源、零 so，API 1 的 Canvas/Path 全部兼容低配包。
 * 颜色可随遥控器聚焦状态切换，避免为 normal / focused 各存一份资源。
 */
public final class SpeakerDrawable extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;

    public SpeakerDrawable(int pxSize, int color) {
        this.size = pxSize;
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setColor(color);
    }

    public void setColor(int color) {
        fill.setColor(color);
        stroke.setColor(color);
        invalidateSelf();
    }

    @Override public int getIntrinsicWidth() { return size; }

    @Override public int getIntrinsicHeight() { return size; }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        float w = b.width();
        float h = b.height();
        if (w <= 0 || h <= 0) return;
        float l = b.left, t = b.top;

        Path horn = new Path();
        horn.moveTo(l + 0.08f * w, t + 0.40f * h);
        horn.lineTo(l + 0.25f * w, t + 0.40f * h);
        horn.lineTo(l + 0.50f * w, t + 0.18f * h);
        horn.lineTo(l + 0.50f * w, t + 0.82f * h);
        horn.lineTo(l + 0.25f * w, t + 0.60f * h);
        horn.lineTo(l + 0.08f * w, t + 0.60f * h);
        horn.close();
        canvas.drawPath(horn, fill);

        stroke.setStrokeWidth(Math.max(1.5f, 0.055f * w));
        canvas.drawArc(new RectF(l + 0.54f * w, t + 0.30f * h,
                l + 0.84f * w, t + 0.70f * h), -55, 110, false, stroke);
        canvas.drawArc(new RectF(l + 0.60f * w, t + 0.17f * h,
                l + 0.98f * w, t + 0.83f * h), -50, 100, false, stroke);
    }

    @Override public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        stroke.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
        stroke.setColorFilter(cf);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
