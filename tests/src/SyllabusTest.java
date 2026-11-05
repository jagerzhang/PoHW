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

/** 方案 B：有序单元进度表、学期 / 单元 SQL 片段、严格 JSON 解析与设置默认值。 */
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
        try {
            Syllabus.fromJson(json);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String json = new String(
                Files.readAllBytes(Paths.get("app/assets/syllabus.json")),
                StandardCharsets.UTF_8);
        Syllabus s = Syllabus.fromJson(json);

        ok("S01 真实进度表可解析且含 12 册", s.bookCount() == 12);
        boolean allBooks = true;
        for (int g = 1; g <= 6; g++) {
            for (int t = 1; t <= 2; t++) {
                if (s.unitCount(g, t, Subjects.MATH) <= 0) allBooks = false;
            }
        }
        ok("S02 小学 1-6 年级上下册数学全覆盖", allBooks);
        ok("S03 三上数学 11 个有序单元", s.unitCount(3, 1, Subjects.MATH) == 11);
        List<Syllabus.Unit> b = s.book(3, 1, Subjects.MATH);
        ok("S04 三上第 1 单元是「时、分、秒」", "时、分、秒".equals(b.get(0).name));
        ok("S05 三上第 1 单元收录两个别名「时、分、秒」/「时分秒」",
                b.get(0).match.length == 2
                        && has(b.get(0).match, "时、分、秒")
                        && has(b.get(0).match, "时分秒"));
        ok("S06 三上第 2 单元收录「(一)」别名",
                has(b.get(1).match, "万以内的加法和减法(一)"));
        ok("S07 进度 0（不限）不产生任何别名",
                s.matchNames(3, 1, Subjects.MATH, 0).length == 0);
        String[] u1 = s.matchNames(3, 1, Subjects.MATH, 1);
        ok("S08 已学到第 1 单元只展开该单元别名",
                u1.length == 2 && has(u1, "时分秒"));
        String[] u2 = s.matchNames(3, 1, Subjects.MATH, 2);
        ok("S09 已学到第 2 单元展开前两单元共 4 个别名",
                u2.length == 4 && has(u2, "万以内的加法和减法(一)"));
        String[] uall = s.matchNames(3, 1, Subjects.MATH, 99);
        ok("S10 序号超过册内单元数时截到整册", uall.length == 13);
        ok("S11 语文 / 英语无进度表，自动退化为仅学期过滤",
                s.matchNames(3, 1, Subjects.CHINESE, 2).length == 0
                        && s.matchNames(3, 1, Subjects.ENGLISH, 2).length == 0);
        ok("S12 7-9 年级无进度表", s.matchNames(7, 1, Subjects.MATH, 3).length == 0);
        ok("S13 maxUnits 反映真实册大小",
                s.maxUnits(3, 1) == 11 && s.maxUnits(6, 2) == 7 && s.maxUnits(7, 1) == 0);
        ok("S14 unitLabel 给出家长可读的第 N 单元名",
                s.unitLabel(3, 1, Subjects.MATH, 2).startsWith("第 2 单元")
                        && s.unitLabel(3, 1, Subjects.MATH, 2).contains("万以内的加法和减法（一）"));
        ok("S15 不限进度时 unitLabel 为空", s.unitLabel(3, 1, Subjects.MATH, 0).isEmpty());
        ok("S16 全学年不加 term 谓词（兼容旧行为）",
                "".equals(Syllabus.termWhere(Syllabus.TERM_ALL)));
        ok("S17 上册含全一册、排除空学期综合练习",
                " AND term IN ('上','全')".equals(Syllabus.termWhere(Syllabus.TERM_FIRST)));
        ok("S18 下册含全一册、排除空学期综合练习",
                " AND term IN ('下','全')".equals(Syllabus.termWhere(Syllabus.TERM_SECOND)));
        ok("S19 非法学期索引退化为全学年", "".equals(Syllabus.termWhere(99)));
        ok("S20 空别名集合不产生单元谓词",
                "".equals(Syllabus.unitWhere(new String[0]))
                        && Syllabus.unitArgs(new String[0]).length == 0);
        String[] one = {"时分秒"};
        ok("S21 单个别名生成一组 LIKE/精确匹配占位符",
                "(topic LIKE ? OR topic = ?)".equals(
                        Syllabus.unitWhere(one).substring(" AND (".length(),
                                Syllabus.unitWhere(one).length() - 1)));
        String[] two = {"时分秒", "测量"};
        String w2 = Syllabus.unitWhere(two);
        String[] a2 = Syllabus.unitArgs(two);
        ok("S22 两别名占位符数与参数数严格一致",
                count(w2, '?') == 4 && a2.length == 4);
        ok("S23 参数顺序为「%：别名, 别名」且中文冒号完整",
                "%：时分秒".equals(a2[0]) && "时分秒".equals(a2[1])
                        && "%：测量".equals(a2[2]) && "测量".equals(a2[3]));
        boolean placeholdersOk = true;
        for (int g = 1; g <= 6; g++) {
            for (int t = 1; t <= 2; t++) {
                String[] names = s.matchNames(g, t, Subjects.MATH, 99);
                if (count(Syllabus.unitWhere(names), '?') != Syllabus.unitArgs(names).length) {
                    placeholdersOk = false;
                }
            }
        }
        ok("S24 全部 12 册的谓词占位符与参数一一对应", placeholdersOk);

        // ---- 严格 JSON 解析：损坏的进度表必须报错，不能静默退化 ----
        ok("S25 Json 支持对象 / 数组 / 转义 / 数字",
                ((Map<String, Object>) Json.parse("{\"a\":[1,\"x\\u4e2d\",true,null]}"))
                        .get("a") instanceof List);
        ok("S26 Json 拒绝尾随内容", throwsRaw("{} {}"));
        ok("S27 Json 拒绝缺逗号", throwsRaw("{\"a\":1 \"b\":2}"));
        ok("S28 进度表拒绝同一册重复别名",
                throwsOn("{\"grades\":{\"3\":{\"1\":{\"数学\":["
                        + "{\"name\":\"A\",\"match\":[\"x\",\"x\"]}]}}}}"));
        ok("S29 进度表拒绝含 SQL 通配符的别名",
                throwsOn("{\"grades\":{\"3\":{\"1\":{\"数学\":["
                        + "{\"name\":\"A\",\"match\":[\"x%y\"]}]}}}}"));
        ok("S30 进度表拒绝非法学期序号",
                throwsOn("{\"grades\":{\"3\":{\"9\":{\"数学\":[]}}}}"));
        ok("S31 进度表拒绝缺失 grades", throwsOn("{\"version\":\"1\"}"));
        ok("S32 empty() 安全降级：无册、无别名、maxUnits=0",
                Syllabus.empty().bookCount() == 0
                        && Syllabus.empty().matchNames(3, 1, Subjects.MATH, 2).length == 0
                        && Syllabus.empty().maxUnits(3, 1) == 0);
        ok("S33 TERM_LABELS 三项且顺序为全学年/上册/下册",
                Syllabus.TERM_LABELS.length == 3
                        && "全学年".equals(Syllabus.TERM_LABELS[0])
                        && "上册".equals(Syllabus.TERM_LABELS[1])
                        && "下册".equals(Syllabus.TERM_LABELS[2]));

        // ---- SharedPreferences 默认值：覆盖安装不得改变旧抽题范围 ----
        SettingsStore st = new SettingsStore(new Context());
        ok("S34 学期默认全学年（0）", st.termIndex() == Syllabus.TERM_ALL);
        ok("S35 单元默认不限（0）", st.unitLimit() == 0);
        st.termIndex(Syllabus.TERM_SECOND);
        ok("S36 学期可写并回读", st.termIndex() == Syllabus.TERM_SECOND);
        st.termIndex(99);
        ok("S37 学期上限收敛到下册", st.termIndex() == Syllabus.TERM_SECOND);
        st.termIndex(-7);
        ok("S38 学期下限收敛到全学年", st.termIndex() == Syllabus.TERM_ALL);
        st.unitLimit(3);
        ok("S39 单元序号可写并回读", st.unitLimit() == 3);
        st.unitLimit(999);
        ok("S40 单元序号上限收敛到 MAX_UNITS", st.unitLimit() == Syllabus.MAX_UNITS);
        st.unitLimit(-1);
        ok("S41 单元序号下限收敛到不限", st.unitLimit() == 0);
        st.termIndex(Syllabus.TERM_ALL);
        st.unitLimit(0);

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
    }

    static boolean throwsRaw(String json) {
        try {
            Json.parse(json);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
