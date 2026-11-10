package com.jager.tvquiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学期 + 单元进度表（按学制与教材版本隔离）。
 *
 * <p>assets/syllabus.json 由 tools/quizbank/build_syllabus.py 从题库生成：
 * 顶层是「自动」视图（每格选题量最多的版本族），systems 下另有「六三制」
 * 「五四制」两套独立视图，各自有自己的有序单元表与代表族。
 * 家长选「上册 / 已学到第 N 单元 / 学制」后，本题负责生成附加到 QuizDb
 * 池谓词之后的 SQL 片段与参数；QuizDb 再按「单元 -> 学期 -> 年级」逐级放宽，
 * 始终保留学科约束。
 *
 * <p>本类是纯 Java，不引用任何 android.*：Json 解析器也是纯 Java，
 * 所以单元表能在 JVM 单测里用真实 assets/syllabus.json 直接跑。
 *
 * <p>诚实边界：题库有真实教材册次、单元与课标知识点，但**没有学制字段**；
 * 六三制 / 五四制按教材名归类（systemFamilies）。未覆盖的组合返回空名单，
 * 绝不假装精确到单元。
 */
public final class Syllabus {

    public static final int TERM_ALL = 0;
    public static final int TERM_FIRST = 1;
    public static final int TERM_SECOND = 2;
    public static final String[] TERM_LABELS = {"全学年", "上册", "下册"};
    /** 家长可选的最大单元序号；也是 SettingsStore.unitLimit() 的合法上界。 */
    public static final int MAX_UNITS = 24;

    /** 学制：0=自动（题量最多的版本族）、1=六三制、2=五四制。 */
    public static final int EDITION_AUTO = 0;
    public static final int EDITION_SIX_THREE = 1;
    public static final int EDITION_FIVE_FOUR = 2;
    public static final String[] EDITION_LABELS = {"自动（题量最多）", "六三制", "五四制"};
    public static final String SYSTEM_SIX_THREE = "六三制";
    public static final String SYSTEM_FIVE_FOUR = "五四制";

    public static final class Unit {
        public final String name;
        public final String[] match;

        Unit(String name, String[] match) {
            this.name = name;
            this.match = match;
        }

        public String label(int index) {
            return "第 " + index + " 单元 · " + name;
        }
    }

    /** index = editionMode；0 是顶层自动视图，1/2 是 systems 下的学制视图。 */
    private final List<Map<String, List<Unit>>> books;
    private final List<Map<String, String>> editionByCell;
    /** 学制名 -> 该学制包含的版本族（用于 edition_family 谓词）。 */
    private final Map<String, String[]> systemFamilies;

    private Syllabus(List<Map<String, List<Unit>>> books,
                     List<Map<String, String>> editionByCell,
                     Map<String, String[]> systemFamilies) {
        this.books = books;
        this.editionByCell = editionByCell;
        this.systemFamilies = systemFamilies;
    }

    /** 进度表缺失/损坏时的安全降级：不做任何单元过滤，学期过滤仍然可用。 */
    public static Syllabus empty() {
        List<Map<String, List<Unit>>> b = new ArrayList<Map<String, List<Unit>>>();
        List<Map<String, String>> e = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 3; i++) {
            b.add(Collections.<String, List<Unit>>emptyMap());
            e.add(Collections.<String, String>emptyMap());
        }
        return new Syllabus(Collections.unmodifiableList(b),
                Collections.unmodifiableList(e),
                Collections.<String, String[]>emptyMap());
    }

    private static int mode(int m) {
        return (m == EDITION_SIX_THREE || m == EDITION_FIVE_FOUR) ? m : EDITION_AUTO;
    }

    /** 该格子自动匹配的教材版本族；无表（如全学年）返回空串。 */
    public String editionFor(int grade, int term, int subject) {
        return editionFor(grade, term, subject, EDITION_AUTO);
    }

    public String editionFor(int grade, int term, int subject, int editionMode) {
        String v = editionByCell.get(mode(editionMode)).get(key(grade, term, subject));
        return v == null ? "" : v;
    }

    /** 该学制下此格子是否有可用的单元表（用于设置页如实提示）。 */
    public boolean hasCell(int grade, int term, int subject, int editionMode) {
        int m = mode(editionMode);
        if (m == EDITION_AUTO) return books.get(0).containsKey(key(grade, term, subject));
        return editionByCell.get(m).containsKey(key(grade, term, subject));
    }

    /**
     * 该学制要附加到 SQL 的 edition_family 值。
     * 自动模式返回该格代表族（1 个）；学制模式返回该学制的全部族，
     * 这样同一格子里的多套六三制教材（人教 / 统编 / 2026秋版）都能抽到。
     */
    public String[] familiesFor(int grade, int term, int subject, int editionMode) {
        int m = mode(editionMode);
        if (m == EDITION_AUTO) {
            String one = editionFor(grade, term, subject, EDITION_AUTO);
            return one.isEmpty() ? new String[0] : new String[]{one};
        }
        String[] f = systemFamilies.get(m == EDITION_SIX_THREE
                ? SYSTEM_SIX_THREE : SYSTEM_FIVE_FOUR);
        return f == null ? new String[0] : f.clone();
    }

    /** 版本族谓词；参数个数必须与 familiesFor() 返回值一致。 */
    public static String editionWhere(int familyCount) {
        if (familyCount <= 0) return "";
        if (familyCount == 1) return " AND edition_family = ?";
        StringBuilder sb = new StringBuilder(" AND edition_family IN (");
        for (int i = 0; i < familyCount; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.append(')').toString();
    }

    public static Syllabus fromJson(String json) {
        Map<String, Object> top = asObject(Json.parse(json), "root");
        List<Map<String, List<Unit>>> bs = new ArrayList<Map<String, List<Unit>>>();
        List<Map<String, String>> es = new ArrayList<Map<String, String>>();
        bs.add(parseBooks(top.get("grades"), true));
        es.add(parseEditions(top.get("editionByCell")));

        Map<String, String[]> fams = new HashMap<String, String[]>();
        Object famObj = top.get("systemFamilies");
        if (famObj != null) {
            for (Map.Entry<String, Object> e : asObject(famObj, "systemFamilies").entrySet()) {
                List<Object> arr = asArray(e.getValue(), "systemFamilies[]");
                ArrayList<String> v = new ArrayList<String>();
                for (Object o : arr) v.add(asString(o, "systemFamilies[][]"));
                fams.put(e.getKey(), v.toArray(new String[0]));
            }
        }
        Object sysObj = top.get("systems");
        Map<String, Object> systems = sysObj == null
                ? Collections.<String, Object>emptyMap() : asObject(sysObj, "systems");
        for (int m = 1; m <= 2; m++) {
            String name = m == EDITION_SIX_THREE ? SYSTEM_SIX_THREE : SYSTEM_FIVE_FOUR;
            Object view = systems.get(name);
            if (view == null) {
                bs.add(Collections.<String, List<Unit>>emptyMap());
                es.add(Collections.<String, String>emptyMap());
                continue;
            }
            Map<String, Object> v = asObject(view, "systems." + name);
            bs.add(parseBooks(v.get("grades"), false));
            es.add(parseEditions(v.get("editionByCell")));
        }
        return new Syllabus(Collections.unmodifiableList(bs),
                Collections.unmodifiableList(es),
                Collections.unmodifiableMap(fams));
    }

    private static Map<String, String> parseEditions(Object edObj) {
        HashMap<String, String> editions = new HashMap<String, String>();
        if (edObj == null) return Collections.unmodifiableMap(editions);
        for (Map.Entry<String, Object> e : asObject(edObj, "editionByCell").entrySet()) {
            editions.put(e.getKey(), asString(e.getValue(), "editionByCell[]"));
        }
        return Collections.unmodifiableMap(editions);
    }

    private static Map<String, List<Unit>> parseBooks(Object gradesObj, boolean required) {
        HashMap<String, List<Unit>> out = new HashMap<String, List<Unit>>();
        if (gradesObj == null) {
            if (required) throw new IllegalArgumentException("syllabus: missing grades");
            return Collections.unmodifiableMap(out);
        }
        Map<String, Object> grades = asObject(gradesObj, "grades");
        for (Map.Entry<String, Object> ge : grades.entrySet()) {
            int grade = intKey(ge.getKey(), "grade");
            Map<String, Object> terms = asObject(ge.getValue(), "grade " + grade);
            for (Map.Entry<String, Object> te : terms.entrySet()) {
                int term = intKey(te.getKey(), "term");
                if (term != TERM_FIRST && term != TERM_SECOND) {
                    throw new IllegalArgumentException("syllabus: bad term index " + term);
                }
                Map<String, Object> subjects = asObject(te.getValue(), "term " + term);
                for (Map.Entry<String, Object> se : subjects.entrySet()) {
                    int subject = subjectId(se.getKey());
                    List<Object> raw = asArray(se.getValue(), "unit list");
                    ArrayList<Unit> units = new ArrayList<Unit>();
                    Set<String> names = new LinkedHashSet<String>();
                    Set<String> aliases = new LinkedHashSet<String>();
                    for (Object o : raw) {
                        Map<String, Object> u = asObject(o, "unit");
                        String name = asString(u.get("name"), "unit.name");
                        if (name.isEmpty() || !names.add(name)) {
                            throw new IllegalArgumentException("syllabus: duplicate/empty unit name " + name);
                        }
                        List<Object> ms = asArray(u.get("match"), "unit.match");
                        ArrayList<String> mm = new ArrayList<String>();
                        for (Object mo : ms) {
                            String a = asString(mo, "unit.match[]");
                            if (a.isEmpty() || a.indexOf('%') >= 0 || a.indexOf('_') >= 0
                                    || a.indexOf('\'') >= 0) {
                                throw new IllegalArgumentException("syllabus: unsafe alias " + a);
                            }
                            if (!aliases.add(a)) {
                                throw new IllegalArgumentException("syllabus: duplicate alias " + a);
                            }
                            mm.add(a);
                        }
                        if (mm.isEmpty()) {
                            throw new IllegalArgumentException("syllabus: empty unit " + name);
                        }
                        units.add(new Unit(name, mm.toArray(new String[0])));
                    }
                    if (units.size() > MAX_UNITS) {
                        throw new IllegalArgumentException("syllabus: too many units");
                    }
                    out.put(key(grade, term, subject),
                            Collections.unmodifiableList(units));
                }
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** 自动视图的单元数（契约与 JVM 单测沿用）。 */
    public int bookCount() { return books.get(EDITION_AUTO).size(); }

    public List<Unit> book(int grade, int term, int subject) {
        return book(grade, term, subject, EDITION_AUTO);
    }

    /** 该册全部有序单元；未覆盖的组合返回空表（只做学期过滤）。 */
    public List<Unit> book(int grade, int term, int subject, int editionMode) {
        List<Unit> v = books.get(mode(editionMode)).get(key(grade, term, subject));
        return v == null ? Collections.<Unit>emptyList() : v;
    }

    public int unitCount(int grade, int term, int subject) {
        return unitCount(grade, term, subject, EDITION_AUTO);
    }

    public int unitCount(int grade, int term, int subject, int editionMode) {
        return book(grade, term, subject, editionMode).size();
    }

    /** 当前年级 / 学期下三科里最大的单元数，用于限制家长可点的序号上界。 */
    public int maxUnits(int grade, int term) {
        return maxUnits(grade, term, EDITION_AUTO);
    }

    public int maxUnits(int grade, int term, int editionMode) {
        int max = 0;
        for (int s = 0; s < Subjects.COUNT; s++) {
            int n = unitCount(grade, term, s, editionMode);
            if (n > max) max = n;
        }
        return max;
    }

    /** 前 limit 个单元展开成题库别名；limit<=0 或该册无表时返回空数组。 */
    public String[] matchNames(int grade, int term, int subject, int limit) {
        return matchNames(grade, term, subject, limit, EDITION_AUTO);
    }

    public String[] matchNames(int grade, int term, int subject, int limit, int editionMode) {
        List<Unit> b = book(grade, term, subject, editionMode);
        if (limit <= 0 || b.isEmpty()) return new String[0];
        int n = Math.min(limit, b.size());
        ArrayList<String> out = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            for (String a : b.get(i).match) out.add(a);
        }
        return out.toArray(new String[0]);
    }

    /** 设置页回显「已学到第 N 单元 · 单元名」；无表时返回空串。 */
    public String unitLabel(int grade, int term, int subject, int limit) {
        return unitLabel(grade, term, subject, limit, EDITION_AUTO);
    }

    public String unitLabel(int grade, int term, int subject, int limit, int editionMode) {
        List<Unit> b = book(grade, term, subject, editionMode);
        if (limit <= 0 || b.isEmpty()) return "";
        int n = Math.min(limit, b.size());
        return b.get(n - 1).label(n);
    }

    /**
     * 学期谓词：题库 term 有 上 / 下 两种真实值；
     * 兼容旧库的「全」与空串写法。全学年返回空串，覆盖安装行为不变。
     */
    public static String termWhere(int term) {
        if (term == TERM_FIRST) return " AND term IN ('上','全')";
        if (term == TERM_SECOND) return " AND term IN ('下','全')";
        return "";
    }

    /**
     * 单元谓词：topic 形如「几何：时分秒」，也可能就是单元名本身，
     * 因此每个别名绑定两种形式。unitArgs() 必须严格按同样顺序展开，
     * 主查询与 COUNT 子查询会各绑一份（见 QuizDb.pick）。
     */
    public static String unitWhere(String[] names) {
        if (names == null || names.length == 0) return "";
        StringBuilder sb = new StringBuilder(" AND (");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) sb.append(" OR ");
            sb.append("(topic LIKE ? OR topic = ?)");
        }
        return sb.append(')').toString();
    }

    public static String[] unitArgs(String[] names) {
        if (names == null || names.length == 0) return new String[0];
        String[] a = new String[names.length * 2];
        for (int i = 0; i < names.length; i++) {
            a[i * 2] = "%：" + names[i];
            a[i * 2 + 1] = names[i];
        }
        return a;
    }

    private static String key(int grade, int term, int subject) {
        return grade + "|" + term + "|" + subject;
    }

    private static int subjectId(String name) {
        if ("数学".equals(name)) return Subjects.MATH;
        if ("语文".equals(name)) return Subjects.CHINESE;
        if ("英语".equals(name)) return Subjects.ENGLISH;
        throw new IllegalArgumentException("syllabus: unsupported subject " + name);
    }

    private static int intKey(String s, String what) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("syllabus: bad " + what + " key " + s);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object o, String what) {
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException("syllabus: object expected for " + what);
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asArray(Object o, String what) {
        if (!(o instanceof List)) {
            throw new IllegalArgumentException("syllabus: array expected for " + what);
        }
        return (List<Object>) o;
    }

    private static String asString(Object o, String what) {
        if (!(o instanceof String)) {
            throw new IllegalArgumentException("syllabus: string expected for " + what);
        }
        return (String) o;
    }
}
