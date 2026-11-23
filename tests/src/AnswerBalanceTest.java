import com.jager.tvquiz.AnswerBalance;

/** 答案位置均衡：纯 JVM 回归。 */
public class AnswerBalanceTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    static boolean isPermutation(int[] a) {
        boolean[] seen = new boolean[4];
        for (int v : a) {
            if (v < 0 || v > 3 || seen[v]) return false;
            seen[v] = true;
        }
        return true;
    }

    public static void main(String[] args) {
        String[] opts = {"甲", "乙", "丙", "丁"};
        long s1 = AnswerBalance.seed("1+1=?", opts);
        long s2 = AnswerBalance.seed("1+1=?", opts);
        long s3 = AnswerBalance.seed("1+2=?", opts);
        ok("B01 同一题种子稳定", s1 == s2);
        ok("B02 不同题种子不同", s1 != s3);
        int[] order = AnswerBalance.shuffleOrder(s1);
        ok("B03 洗牌结果是 0..3 的排列", isPermutation(order));
        ok("B04 同种子洗牌可复现",
                java.util.Arrays.equals(order, AnswerBalance.shuffleOrder(s1)));
        ok("B05 答案位置随洗牌正确迁移",
                AnswerBalance.movedIndex(new int[]{2, 0, 3, 1}, 0) == 1);
        ok("B06 找不到时返回原位置",
                AnswerBalance.movedIndex(new int[]{0, 1, 2, 3}, 9) == 9);

        // 1000 道「答案全在 A」的伪题库，确定性洗牌后应回到约 25%
        int[] hist = new int[4];
        for (int i = 0; i < 1000; i++) {
            String t = "题号" + i;
            int[] o = AnswerBalance.shuffleOrder(AnswerBalance.seed(t, opts));
            hist[AnswerBalance.movedIndex(o, 0)]++;
        }
        boolean uniform = true;
        for (int v : hist) if (v < 180 || v > 320) uniform = false;
        ok("B07 全 A 题库洗牌后四位置近似均匀 " + java.util.Arrays.toString(hist), uniform);

        int[] empty = {-1, -1, -1, -1, -1};
        ok("B08 空记录不重抽", !AnswerBalance.shouldRedraw(empty, 0));
        ok("B09 末尾连续两次同位置则重抽",
                AnswerBalance.shouldRedraw(new int[]{-1, -1, 1, 0, 0}, 0));
        ok("B10 末尾位置不同不重抽",
                !AnswerBalance.shouldRedraw(new int[]{-1, -1, 0, 2, 0}, 0));
        ok("B11 一组内同位置三次则重抽",
                AnswerBalance.shouldRedraw(new int[]{0, 1, 0, 2, 0}, 0));
        ok("B12 其它位置不受影响",
                !AnswerBalance.shouldRedraw(new int[]{0, 1, 0, 2, 0}, 3));
        ok("B13 null 记录不崩", !AnswerBalance.shouldRedraw(null, 0));

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
