import android.content.Context;
import com.jager.tvquiz.Json;
import com.jager.tvquiz.SettingsStore;
import com.jager.tvquiz.Syllabus;
import com.jager.tvquiz.Subjects;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 题库已打平（无教材版本 / 学期 / 单元）：本单测验证
 * 1) shipped 的 syllabus.json 是停用空表，运行时不会产生任何进度谓词；
 * 2) 保留的兼容类本身解析严格、SQL 片段占位符与参数仍一一对应（用内联最小表测）；
 * 3) SettingsStore 的旧键默认值不因覆盖安装改变抽题范围。
 */
public class SyllabusTest {

    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    static boolean has(String[] arr, String v) {
        if (arr == null) return false;
        for (String a : arr) if (v.equals(a)) return true;
        return false;
    }

    static boolean throwsOn(String json) {
        try { Syllabus.fromJson(json); return false; }
        catch (IllegalArgumentException e) { return true; }
    }

    static boolean throwsRaw(String json) {
        try { Json.parse(json); return false; }
        catch (IllegalArgumentException e) { return true; }
    }

    /** 只用于验证兼容类，不随 APK 发布、不参与抽题。 */
    static final String MINI =
            "{\"grades\":{\"3\":{\"1\":{\"数学\":["
            + "{\"name\":\"U1\",\"match\":[\"时分秒\",\"时、分、秒\"]},"
            + "{\"name\":\"U2\",\"match\":[\"万以内的加法和减法(一)\"]}"
            + "]}}}}";

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String shipped = new String(
                Files.readAllBytes(Paths.get("app/assets/syllabus.json")),
                StandardCharsets.UTF_8);
        Syllabus s = Syllabus.fromJson(shipped);

        // ---- 1. shipped 停用空表 ----
        ok("S01 shipped 进度表为停用空表", s.bookCount() == 0);
        ok("S02 空表不产生任何单元别名",
                s.matchNames(3, 1, Subjects.MATH, 99).length == 0);
        ok("S03 空表 maxUnits 为 0", s.maxUnits(3, 1) == 0);
        ok("S04 空表 unitLabel 为空", s.unitLabel(3, 1, Subjects.MATH, 2).isEmpty());
        ok("S05 全学年不加 term 谓词",
                "".equals(Syllabus.termWhere(Syllabus.TERM_ALL)));
        ok("S06 空别名集合不产生单元谓词",
                "".equals(Syllabus.unitWhere(new String[0]))
                        && Syllabus.unitArgs(new String[0]).length == 0);
        ok("S07 empty() 与停用表等价",
                Syllabus.empty().bookCount() == 0
                        && Syllabus.empty().maxUnits(3, 1) == 0);

        // ---- 2. 兼容类纯逻辑（内联最小表） ----
        Syllabus m = Syllabus.fromJson(MINI);
        ok("S08 内联进度表可解析", m.bookCount() == 1
                && m.unitCount(3, 1, Subjects.MATH) == 2);
        ok("S09 进度 0 不产生别名",
                m.matchNames(3, 1, Subjects.MATH, 0).length == 0);
        String[] u1 = m.matchNames(3, 1, Subjects.MATH, 1);
        ok("S10 已学第 1 单元只展开该单元别名",
                u1.length == 2 && has(u1, "时分秒"));
        ok("S11 序号超过册内单元数截到整册",
                m.matchNames(3, 1, Subjects.MATH, 99).length == 3);
        ok("S12 语文 / 英语无进度表",
                m.matchNames(3, 1, Subjects.CHINESE, 2).length == 0
                        && m.matchNames(3, 1, Subjects.ENGLISH, 2).length == 0);
        ok("S13 unitLabel 给出可读的第 N 单元名",
                m.unitLabel(3, 1, Subjects.MATH, 2).startsWith("第 2 单元"));
        String[] two = {"时分秒", "测量"};
        String w2 = Syllabus.unitWhere(two);
        String[] a2 = Syllabus.unitArgs(two);
        ok("S14 占位符数与参数数严格一致",
                count(w2, '?') == 4 && a2.length == 4);
        ok("S15 参数顺序与中文冒号完整",
                "%：时分秒".equals(a2[0]) && "时分秒".equals(a2[1])
                        && "%：测量".equals(a2[2]) && "测量".equals(a2[3]));
        ok("S16 上册 / 下册谓词仍可用（兼容重载）",
                " AND term IN ('上','全')".equals(Syllabus.termWhere(Syllabus.TERM_FIRST))
                        && " AND term IN ('下','全')".equals(Syllabus.termWhere(Syllabus.TERM_SECOND)));
        ok("S17 非法学期索引退化为全学年",
                "".equals(Syllabus.termWhere(99)));

        // ---- 3. 严格 JSON 解析 ----
        ok("S18 Json 支持对象 / 数组 / 转义 / 数字",
                ((Map<String, Object>) Json.parse("{\"a\":[1,\"x\\u4e2d\",true,null]}"))
                        .get("a") instanceof List);
        ok("S19 Json 拒绝尾随内容", throwsRaw("{} {}"));
        ok("S20 Json 拒绝缺逗号", throwsRaw("{\"a\":1 \"b\":2}"));
        ok("S21 拒绝同一册重复别名",
                throwsOn("{\"grades\":{\"3\":{\"1\":{\"数学\":["
                        + "{\"name\":\"A\",\"match\":[\"x\",\"x\"]}]}}}}"));
        ok("S22 拒绝含 SQL 通配符的别名",
                throwsOn("{\"grades\":{\"3\":{\"1\":{\"数学\":["
                        + "{\"name\":\"A\",\"match\":[\"x%y\"]}]}}}}"));
        ok("S23 拒绝非法学期序号",
                throwsOn("{\"grades\":{\"3\":{\"9\":{\"数学\":[]}}}}"));
        ok("S24 拒绝缺失 grades", throwsOn("{\"version\":\"1\"}"));
        ok("S25 TERM_LABELS 三项且顺序正确",
                Syllabus.TERM_LABELS.length == 3
                        && "全学年".equals(Syllabus.TERM_LABELS[0]));

        // ---- 4. 旧设置键默认值（覆盖安装不改变抽题范围） ----
        SettingsStore st = new SettingsStore(new Context());
        ok("S26 学期默认全学年", st.termIndex() == Syllabus.TERM_ALL);
        ok("S27 单元默认不限", st.unitLimit() == 0);
        st.termIndex(99);
        ok("S28 学期上限收敛", st.termIndex() == Syllabus.TERM_SECOND);
        st.termIndex(-7);
        ok("S29 学期下限收敛", st.termIndex() == Syllabus.TERM_ALL);
        st.unitLimit(999);
        ok("S30 单元上限收敛", st.unitLimit() == Syllabus.MAX_UNITS);
        st.unitLimit(-1);
        ok("S31 单元下限收敛", st.unitLimit() == 0);

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
    }
}
