package com.jager.tvquiz;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * 用户主动点击「下载并安装」后才联网下载 APK：流式校验 sha256 与大小，
 * 通过后才拉起系统安装界面。非系统应用无法静默安装，最后一步必须由用户确认。
 */
public final class UpdateInstaller {

    public interface Listener {
        void onProgress(String msg);
        void onDone(boolean ok, String msg);
    }

    private static final long MAX_APK = 25L * 1024L * 1024L;

    private UpdateInstaller() {}

    /** 应用私有目录；API 24+ 由 UpdateProvider 以 content:// 暴露给安装器。 */
    public static File apkDir(Context c) {
        File d = new File(c.getFilesDir(), "updates");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 在后台线程执行；下载前清空旧包，任何失败都删除临时文件，不留半截 APK。 */
    public static void download(final Context c, final UpdateConfig.Info info,
                                final Listener l) {
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                File dir = apkDir(app);
                File[] older = dir.listFiles();
                if (older != null) {
                    for (File f : older) if (f.isFile()) f.delete();
                }
                String first = info.downloadUrl();
                if (first == null) { l.onDone(false, "更新地址不合法"); return; }
                File out = new File(dir, "PoHW-" + info.versionCode + ".apk");
                HttpURLConnection conn = null;
                InputStream in = null;
                OutputStream os = null;
                try {
                    conn = open(first);
                    int code = conn.getResponseCode();
                    if (isRedirect(code)) {
                        String loc = conn.getHeaderField("Location");
                        conn.disconnect();
                        conn = null;
                        if (!UpdateConfig.safeUrl(loc)) {
                            l.onDone(false, "下载地址被拒绝"); return;
                        }
                        conn = open(loc);
                        code = conn.getResponseCode();
                    }
                    if (code != 200) { l.onDone(false, "下载失败 HTTP " + code); return; }
                    in = conn.getInputStream();
                    os = new FileOutputStream(out);
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] buf = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_APK) throw new IllegalStateException("APK 超过 25MB");
                        os.write(buf, 0, n);
                        md.update(buf, 0, n);
                    }
                    os.flush();
                    os.close();
                    os = null;
                    String actual = Hashes.hex(md.digest());
                    if (!info.sha256.equalsIgnoreCase(actual)) {
                        out.delete();
                        l.onDone(false, "校验失败，已丢弃安装包");
                        return;
                    }
                    if (total != info.size) {
                        out.delete();
                        l.onDone(false, "安装包大小不符，已丢弃");
                        return;
                    }
                    l.onProgress("校验通过，正在打开安装界面");
                    launchInstall(app, out);
                    l.onDone(true, "请在系统安装界面确认");
                } catch (Exception e) {
                    out.delete();
                    l.onDone(false, "下载失败：" + e.getClass().getSimpleName());
                } finally {
                    if (os != null) { try { os.close(); } catch (Exception ignored) { } }
                    if (in != null) { try { in.close(); } catch (Exception ignored) { } }
                    if (conn != null) conn.disconnect();
                }
            }
        }, "pohw-update").start();
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestMethod("GET");
        c.setInstanceFollowRedirects(false);
        return c;
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static void launchInstall(Context c, File f) {
        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = new Uri.Builder().scheme("content")
                    .authority(UpdateProvider.AUTHORITY)
                    .path("apk/" + f.getName()).build();
        } else {
            // API 21-23：系统安装器读不到应用私有目录，复制到外部目录再用 file://
            File pub = new File(c.getExternalFilesDir(null), "updates");
            if (!pub.exists()) pub.mkdirs();
            File copy = new File(pub, f.getName());
            if (!copyFile(f, copy)) return;
            uri = Uri.fromFile(copy);
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(i);
    }

    private static boolean copyFile(File src, File dst) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new java.io.FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (in != null) { try { in.close(); } catch (Exception ignored) { } }
            if (out != null) { try { out.close(); } catch (Exception ignored) { } }
        }
    }
}
