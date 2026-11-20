package com.jager.tvquiz;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 到点由系统闹钟直接拉起前台服务：不依赖 APP 进程常驻，也不依赖厂商是否发
 * SCREEN_ON / USER_PRESENT 广播。电视浅待机把第三方进程冻结或查杀后，
 * ELAPSED_REALTIME_WAKEUP 闹钟仍会唤醒 CPU 并启动服务。
 *
 * <p>只用 API 1 就存在的 AlarmManager / PendingIntent 类；高版本方法都带
 * SDK_INT 分支，低配 minSdk 14 的 dex 不引入任何 API>14 的类型。
 */
public final class QuizAlarm {

    public static final int ID = 7305;

    private QuizAlarm() {}

    /** deadlineElapsed 是 SystemClock.elapsedRealtime() 基准的绝对时刻。 */
    public static void schedule(Context c, long deadlineElapsed) {
        if (deadlineElapsed <= 0L) return;
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, QuizOverlayService.class)
                    .setAction(QuizOverlayService.ACTION_QUIZ_ALARM);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi;
            if (Build.VERSION.SDK_INT >= 26) {
                pi = PendingIntent.getForegroundService(c, ID, i, flags);
            } else {
                pi = PendingIntent.getService(c, ID, i, flags);
            }
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        deadlineElapsed, pi);
            } else if (Build.VERSION.SDK_INT >= 19) {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadlineElapsed, pi);
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadlineElapsed, pi);
            }
        } catch (Exception ignored) { }
    }

    /** 家长主动关闭守护时撤销，避免闹钟把守护又拉起来。 */
    public static void cancel(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, QuizOverlayService.class)
                    .setAction(QuizOverlayService.ACTION_QUIZ_ALARM);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi;
            if (Build.VERSION.SDK_INT >= 26) {
                pi = PendingIntent.getForegroundService(c, ID, i, flags);
            } else {
                pi = PendingIntent.getService(c, ID, i, flags);
            }
            am.cancel(pi);
        } catch (Exception ignored) { }
    }
}
