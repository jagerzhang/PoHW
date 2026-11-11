package com.jager.tvquiz;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Android 侧读取 assets/holidays.json 的唯一入口。
 *
 * <p>与纯 Java 的 HolidayCalendar / SchedulePolicy 分开：那两个类要能在
 * JVM 单测里编译执行，不能引用 android.*。APK 内 assets 运行期不会变，
 * 这里读一次就缓存。表缺失或 JSON 损坏时降级为 HolidayCalendar.empty()，
 * 由 SchedulePolicy 退回星期掩码并在设置页提示「日历不可用」，绝不因为
 * 一张静态表让守护服务崩掉。
 */
public final class HolidayAssets {

    private static final String TAG = "PoHW.Holiday";
    private static final String ASSET = "holidays.json";
    private static HolidayCalendar cached;

    private HolidayAssets() { }

    public static synchronized HolidayCalendar get(Context c) {
        if (cached != null) return cached;
        InputStream in = null;
        try {
            in = c.getApplicationContext().getAssets().open(ASSET);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            cached = HolidayCalendar.fromJson(new String(out.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            Log.e(TAG, ASSET + " 读取失败，退化为纯星期掩码", e);
            cached = HolidayCalendar.empty();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) { }
            }
        }
        return cached;
    }
}
