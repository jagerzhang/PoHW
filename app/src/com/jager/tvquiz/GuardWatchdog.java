package com.jager.tvquiz;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

/**
 * v1.3.0 未发布：常驻服务的第二条、第三条路。
 *
 * <p>很多国产电视 / 盒子默认拦截第三方开机广播，也会在省电策略里掐掉
 * JobScheduler，但放行 AlarmManager。这里用「持久化 Job + 不精确重复闹钟」
 * 双兜底，进程被清后最迟约 15~20 分钟重新拉起守护。
 */
public final class GuardWatchdog extends JobService {

    public static final int JOB_ID = 7301;
    public static final int ALARM_ID = 7302;
    public static final long PERIOD_MS = 15L * 60L * 1000L;
    public static final String REVIVE_ACTION = "com.jager.tvquiz.REVIVE";

    @Override public boolean onStartJob(JobParameters p) {
        QuizOverlayService.ensureRunning(this, "job");
        return false;
    }

    @Override public boolean onStopJob(JobParameters p) { return true; }

    public static void schedule(Context c) {
        if (Build.VERSION.SDK_INT < 21) return;
        try {
            android.app.job.JobScheduler s = (android.app.job.JobScheduler)
                    c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (s == null) return;
            for (JobInfo j : s.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
            s.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, GuardWatchdog.class))
                    .setPeriodic(PERIOD_MS).setPersisted(true).build());
        } catch (Exception ignored) { }
    }

    public static void scheduleAlarm(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, ReviveReceiver.class).setAction(REVIVE_ACTION);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_ID, i, flags);
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 5L * 60L * 1000L, PERIOD_MS, pi);
        } catch (Exception ignored) { }
    }

    /** 服务被「最近任务划掉」后延时拉起；PendingIntent 走前台服务通道。 */
    public static void scheduleRestart(Context c, long delayMs) {
        try {
            Intent i = new Intent(c, QuizOverlayService.class)
                    .setAction(QuizOverlayService.ACTION_START);
            i.putExtra(QuizOverlayService.EXTRA_VIA, "taskRemoved");
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi;
            if (Build.VERSION.SDK_INT >= 26) {
                pi = PendingIntent.getForegroundService(c, 7303, i, flags);
            } else {
                pi = PendingIntent.getService(c, 7303, i, flags);
            }
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delayMs, pi);
        } catch (Exception ignored) { }
    }
}
