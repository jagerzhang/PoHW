import com.jager.tvquiz.DifficultyPolicy;

/** code24：难度倾向加权，默认不限时行为与旧版一致。 */
public class DifficultyPolicyTest {
    static int pass = 0, fail = 0;
    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }
    public static void main(String[] args) {
        ok("D01 合法档位保留", DifficultyPolicy.clamp(2) == 2);
        ok("D02 越界回落到不限", DifficultyPolicy.clamp(9) == 0 && DifficultyPolicy.clamp(-1) == 0);
        ok("D03 默认档为不限", DifficultyPolicy.LEVEL_ALL == 0);
        ok("D04 不限时永不启用过滤",
           !DifficultyPolicy.useLevel(0, 0) && !DifficultyPolicy.useLevel(0, 79));
        ok("D05 低档 roll<80 命中", DifficultyPolicy.useLevel(1, 79));
        ok("D06 roll>=80 走全库", !DifficultyPolicy.useLevel(1, 80));
        ok("D07 roll=99 走全库", !DifficultyPolicy.useLevel(3, 99));
        ok("D08 负 roll 不启用", !DifficultyPolicy.useLevel(2, -1));
        ok("D09 不限时谓词为空", DifficultyPolicy.predicate(0).isEmpty());
        ok("D10 低档谓词带条件", DifficultyPolicy.predicate(1).equals(" AND difficulty_level=1"));
        ok("D11 高档谓词带条件", DifficultyPolicy.predicate(3).equals(" AND difficulty_level=3"));
        ok("D12 无条件兜底谓词合法",
           DifficultyPolicy.rootPredicate(2).equals("WHERE difficulty_level=2"));
        ok("D13 越界档位回落为空谓词", DifficultyPolicy.predicate(9).isEmpty());
        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
    }
}
