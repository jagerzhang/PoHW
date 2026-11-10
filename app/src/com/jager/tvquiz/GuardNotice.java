package com.jager.tvquiz;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * v1.3.3：权限被系统撤销时给家长一条可点击的高优先级通知。
 *
 * <p>只说明「授权丢失、设置没丢」并直接跳系统授权页，不自动改任何设置，
 * 也不含网络请求。没有 POST_NOTIFICATIONS 权限时静默失败，由首页诊断行兜底。
 */
public final class GuardNotice {

    private static final String TAG = "PoHW.Notice";
    private static final String CHANNEL = "pohw_permission_alert";
    private static final int ID_REVOKED = 7304;

    private GuardNotice() {}

    public static void overlayRevoked(Context c) {
        try {
            NotificationManager nm = (NotificationManager)
                    c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL,
                        "守护权限提醒", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("悬浮窗授权被系统撤销时提醒家长重新授权");
                nm.createNotificationChannel(ch);
            }
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(c, ID_REVOKED, i, flags);

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(c, CHANNEL)
                    : new Notification.Builder(c);
            b.setSmallIcon(R.drawable.ic_stat_quiz)
                    .setContentTitle("悬浮窗授权被系统撤销")
                    .setContentText("守护已暂停，设置没有丢失；点此重新授权")
                    .setStyle(new Notification.BigTextStyle().bigText(
                            "部分电视重启后会撤销「显示在其他应用上层」权限。"
                                    + "家长密码、年级与守护开关都还在，重新授权后守护会自动恢复。"))
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(ID_REVOKED, b.build());
        } catch (Exception e) {
            Log.w(TAG, "cannot post permission notice", e);
        }
    }
}
