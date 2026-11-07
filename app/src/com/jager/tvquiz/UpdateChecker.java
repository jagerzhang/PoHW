package com.jager.tvquiz;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 只拉取 update.json：固定 HTTPS、禁止跨主机重定向、超时与体积上限。
 *
 * <p>不发任何设备标识、query 参数或自定义 UA，请求里不含个人信息。
 * 必须在后台线程调用；任何失败都返回 null，绝不把异常抛到 UI 或守护链路。
 */
public final class UpdateChecker {

    private static final int MAX_BYTES = 64 * 1024;

    private UpdateChecker() {}

    public static UpdateConfig.Info fetch(String channel) {
        String url = UpdateConfig.MANIFEST_URL;
        try {
            for (int redirects = 0; redirects <= 3; redirects++) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                InputStream in = null;
                try {
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(12000);
                    c.setRequestMethod("GET");
                    c.setInstanceFollowRedirects(false);
                    c.setRequestProperty("Accept", "application/json");
                    int code = c.getResponseCode();
                    if (code == 301 || code == 302 || code == 303
                            || code == 307 || code == 308) {
                        if (redirects == 3) return null;
                        String loc = c.getHeaderField("Location");
                        if (loc == null
                                || !loc.startsWith("https://" + UpdateConfig.HOST + "/")) {
                            return null;
                        }
                        url = loc;
                        continue;
                    }
                    if (code != 200) return null;
                    in = c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n, total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_BYTES) return null;
                        bo.write(buf, 0, n);
                    }
                    return UpdateConfig.parse(new String(bo.toByteArray(), "UTF-8"), channel);
                } finally {
                    if (in != null) { try { in.close(); } catch (Exception ignored) { } }
                    c.disconnect();
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
