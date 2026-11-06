import com.jager.tvquiz.UnlockPolicy;

/** v1.1.0：连续答对解锁策略，默认 1 题即解锁（行为与旧版一致）。 */
public class UnlockPolicyTest {

    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    public static void main(String[] args) {
        ok("U01 clamp 下界为 1", UnlockPolicy.clamp(0) == 1);
        ok("U02 clamp 上界为 5", UnlockPolicy.clamp(99) == 5);
        ok("U03 clamp 保留合法值", UnlockPolicy.clamp(3) == 3);
        ok("U04 构造同样 clamp", new UnlockPolicy(-4).required() == 1);

        UnlockPolicy def = new UnlockPolicy(UnlockPolicy.DEFAULT_STREAK);
        ok("U05 默认门槛为 1", def.required() == 1 && UnlockPolicy.DEFAULT_STREAK == 1);
        ok("U06 默认首次答对即解锁", def.onCorrect());

        UnlockPolicy p = new UnlockPolicy(2);
        ok("U07 初始进度为 0", p.streak() == 0);
        ok("U08 连对第 1 题不解锁", !p.onCorrect());
        ok("U09 进度文案显示 1/2", p.progress().contains("1/2"));
        ok("U10 连对第 2 题解锁", p.onCorrect());
        p.reset();
        ok("U11 reset 后归零", p.streak() == 0);
        ok("U12 reset 后需重新连对", !p.onCorrect());

        UnlockPolicy q = new UnlockPolicy(3);
        q.onCorrect();
        q.onCorrect();
        q.onWrong();
        ok("U13 答错清零", q.streak() == 0);
        ok("U14 清零后不因旧进度提前解锁", !q.onCorrect() && !q.onCorrect());
        ok("U15 清零后仍需满 3 题", q.onCorrect());

        UnlockPolicy r = new UnlockPolicy(5);
        boolean unlocked = false;
        for (int i = 0; i < 5; i++) unlocked = r.onCorrect();
        ok("U16 门槛 5 需答满 5 题", unlocked && r.streak() == 5);

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
    }
}
