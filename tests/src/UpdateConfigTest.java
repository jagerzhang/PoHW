import com.jager.tvquiz.UpdateConfig;

/** v1.2.5：在线版本更新的纯逻辑（无 Android 依赖）。 */
public class UpdateConfigTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    static final String SHA =
            "24a31d9ccdebceab7f9cd01424cf832810c7b1b8c16011847cc21e6605f22d29";

    static String json(String versioned, String sha) {
        return "{\"schema\":1,\"main\":{"
                + "\"versionName\":\"1.2.5\",\"versionCode\":39,\"minSdk\":21,"
                + "\"latest\":\"https://pohw.zhangge.net/dl/latest-main.apk\","
                + "\"versioned\":\"" + versioned + "\","
                + "\"sha256\":\"" + sha + "\",\"size\":1025024,"
                + "\"notes\":\"ota\"}}";
    }

    public static void main(String[] args) {
        long day = UpdateConfig.CHECK_INTERVAL_MS;
        ok("U01 默认开启且首次检查", UpdateConfig.shouldCheck(true, 100000L, 0L));
        ok("U02 关闭时不检查", !UpdateConfig.shouldCheck(false, 100000L, 0L));
        ok("U03 24 小时内不重复", !UpdateConfig.shouldCheck(true, day - 1L, 1L));
        ok("U04 满 24 小时检查", UpdateConfig.shouldCheck(true, day + 1L, 1L));
        ok("U05 系统时间倒退重新检查", UpdateConfig.shouldCheck(true, 10L, 1000L));
        ok("U06 新版本才提示", UpdateConfig.hasUpdate(37, 39, 0));
        ok("U07 同版本不提示", !UpdateConfig.hasUpdate(39, 39, 0));
        ok("U08 旧版本不提示", !UpdateConfig.hasUpdate(40, 39, 0));
        ok("U09 跳过版本不提示", !UpdateConfig.hasUpdate(37, 39, 39));
        ok("U10 合法 sha256", UpdateConfig.validSha256(SHA));
        ok("U11 长度不足被拒", !UpdateConfig.validSha256("abcd"));
        ok("U12 非十六进制被拒",
                !UpdateConfig.validSha256(SHA.substring(0, 63) + "z"));
        ok("U13 站点 URL 放行",
                UpdateConfig.safeUrl("https://pohw.zhangge.net/dl/PoHW-v1.2.5.apk"));
        ok("U14 外部主机被拒", !UpdateConfig.safeUrl("https://evil.example/dl/x.apk"));
        ok("U15 明文 HTTP 被拒",
                !UpdateConfig.safeUrl("http://pohw.zhangge.net/dl/x.apk"));
        ok("U16 路径穿越被拒",
                !UpdateConfig.safeUrl("https://pohw.zhangge.net/dl/../x.apk"));
        ok("U17 非 apk 被拒",
                !UpdateConfig.safeUrl("https://pohw.zhangge.net/dl/x.zip"));

        String good = json("https://pohw.zhangge.net/dl/PoHW-v1.2.5.apk", SHA);
        UpdateConfig.Info i = UpdateConfig.parse(good, UpdateConfig.CHANNEL_MAIN);
        ok("U18 清单解析成功", i != null);
        ok("U19 版本号字段", i != null && i.versionCode == 39
                && "1.2.5".equals(i.versionName) && i.minSdk == 21);
        ok("U20 优先带版本号 URL",
                i != null && i.downloadUrl().endsWith("PoHW-v1.2.5.apk"));
        ok("U21 带版本号非法时回退 latest",
                UpdateConfig.parse(json("bad", SHA), "main").downloadUrl()
                        .endsWith("latest-main.apk"));
        ok("U22 缺通道返回 null",
                UpdateConfig.parse(good, UpdateConfig.CHANNEL_LEGACY) == null);
        ok("U23 坏 JSON 返回 null", UpdateConfig.parse("{oops", "main") == null);
        ok("U24 非法 sha 返回 null",
                UpdateConfig.parse(json("https://pohw.zhangge.net/dl/x.apk", "xyz"),
                        "main") == null);
        ok("U25 两个 URL 都非法返回 null",
                UpdateConfig.parse(json("bad", SHA).replace(
                        "https://pohw.zhangge.net/dl/latest-main.apk", "bad"),
                        "main") == null);
        ok("U26 空输入返回 null", UpdateConfig.parse(null, "main") == null);

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL")
                + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
