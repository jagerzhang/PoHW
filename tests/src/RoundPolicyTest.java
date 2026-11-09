import com.jager.tvquiz.RoundPolicy;

/** v1.3.0：按组答题纯逻辑。 */
public class RoundPolicyTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    public static void main(String[] args) {
        RoundPolicy p = new RoundPolicy(5, 60);
        ok("R01 默认题量 5", p.target() == 5);
        ok("R02 默认正确率 60", p.passPercent() == 60);
        ok("R03 需答对 3 题", p.minCorrect() == 3);
        ok("R04 第 1 题答对继续", p.onAnswer(true) == RoundPolicy.CONTINUE);
        ok("R05 第 2 题答错继续", p.onAnswer(false) == RoundPolicy.CONTINUE);
        ok("R06 第 3 题答对继续", p.onAnswer(true) == RoundPolicy.CONTINUE);
        ok("R07 第 4 题答对仍继续", p.onAnswer(true) == RoundPolicy.CONTINUE);
        ok("R08 第 5 题答错但已达标 -> PASS", p.onAnswer(false) == RoundPolicy.PASS);
        ok("R09 结算计数正确", p.answered() == 5 && p.correct() == 3);
        ok("R10 通过文案", p.result(true).contains("本组通过"));

        RoundPolicy q = new RoundPolicy(5, 60);
        for (int i = 0; i < 4; i++) q.onAnswer(false);
        ok("R11 未达标 -> FAIL", q.onAnswer(false) == RoundPolicy.FAIL);
        ok("R12 未达标文案", q.result(false).contains("再来一组"));
        q.reset();
        ok("R13 reset 清空计数", q.answered() == 0 && q.correct() == 0);

        ok("R14 target 下界", RoundPolicy.clampTarget(0) == 1);
        ok("R15 target 上界", RoundPolicy.clampTarget(99) == 20);
        ok("R16 percent 下界", RoundPolicy.clampPercent(0) == 1);
        ok("R17 percent 上界", RoundPolicy.clampPercent(200) == 100);
        ok("R18 100% 需全对", new RoundPolicy(5, 100).minCorrect() == 5);
        ok("R19 1 题 60% 需 1 题", new RoundPolicy(1, 60).minCorrect() == 1);
        RoundPolicy r = new RoundPolicy(1, 60);
        ok("R20 单题答对立即 PASS", r.onAnswer(true) == RoundPolicy.PASS);
        ok("R21 进度文案含题号", p.progress().contains("题"));

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL")
                + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
