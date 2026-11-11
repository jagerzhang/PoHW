package com.jager.tvquiz;

import android.app.Notification;
import android.content.Context;
import android.os.Build;

/**
 * Android 4.0 低配分支的通知兼容层。
 *
 * <p>API 26 起前台服务通知必须绑定 NotificationChannel，否则系统会异步抛
 * CannotPostForegroundServiceNotificationException 直接杀进程（try/catch 挡不住）。
 * 但低配包的 dex 又不能引用任何 API>14 的类，否则 Dalvik 加载类就崩。
 *
 * <p>因此这里只用反射：类名与方法名都是字符串，不产生 Landroid/app/NotificationChannel;
 * 类型引用；且整段只在 SDK_INT>=26 时执行，Android 4 完全不会走这条路径。
 */
public final class NotifCompat {

    private NotifCompat() {}

    /** 返回可直接 setContentTitle/... 的 Builder；高版本自动建好 channel。 */
    public static Notification.Builder builder(Context c, String channelId) {
        if (Build.VERSION.SDK_INT < 26) return new Notification.Builder(c);
        try {
            Class<?> channel = Class.forName("android.app.NotificationChannel");
            Object nm = c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                Object ch = channel
                        .getConstructor(String.class, CharSequence.class, int.class)
                        .newInstance(channelId, "答题守护", 2); // IMPORTANCE_LOW
                nm.getClass().getMethod("createNotificationChannel", channel)
                        .invoke(nm, ch);
            }
            return (Notification.Builder) Notification.Builder.class
                    .getConstructor(Context.class, String.class)
                    .newInstance(c, channelId);
        } catch (Throwable ignored) {
            return new Notification.Builder(c);
        }
    }
}
