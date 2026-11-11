package com.jager.tvquiz;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Android 4.0 低配分支的看门狗：只保留 AlarmManager 一条兜底路径。
 *
 * <p>主线用 JobService(API 21) + AlarmManager 双兜底；低配的 dex 里不能出现
 * 任何 API>14 的类，否则 Dalvik 在加载类（尤其父类 / 字段类型）时就会抛
 * NoClassDefFoundError，SDK_INT 运行时守卫挡不住。常量与方法签名保持与主线一致，
 * QuizOverlayService 无需改动调用点。
 */
public final class GuardWatchdog {

    public static final int JOB_ID = 7301;
    public static final int ALARM_ID = 7302;
    public static final long PERIOD_MS = 15L * 60L * 1000L;
    public static final String REVIVE_ACTION = "com.jager.tvquiz.REVIVE";

    private GuardWatchdog() {}

    /** 低配只注册不精确重复闹钟，不注册 Job。 */
    public static void schedule(Context c) {
        scheduleAlarm(c);
    }

    public static void scheduleAlarm(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, ReviveReceiver.class).setAction(REVIVE_ACTION);
            PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_ID, i,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 5L * 60L * 1000L, PERIOD_MS, pi);
        } catch (Exception ignored) { }
    }

    /** 服务被「最近任务划掉」后延时拉起；低配统一用 getService。 */
    public static void scheduleRestart(Context c, long delayMs) {
        try {
            Intent i = new Intent(c, QuizOverlayService.class)
                    .setAction(QuizOverlayService.ACTION_START);
            i.putExtra(QuizOverlayService.EXTRA_VIA, "taskRemoved");
            PendingIntent pi = PendingIntent.getService(c, 7303, i,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delayMs, pi);
        } catch (Exception ignored) { }
    }
}
