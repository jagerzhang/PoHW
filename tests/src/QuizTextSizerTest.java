import com.jager.tvquiz.QuizTextSizer;

/** v1.3.0：选项长文本不得被固定高度网格裁切。 */
public class QuizTextSizerTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    static String longText(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append('题');
        return b.toString();
    }

    public static void main(String[] args) {
        ok("T01 题干短文本 30sp", QuizTextSizer.sp("1+1=?") == 30);
        ok("T02 题干长文本降号", QuizTextSizer.sp(longText(80)) == 19);

        ok("O01 短选项保持 24sp",
                QuizTextSizer.optionSp(new String[]{"15", "16", "17", "18"}) == 24);
        ok("O02 中长选项 22sp",
                QuizTextSizer.optionSp(new String[]{longText(25), "B", "C", "D"}) == 22);
        ok("O03 长选项 20sp",
                QuizTextSizer.optionSp(new String[]{longText(36), "B", "C", "D"}) == 20);
        ok("O04 超长选项 18sp",
                QuizTextSizer.optionSp(new String[]{longText(50), "B", "C", "D"}) == 18);
        ok("O05 空选项不崩", QuizTextSizer.optionSp(null) == 24);
        ok("O06 含 null 不崩",
                QuizTextSizer.optionSp(new String[]{null, "B", null, "D"}) == 24);

        int h = QuizTextSizer.optionTextHeightDp(200, 8, 8);
        ok("O07 按钮文字区 68dp", h == 68);
        ok("O08 两行 24sp 放得下", QuizTextSizer.optionFits(200, 8, 8, 24, 2));
        ok("O09 三行 18sp 放得下", QuizTextSizer.optionFits(200, 8, 8, 18, 3));
        ok("O10 三行 24sp 放不下", !QuizTextSizer.optionFits(200, 8, 8, 24, 3));
        ok("O11 旧 186dp 网格不够三行", !QuizTextSizer.optionFits(186, 8, 8, 18, 3));
        ok("O12 超长选项在 200dp 内安全",
                QuizTextSizer.optionFits(200, 8, 8,
                        QuizTextSizer.optionSp(new String[]{longText(50)}), 3));

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL")
                + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
