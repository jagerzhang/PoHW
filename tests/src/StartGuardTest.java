import com.jager.tvquiz.GuardPolicy;
import com.jager.tvquiz.LaunchPolicy;

/** v1.3.1：首次开启守护必须先设置家长密码，且关闭路径不受影响。 */
public class StartGuardTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    public static void main(String[] args) {
        ok("S01 无密码不允许开启守护", !GuardPolicy.canStartGuard(false));
        ok("S02 已设密码才允许开启守护", GuardPolicy.canStartGuard(true));
        ok("S03 有密码时关闭守护仍需验证", GuardPolicy.requiresPinToStop(true));
        ok("S04 无密码时关闭守护不额外拦截", !GuardPolicy.requiresPinToStop(false));
        ok("S05 服务侧拒绝未验证的关闭", !GuardPolicy.allowStop(true, false));
        ok("S06 服务侧接受已验证的关闭", GuardPolicy.allowStop(true, true));
        ok("S07 冷启动自动守护判定不变",
                LaunchPolicy.shouldAutoGuard(true, true, true, false));
        ok("S08 已运行不重复拉起", !LaunchPolicy.shouldAutoGuard(true, true, true, true));
        ok("S09 未授权悬浮窗不自动拉起", !LaunchPolicy.shouldAutoGuard(true, true, false, false));
        ok("S10 家长关掉自动守护后不拉起", !LaunchPolicy.shouldAutoGuard(true, false, true, false));

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
