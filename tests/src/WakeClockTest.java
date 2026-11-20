import com.jager.tvquiz.WakeClock;

/** 浅待机/伪关机下的出题时钟：纯 JVM 回归。 */
public class WakeClockTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    public static void main(String[] args) {
        ok("W01 15 秒后的 deadline", WakeClock.deadline(1000L, 15) == 16000L);
        ok("W02 负秒数按 0 处理", WakeClock.deadline(1000L, -3) == 1000L);
        ok("W03 剩余秒数向上取整", WakeClock.remainingSec(1000L, 16000L) == 15);
        ok("W04 无 deadline 时剩余 0", WakeClock.remainingSec(1000L, 0L) == 0);
        ok("W05 未到点", !WakeClock.due(15999L, 16000L));
        ok("W06 到点", WakeClock.due(16000L, 16000L));
        ok("W07 deadline 为 0 不误判到点", !WakeClock.due(99999L, 0L));

        // 浅待机：CPU 被冻结 8 小时，elapsedRealtime 照常推进
        ok("W08 浅待机跨越 deadline 后返回 0（立即补弹）",
                WakeClock.restoreRemaining(16000L, 1000000L, 1000L,
                        1000000L + 8 * 3600 * 1000L, 1000L + 8 * 3600 * 1000L,
                        600) == 0);
        ok("W09 尚未到点时按真实剩余恢复",
                WakeClock.restoreRemaining(16000L, 1000000L, 1000L,
                        1000000L, 5000L, 600) == 11);
        ok("W10 恢复结果不超过默认间隔",
                WakeClock.restoreRemaining(999999999L, 1000000L, 1000L,
                        1000000L, 1000L, 600) == 600);
        ok("W11 首次开启（无存档）用正常间隔",
                WakeClock.restoreRemaining(0L, 0L, 0L, 1000000L, 1000L, 600) == 600);
        ok("W12 冷开机 elapsed 归零按新会话处理",
                WakeClock.restoreRemaining(16000L, 1000000L, 1000L,
                        1000000L, 20L, 600) == 600);
        ok("W13 墙钟回拨按新会话处理",
                WakeClock.restoreRemaining(16000L, 1000000L, 1000L,
                        900000L, 5000L, 600) == 600);
        ok("W14 已过期但间隔为 0 时不返回负数",
                WakeClock.restoreRemaining(16000L, 1000000L, 1000L,
                        1000000L, 20000L, 0) == 0);

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
