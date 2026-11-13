import com.jager.tvquiz.TextFit;
import com.jager.tvquiz.SpeakFocus;
import com.jager.tvquiz.KeyRouter;

/** 运行时字号自适应决策与朗读按钮遥控器焦点：纯 JVM 回归。 */
public class TextFitTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    public static void main(String[] args) {
        int[] ladder   = {24, 22, 20, 18, 16, 14, 12};
        int[] lines    = { 4,  3,  3,  2,  2,  2,  2};
        int[] heights  = {92, 80, 69, 61, 55, 49, 42};

        ok("F01 68dp/3 行时降到 18sp（20sp 需 69dp 放不下）",
                TextFit.choose(ladder, lines, heights, 68, 3) == 18);
        ok("F02 100dp/2 行时行数约束生效",
                TextFit.choose(ladder, lines, heights, 100, 2) == 18);
        ok("F03 100dp/4 行时保持 24sp",
                TextFit.choose(ladder, lines, heights, 100, 4) == 24);
        ok("F04 全放不下时回退最小档",
                TextFit.choose(ladder, lines, heights, 10, 1) == 12);
        ok("F05 可用高度非正时回退最小档",
                TextFit.choose(ladder, lines, heights, 0, 3) == 12);
        ok("F06 空候选不崩", TextFit.choose(new int[0], null, null, 68, 3) == 0);
        ok("F07 测量数组缺失时保守取最小档",
                TextFit.choose(ladder, null, heights, 68, 3) == 12);
        ok("F08 行数约束不可省（24sp 四行必须被跳过）",
                TextFit.choose(ladder, lines, heights, 200, 3) == 22);
        ok("F09 高度约束不可省（22sp 80dp 必须被跳过）",
                TextFit.choose(ladder, lines, heights, 79, 4) == 20);

        ok("S01 A 选项按上键进入朗读按钮",
                SpeakFocus.shouldEnter(false, SpeakFocus.DIR_UP, 0));
        ok("S02 B 选项按上键进入朗读按钮",
                SpeakFocus.shouldEnter(false, SpeakFocus.DIR_UP, 1));
        ok("S03 C/D 选项按上键不进入",
                !SpeakFocus.shouldEnter(false, SpeakFocus.DIR_UP, 2)
                        && !SpeakFocus.shouldEnter(false, SpeakFocus.DIR_UP, 3));
        ok("S04 左右键不进入朗读按钮",
                !SpeakFocus.shouldEnter(false, SpeakFocus.DIR_LEFT, 0)
                        && !SpeakFocus.shouldEnter(false, SpeakFocus.DIR_RIGHT, 0));
        ok("S05 已聚焦时不会重复进入",
                !SpeakFocus.shouldEnter(true, SpeakFocus.DIR_UP, 0));
        ok("S06 朗读按钮按下键回到选项",
                SpeakFocus.shouldLeave(true, SpeakFocus.DIR_DOWN));
        ok("S07 未聚焦时不消费下键",
                !SpeakFocus.shouldLeave(false, SpeakFocus.DIR_DOWN));
        ok("S08 朗读按钮上键保持聚焦",
                !SpeakFocus.shouldLeave(true, SpeakFocus.DIR_UP));

        ok("K01 设置键语义映射到完整朗读",
                KeyRouter.decide(KeyRouter.ACTION_DOWN, 0,
                        KeyRouter.SEM_SPEAK_ALL, 0).action
                        == KeyRouter.ACT_SPEAK_ALL);
        ok("K02 设置键长按重复被过滤",
                KeyRouter.decide(KeyRouter.ACTION_DOWN, 3,
                        KeyRouter.SEM_SPEAK_ALL, 0).action
                        == KeyRouter.ACT_CONSUME);
        ok("K03 播放键仍只读当前高亮项",
                KeyRouter.decide(KeyRouter.ACTION_DOWN, 0,
                        KeyRouter.SEM_SPEAK, 0).action
                        == KeyRouter.ACT_SPEAK);

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
