import com.jager.tvquiz.QuizTextSizer;

/** v1.3.0 起：选项长文本、题干结构与 TV 编号序列题的抽题适配。 */
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
        ok("O02 中长选项 16sp",
                QuizTextSizer.optionSp(new String[]{longText(25), "B", "C", "D"}) == 16);
        ok("O03 长选项 16sp",
                QuizTextSizer.optionSp(new String[]{longText(40), "B", "C", "D"}) == 16);
        ok("O04 超长选项 14sp",
                QuizTextSizer.optionSp(new String[]{longText(50), "B", "C", "D"}) == 14);
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

        ok("O13 71 字极端选项降到 14sp",
                QuizTextSizer.optionSp(new String[]{longText(71), "B", "C", "D"}) == 14);
        ok("O14 极端选项 4 行 14sp 放得下",
                QuizTextSizer.optionFits(200, 8, 8,
                        QuizTextSizer.optionSp(new String[]{longText(71)}), 4));
        ok("O15 24sp 四行放不下",
                !QuizTextSizer.optionFits(200, 8, 8, 24, 4));

        ok("O16 48 字选项适合 TV",
                QuizTextSizer.optionsFitTv(new String[]{longText(48), "B", "C", "D"}));
        ok("O17 82 字选项被过滤",
                !QuizTextSizer.optionsFitTv(new String[]{longText(82), "B", "C", "D"}));
        ok("O18 null 选项不崩", QuizTextSizer.optionsFitTv(null));

        ok("O19 35 字题 16sp 三行放得下",
                QuizTextSizer.optionFits(200, 8, 8,
                        QuizTextSizer.optionSp(new String[]{longText(35)}), 3));

        ok("Q01 短题干可上 TV", QuizTextSizer.questionFitTv("1+1=?"));
        ok("Q02 超长题干被挡", !QuizTextSizer.questionFitTv(longText(120)));
        ok("Q03 依赖课文原文被挡", !QuizTextSizer.questionFitTv("本文结尾的作用是什么"));
        ok("Q04 多子项编号被挡",
                !QuizTextSizer.questionFitTv("1. 甲( ) 2. 乙( ) 3. 丙( )"));
        ok("Q05 null 题干不崩", QuizTextSizer.questionFitTv(null));

        ok("Q06 编号在前汉字在后的序列题被挡",
                !QuizTextSizer.questionFitTv(
                        "下列的汉字按音序排列正确的一项是 (1)接 (2)天 (3)莲 (4)叶 (5)无"));
        ok("Q07 两个编号不算序列题",
                QuizTextSizer.questionFitTv("比较 (1) 和 (2) 的大小"));
        ok("Q08 选项含编号序列被挡",
                !QuizTextSizer.optionsFitTv(new String[]{
                        "（7）（1）（3）（6）（2）（5）（4）", "B", "C", "D"}));
        ok("Q09 普通整点角题不受影响",
                QuizTextSizer.questionFitTv("3时整，时针和分针成直角吗？")
                && QuizTextSizer.optionsFitTv(new String[]{"直角", "锐角", "钝角", "平角"}));
        ok("Q10 含 null 选项不崩",
                QuizTextSizer.optionsFitTv(new String[]{null, "B", "C", "D"}));

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL")
                + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
