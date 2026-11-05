package com.jager.tvquiz;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Android 侧读取 assets/syllabus.json 的唯一入口。
 *
 * <p>刻意与纯 Java 的 Syllabus / Json 分开：那两个类要能在 JVM 单测里编译执行，
 * 不能引用 android.*。这里做一次读取并缓存（APK 内 assets 运行期不会变），
 * 表缺失或 JSON 损坏时降级为 Syllabus.empty()，只保留学期过滤，
 * 绝不因为一张静态表让守护服务或家长设置崩掉。
 */
public final class SyllabusAssets {

    private static final String TAG = "PoHW.Syllabus";
    private static final String ASSET = "syllabus.json";
    private static Syllabus cached;

    private SyllabusAssets() { }

    public static synchronized Syllabus get(Context c) {
        if (cached != null) return cached;
        InputStream in = null;
        try {
            in = c.getApplicationContext().getAssets().open(ASSET);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            cached = Syllabus.fromJson(new String(out.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            Log.e(TAG, ASSET + " 读取失败，退化为仅学期过滤", e);
            cached = Syllabus.empty();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) { }
            }
        }
        return cached;
    }
}
