package com.jager.tvquiz;

import java.util.Map;

/**
 * 在线版本更新的纯逻辑：清单解析、通道选择、版本比较、24 小时节流。
 *
 * <p>无 Android 依赖，可在 tests/run.sh 里直接编译执行。安全边界：下载 URL 只允许
 * 固定站点的 HTTPS /dl/ 路径，清单无法把 APP 指到任意主机；sha256 必须是 64 位
 * 十六进制；任何字段不合法都返回 null，由调用方静默忽略。
 */
public final class UpdateConfig {

    public static final String HOST = "pohw.zhangge.net";
    public static final String MANIFEST_URL = "https://" + HOST + "/update.json";
    public static final String CHANNEL_MAIN = "main";
    public static final String CHANNEL_LEGACY = "legacy";
    public static final long CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private UpdateConfig() {}

    /** 默认开启；lastCheckMs<=0 表示从未检查；系统时间倒退时重新检查。 */
    public static boolean shouldCheck(boolean enabled, long nowMs, long lastCheckMs) {
        if (!enabled) return false;
        if (lastCheckMs <= 0 || nowMs < lastCheckMs) return true;
        return nowMs - lastCheckMs >= CHECK_INTERVAL_MS;
    }

    /** 远端构建号必须严格大于本机；skipCode 用于「跳过此版本」。 */
    public static boolean hasUpdate(int localCode, int remoteCode, int skipCode) {
        return remoteCode > localCode && remoteCode != skipCode;
    }

    /** 只允许固定站点的 HTTPS /dl/ 下的 apk 地址，且拒绝路径穿越。 */
    public static boolean safeUrl(String u) {
        return u != null
                && u.startsWith("https://" + HOST + "/dl/")
                && u.endsWith(".apk")
                && !u.contains("..");
    }

    public static boolean validSha256(String s) {
        if (s == null || s.length() != 64) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }

    /** 清单里的一个通道；字段全为 final，解析后不可变。 */
    public static final class Info {
        public final String versionName;
        public final int versionCode;
        public final int minSdk;
        public final String latestUrl;
        public final String versionedUrl;
        public final String sha256;
        public final long size;
        public final String notes;

        private Info(String versionName, int versionCode, int minSdk,
                     String latestUrl, String versionedUrl, String sha256,
                     long size, String notes) {
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.minSdk = minSdk;
            this.latestUrl = latestUrl;
            this.versionedUrl = versionedUrl;
            this.sha256 = sha256;
            this.size = size;
            this.notes = notes;
        }

        /** 优先带版本号的 URL（天然绕开 CDN 旧缓存），不合法时回退 latest。 */
        public String downloadUrl() {
            if (safeUrl(versionedUrl)) return versionedUrl;
            if (safeUrl(latestUrl)) return latestUrl;
            return null;
        }
    }

    /** 解析指定通道；任何字段不合法或 JSON 损坏都返回 null。 */
    public static Info parse(String json, String channel) {
        try {
            Object root = Json.parse(json);
            if (!(root instanceof Map)) return null;
            Object ch = ((Map<?, ?>) root).get(channel);
            if (!(ch instanceof Map)) return null;
            Map<?, ?> m = (Map<?, ?>) ch;
            String name = str(m.get("versionName"));
            int code = intOf(m.get("versionCode"));
            int minSdk = intOf(m.get("minSdk"));
            String latest = str(m.get("latest"));
            String versioned = str(m.get("versioned"));
            String sha = str(m.get("sha256"));
            long size = longOf(m.get("size"));
            String notes = str(m.get("notes"));
            if (name == null || name.length() == 0 || code <= 0 || size <= 0) return null;
            if (!validSha256(sha)) return null;
            Info i = new Info(name, code, minSdk, latest, versioned, sha, size,
                    notes == null ? "" : notes);
            if (i.downloadUrl() == null) return null;
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o instanceof String ? (String) o : null;
    }

    private static int intOf(Object o) {
        if (o instanceof Long) return (int) ((Long) o).longValue();
        if (o instanceof Double) return (int) ((Double) o).doubleValue();
        return -1;
    }

    private static long longOf(Object o) {
        if (o instanceof Long) return ((Long) o).longValue();
        if (o instanceof Double) return (long) ((Double) o).doubleValue();
        return -1L;
    }
}
