package com.jager.tvquiz;

import android.content.Context;
import android.content.Intent;
import java.security.SecureRandom;

/**
 * v1.3.0 未发布：局域网管理 API。所有写操作与状态读取都要 Bearer token。
 *
 * <p>token 只存内存、进程重启失效；PIN 连续失败 5 次锁 60 秒；
 * 只暴露白名单路径，不提供任意文件读取。数据全部留在设备本地。
 */
public final class AdminApi {

    private static volatile String token = null;
    private static volatile int failures = 0;
    private static volatile long lockUntil = 0L;
    private static final SecureRandom RND = new SecureRandom();

    private AdminApi() { }

    public static void reset() { token = null; failures = 0; lockUntil = 0L; }

    public static boolean authorized(String auth) {
        return token != null && auth != null && auth.equals("Bearer " + token);
    }

    /** 返回 HTTP 状态码与 JSON 正文。 */
    public static Result handle(Context c, String method, String path,
                                String auth, String body) {
        if ("/api/login".equals(path) && "POST".equals(method)) {
            if (System.currentTimeMillis() < lockUntil) {
                return new Result(429, "{\"error\":\"尝试过多，请稍后再试\"}");
            }
            String pin = jsonString(body, "pin");
            SettingsStore st = new SettingsStore(c);
            if (!st.hasPin() || pin == null || !st.checkPin(pin)) {
                failures++;
                if (failures >= 5) lockUntil = System.currentTimeMillis() + 60000L;
                return new Result(401, "{\"error\":\"密码不正确\"}");
            }
            failures = 0;
            byte[] b = new byte[24];
            RND.nextBytes(b);
            token = Hashes.hex(b);
            return new Result(200, "{\"token\":\"" + token + "\"}");
        }
        if (!authorized(auth)) return new Result(401, "{\"error\":\"请先验证密码\"}");

        SettingsStore st = new SettingsStore(c);
        if ("/api/status".equals(path) && "GET".equals(method)) {
            StringBuilder s = new StringBuilder();
            s.append("{\"guard\":").append(QuizOverlayService.isRunning());
            s.append(",\"grade\":").append(st.grade());
            s.append(",\"interval\":").append(st.intervalSec());
            s.append(",\"questionsPerRound\":").append(st.questionsPerRound());
            s.append(",\"passPercent\":").append(st.passPercent());
            s.append(",\"roundMode\":").append(st.roundMode());
            s.append(",\"wrong\":").append(WrongBook.cachedCount());
            s.append(",\"version\":\"1.2.7\"}");
            return new Result(200, s.toString());
        }
        if ("/api/config".equals(path) && "POST".equals(method)) {
            int n = jsonInt(body, "questionsPerRound");
            int p = jsonInt(body, "passPercent");
            int v = jsonInt(body, "interval");
            if (n > 0) st.questionsPerRound(n);
            if (p > 0) st.passPercent(p);
            if (v > 0) st.intervalSec(v);
            try {
                Intent i = new Intent(c, QuizOverlayService.class)
                        .setAction(QuizOverlayService.ACTION_RELOAD);
                c.startService(i);
            } catch (Exception ignored) { }
            return new Result(200, "{\"ok\":true}");
        }
        return new Result(404, "{\"error\":\"unknown api\"}");
    }

    private static String jsonString(String body, String key) {
        if (body == null) return null;
        int i = body.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int c = body.indexOf(':', i);
        int q1 = body.indexOf('"', c + 1);
        if (q1 < 0) return null;
        int q2 = body.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return body.substring(q1 + 1, q2);
    }

    private static int jsonInt(String body, String key) {
        String v = jsonString(body, key);
        if (v == null) {
            if (body == null) return -1;
            int i = body.indexOf("\"" + key + "\"");
            if (i < 0) return -1;
            int c = body.indexOf(':', i);
            if (c < 0) return -1;
            StringBuilder b = new StringBuilder();
            for (int k = c + 1; k < body.length(); k++) {
                char ch = body.charAt(k);
                if (ch >= '0' && ch <= '9') b.append(ch);
                else if (b.length() > 0) break;
            }
            try { return Integer.parseInt(b.toString()); } catch (Exception e) { return -1; }
        }
        try { return Integer.parseInt(v); } catch (Exception e) { return -1; }
    }

    public static final class Result {
        public final int code;
        public final String json;
        Result(int code, String json) { this.code = code; this.json = json; }
    }
}
