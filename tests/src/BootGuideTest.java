import com.jager.tvquiz.BootGuide;

/** 开机自启引导：按品牌给出可用路径，且降级说明如实。 */
public class BootGuideTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    public static void main(String[] args) {
        ok("B01 海信走启动管理",
                BootGuide.pathFor("Hisense").contains("启动管理")
                        && BootGuide.pathFor("VIDAA").contains("小聚助手"));
        ok("B02 小米走电视管家",
                BootGuide.pathFor("Xiaomi").contains("电视管家")
                        && BootGuide.pathFor("MiTV").contains("自启动"));
        ok("B03 TCL 走安全卫士",
                BootGuide.pathFor("TCL").contains("安全卫士"));
        ok("B04 创维走启动管理",
                BootGuide.pathFor("Skyworth").contains("启动管理"));
        ok("B05 大小写不敏感",
                BootGuide.pathFor("HISENSE").equals(BootGuide.pathFor("hisense")));
        ok("B06 未知品牌有通用路径",
                BootGuide.pathFor("SomeBox").contains("设置"));
        ok("B07 null 不崩且给通用路径",
                BootGuide.pathFor(null).contains("设置"));
        ok("B08 降级提示说明属正常",
                BootGuide.fallbackHint().contains("属正常")
                        && BootGuide.fallbackHint().contains("手动打开一次"));

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL")
                + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
