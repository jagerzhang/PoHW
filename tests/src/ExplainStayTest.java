import android.content.Context;
import com.jager.tvquiz.SettingsStore;

/** 答案解析停留档位：纯 JVM 回归（复用 SharedPreferences stub）。 */
public class ExplainStayTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    public static void main(String[] args) {
        Context.resetAll();
        SettingsStore st = new SettingsStore(new Context());
        ok("X01 默认停留 5 秒", st.explainStayIndex() == 2 && st.explainStaySec() == 5);
        ok("X02 六档齐全", SettingsStore.EXPLAIN_STAY_SEC.length == 6
                && SettingsStore.EXPLAIN_STAY_LABELS.length == 6);
        ok("X03 立即继续为 0 秒", SettingsStore.EXPLAIN_STAY_SEC[0] == 0);
        st.explainStayIndex(5);
        ok("X04 按任意键档位为 -1", st.explainStaySec() == -1
                && "按任意键继续".equals(st.explainStayLabel()));
        st.explainStayIndex(99);
        ok("X05 上界 clamp", st.explainStayIndex() == 5);
        st.explainStayIndex(-7);
        ok("X06 下界 clamp", st.explainStayIndex() == 0);
        st.explainStayIndex(3);
        ok("X07 8 秒档位", st.explainStaySec() == 8);
        Context.restartProcess();
        ok("X08 档位持久化", new SettingsStore(new Context()).explainStayIndex() == 3);
        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
