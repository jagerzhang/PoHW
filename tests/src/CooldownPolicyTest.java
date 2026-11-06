import com.jager.tvquiz.CooldownPolicy;

/** v1.1.0：答错递增冷却、截止时间戳与时钟回拨检测（纯计算）。 */
public class CooldownPolicyTest {

    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    public static void main(String[] args) {
        ok("C01 0 次答错无冷却", CooldownPolicy.secondsFor(0) == 0);
        ok("C02 第 1 次 30 秒", CooldownPolicy.secondsFor(1) == 30);
        ok("C03 第 2 次 60 秒", CooldownPolicy.secondsFor(2) == 60);
        ok("C04 第 3 次 120 秒", CooldownPolicy.secondsFor(3) == 120);
        ok("C05 第 4 次 300 秒", CooldownPolicy.secondsFor(4) == 300);
        ok("C06 超过档位封顶 300 秒", CooldownPolicy.secondsFor(99) == 300);

        CooldownPolicy p = new CooldownPolicy();
        long now = 1000000L;
        long d1 = p.onWrong(now);
        ok("C07 首次答错 deadline = now + 30s", d1 == now + 30000L);
        ok("C08 累计次数为 1", p.wrongCount() == 1);
        ok("C09 剩余 30 秒", p.remainingSec(now) == 30);
        ok("C10 到期后剩余 0", p.remainingSec(d1) == 0);
        ok("C11 isActive 以 deadline 为准", p.isActive(now + 1000L) && !p.isActive(d1));
        long d2 = p.onWrong(now);
        ok("C12 第二次答错 60 秒", d2 == now + 60000L && p.wrongCount() == 2);
        p.reset();
        ok("C13 reset 清空次数与 deadline", p.wrongCount() == 0 && p.deadline() == 0L);

        ok("C14 正常恢复剩余 30 秒",
                CooldownPolicy.safeRemaining(now + 30000L, now, now, 100L, 200L, 1) == 30);
        ok("C15 已到期返回 0",
                CooldownPolicy.safeRemaining(now - 1L, now, now, 100L, 200L, 1) == 0);
        ok("C16 空状态返回 0",
                CooldownPolicy.safeRemaining(0L, now, now, 100L, 200L, 1) == 0);
        ok("C17 墙钟回拨按档位重计",
                CooldownPolicy.safeRemaining(now + 999999L, now, now + 5000L, 100L, 200L, 2) == 60);
        ok("C18 重启（elapsed 归零）按档位重计",
                CooldownPolicy.safeRemaining(now + 999999L, now, now, 1000L, 10L, 3) == 120);
        ok("C19 剩余不超过当前档位上限",
                CooldownPolicy.safeRemaining(now + 999999L, now, now, 100L, 200L, 1) == 30);

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
    }
}
