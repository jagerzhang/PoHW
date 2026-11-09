package com.jager.tvquiz;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** 管理页静态资源：只允许白名单文件名，杜绝路径穿越。 */
public final class AdminAssets {
    private AdminAssets() { }

    private static final String[] ALLOWED = {"index.html", "app.js", "style.css"};

    public static byte[] read(Context c, String name) {
        boolean ok = false;
        for (String a : ALLOWED) if (a.equals(name)) ok = true;
        if (!ok) return null;
        InputStream in = null;
        try {
            in = c.getAssets().open("admin/" + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    public static String type(String name) {
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        return "text/html; charset=utf-8";
    }
}
