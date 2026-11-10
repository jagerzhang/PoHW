import com.jager.tvquiz.OverlayChannel;
import com.jager.tvquiz.OverlayPermissionTracker;

/** v1.3.3：弹题通道决策与权限撤销检测。 */
public class OverlayChannelTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    public static void main(String[] args) {
        ok("C01 有悬浮窗权限优先走应用浮层",
                OverlayChannel.pick(true, false, 33) == OverlayChannel.APPLICATION);
        ok("C02 两者都有仍优先应用浮层",
                OverlayChannel.pick(true, true, 33) == OverlayChannel.APPLICATION);
        ok("C03 无悬浮窗但有无障碍走降级通道",
                OverlayChannel.pick(false, true, 33) == OverlayChannel.ACCESSIBILITY);
        ok("C04 API21 不支持无障碍窗口类型",
                OverlayChannel.pick(false, true, 21) == OverlayChannel.NONE);
        ok("C05 API22 起支持降级",
                OverlayChannel.pick(false, true, 22) == OverlayChannel.ACCESSIBILITY);
        ok("C06 两者皆无则无通道",
                OverlayChannel.pick(false, false, 33) == OverlayChannel.NONE);
        ok("C07 available 只认两个有效通道",
                OverlayChannel.available(OverlayChannel.APPLICATION)
                && OverlayChannel.available(OverlayChannel.ACCESSIBILITY)
                && !OverlayChannel.available(OverlayChannel.NONE));

        ok("P01 已授权变未授权判定为撤销",
                OverlayPermissionTracker.revokeDetected(true, false));
        ok("P02 首次安装未授权不误报",
                !OverlayPermissionTracker.revokeDetected(false, false));
        ok("P03 一直已授权不误报",
                !OverlayPermissionTracker.revokeDetected(true, true));
        ok("P04 重新授权不是撤销",
                !OverlayPermissionTracker.revokeDetected(false, true));
        ok("P05 恢复授权可触发自愈",
                OverlayPermissionTracker.restored(false, true));
        ok("P06 状态变化才落盘",
                OverlayPermissionTracker.shouldRecord(true, false)
                && OverlayPermissionTracker.shouldRecord(false, true)
                && !OverlayPermissionTracker.shouldRecord(true, true)
                && !OverlayPermissionTracker.shouldRecord(false, false));

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
