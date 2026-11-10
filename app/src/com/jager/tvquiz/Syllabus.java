package com.jager.tvquiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 方案 B：学期 + 单元进度表。
 *
 * <p>assets/syllabus.json 人工维护「年级 × 学期 × 学科 → 按教学顺序排列的单元」，
 * 每个单元记录它在题库 topic 里可能出现的所有别名（例如「时、分、秒」与「时分秒」）。
 * 家长选「上册 / 已学到第 N 单元」后，本题负责生成附加到 QuizDb 池谓词之后的
 * SQL 片段与参数；QuizDb 再按「单元 -> 学期 -> 年级」逐级放宽，始终保留学科约束。
 *
 * <p>本类是纯 Java，不引用任何 android.*：Json 解析器也是纯 Java，
 * 所以单元表能在 JVM 单测里用真实 assets/syllabus.json 直接跑。
 * 读取 assets 的动作留在 QuizOverlayService / SettingsActivity（它们才有 Context）。
 *
 * <p>诚实边界：题库没有出版社教材版本、册次（term 全为空）和单元序号；
 * 本表只是把题库真实出现的课标纲目名按常见教学顺序归并，覆盖小学 1–6 年级数学，
 * 不等于任何教材版本的真实单元进度。语文是课文名、英语是主题词，不做单元过滤。
 * 运行时 QuizDb 还会把 syllabus_scope='generic' 的通用题（无教材版本）排除在单元过滤之外。
 * 未覆盖的组合返回空名单，绝不假装精确。
 */
public final class Syllabus {

    public static final int TERM_ALL = 0;
    public static final int TERM_FIRST = 1;
    public static final int TERM_SECOND = 2;
    public static final String[] TERM_LABELS = {"全学年", "上册", "下册"};
    /** 家长可选的最大单元序号；也是 SettingsStore.unitLimit() 的合法上界。 */
    public static final int MAX_UNITS = 24;

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

    private final Map<String, List<Unit>> books;
    /** (年级|册次|学科) -> 该格子自动匹配的教材版本族，用于按单元过滤时隔离版本。 */
    private final Map<String, String> editionByCell;

    private Syllabus(Map<String, List<Unit>> books,
                     Map<String, String> editionByCell) {
        this.books = books;
        this.editionByCell = editionByCell;
    }

    /** 进度表缺失/损坏时的安全降级：不做任何单元过滤，学期过滤仍然可用。 */
    public static Syllabus empty() {
        return new Syllabus(Collections.<String, List<Unit>>emptyMap(),
                Collections.<String, String>emptyMap());
    }

    /** 该格子自动匹配的教材版本族；无表（如全学年）返回空串。 */
    public String editionFor(int grade, int term, int subject) {
        String v = editionByCell.get(key(grade, term, subject));
        return v == null ? "" : v;
    }

    public static Syllabus fromJson(String json) {
        Map<String, Object> top = asObject(Json.parse(json), "root");
        HashMap<String, String> editions = new HashMap<String, String>();
        Object edObj = top.get("editionByCell");
        if (edObj != null) {
            for (Map.Entry<String, Object> e : asObject(edObj, "editionByCell").entrySet()) {
                editions.put(e.getKey(), asString(e.getValue(), "editionByCell[]"));
            }
        }
        Object gradesObj = top.get("grades");
        if (gradesObj == null) throw new IllegalArgumentException("syllabus: missing grades");
        Map<String, Object> grades = asObject(gradesObj, "grades");
        HashMap<String, List<Unit>> out = new HashMap<String, List<Unit>>();
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
        return new Syllabus(Collections.unmodifiableMap(out),
                Collections.unmodifiableMap(editions));
    }

    public int bookCount() { return books.size(); }

    /** 该册全部有序单元；未覆盖的组合返回空表（只做学期过滤）。 */
    public List<Unit> book(int grade, int term, int subject) {
        List<Unit> v = books.get(key(grade, term, subject));
        return v == null ? Collections.<Unit>emptyList() : v;
    }

    public int unitCount(int grade, int term, int subject) {
        return book(grade, term, subject).size();
    }

    /** 当前年级 / 学期下三科里最大的单元数，用于限制家长可点的序号上界。 */
    public int maxUnits(int grade, int term) {
        int max = 0;
        for (int s = 0; s < Subjects.COUNT; s++) {
            int n = unitCount(grade, term, s);
            if (n > max) max = n;
        }
        return max;
    }

    /** 前 limit 个单元展开成题库别名；limit<=0 或该册无表时返回空数组。 */
    public String[] matchNames(int grade, int term, int subject, int limit) {
        List<Unit> b = book(grade, term, subject);
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
        List<Unit> b = book(grade, term, subject);
        if (limit <= 0 || b.isEmpty()) return "";
        int n = Math.min(limit, b.size());
        return b.get(n - 1).label(n);
    }

    /**
     * 学期谓词：题库 term 有 上 / 下 / 全 / '' 四种值。
     * 选上/下册时包含「全一册」，并排除 term='' 的 810 道综合练习
     * （它们没有学期与单元，三年级样本里混有质数 / 百分数等超纲题）。
     * 全学年返回空串，保持旧版行为完全不变，兼容覆盖安装。
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
