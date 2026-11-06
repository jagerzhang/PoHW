import com.jager.tvquiz.PracticeConfig;

/** v1.2.1：练习模式参数与统计（纯 Java，无 Android 依赖）。 */
public class PracticeConfigTest {
    static int pass = 0, fail = 0;
    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }
    public static void main(String[] args) {
        PracticeConfig c = new PracticeConfig();
        ok("P01 默认混合学科", c.subject() == PracticeConfig.MIXED);
        ok("P02 默认三年级", c.grade() == 3);
        ok("P03 默认难度不限", c.difficulty() == PracticeConfig.DIFF_ALL);
        ok("P04 初始统计为零", c.asked() == 0 && c.correct() == 0 && c.streak() == 0);
        ok("P05 混合首题数学", c.subjectForDraw() == PracticeConfig.MATH);
        c.onAsked();
        ok("P06 混合第二题语文", c.subjectForDraw() == PracticeConfig.CHINESE);
        c.onAsked();
        ok("P07 混合第三题英语", c.subjectForDraw() == PracticeConfig.ENGLISH);
        c.onAsked();
        ok("P08 混合第四题回到数学", c.subjectForDraw() == PracticeConfig.MATH);
        c.cycleSubject();
        ok("P09 学科循环回到数学", c.subject() == PracticeConfig.MATH);
        c.cycleSubject(); c.cycleSubject(); c.cycleSubject();
        ok("P10 学科四次循环回混合", c.subject() == PracticeConfig.MIXED);
        c.onCorrect();
        ok("P11 答对累计与连对", c.correct() == 1 && c.streak() == 1);
        c.onWrong();
        ok("P12 答错只清连对", c.streak() == 0 && c.correct() == 1);
        c.cycleGrade();
        ok("P13 年级递增", c.grade() == 4);
        c.cycleDifficulty();
        ok("P14 难度循环", c.difficulty() == 1);
        ok("P15 汇总含已练/正确/连对",
           c.summary().contains("已练") && c.summary().contains("正确") && c.summary().contains("连对"));
        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
