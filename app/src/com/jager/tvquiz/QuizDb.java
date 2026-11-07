package com.jager.tvquiz;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 内置离线 SQLite 题库。
 *
 * <p>APK 的 assets/quiz.db 随包发布，首次使用复制到应用 databases 目录，之后全程只读、零网络。
 * 版本升级用 assets/quiz.db.version 与本地库 meta.version 比对，不一致时原子替换，
 * 避免「只比文件长度」导致的同长度旧库残留，以及写一半断电产生损坏库。
 *
 * <p>表结构由 tools/quizbank/build_bank.py 生成：
 * questions(grade,grade_label,term,subject,subject_group,chapter,topic,text,opt_a..opt_d,
 *           answer_index,explain,source,qhash)、knowledge(全量纲目)、chapters、meta。
 * 抽题按学科池查一次，可叠加 term / topic 学习进度；空池时逐级放宽学期与单元，
 * 但任何一层都不放宽年级上限（grade<=用户年级），也不换学科。任何异常返回 null，由 QuizEngine 回退生成器。
 */
public final class QuizDb {

    private static final String TAG = "PoHW.QuizDb";
    private static final String NAME = "quiz.db";
    private static final String VERSION_ASSET = "quiz.db.version";
    private static final String SELECT =
            "SELECT topic,text,opt_a,opt_b,opt_c,opt_d,answer_index,explain,subject "
                    + "FROM questions ";

    // 四个池谓词集中定义：所有层都带 grade<=? 年级上限，绝不为了凑题放出高年级内容；
    // 学科约束也贯穿始终，避免池为空时悄悄抽到别的学科、破坏配比。tests/bank_distribution_check.py
    // 会直接从这些常量读取 SQL，改坏源码会立刻被测出来。
    private static final String POOL_OTHER_GRADE =
            "WHERE grade<=? AND subject NOT IN ('数学','语文','英语')";
    private static final String POOL_OTHER_ALL =
            "WHERE grade<=? AND subject NOT IN ('数学','语文','英语')";
    private static final String POOL_CORE_GRADE = "WHERE grade<=? AND subject=?";
    private static final String POOL_CORE_ALL = "WHERE grade<=? AND subject=?";
    /** 可选出题偏好：只优先抽真知识 / 计算题；空池时仍按原有五层兜底回退全库。 */
    private static final String KNOWLEDGE_ONLY_SQL = " AND qtype='knowledge'";

    private static QuizDb instance;
    private final SQLiteDatabase db;
    // 这些聚合值是内置静态题库的元数据，打开后就不会变；在 open() 里一次性算好，
    // 避免 MainActivity 每秒在 UI 线程上重复查 SQLite。
    private final int cachedCount;
    private final int cachedKnowledge;
    private final int cachedSubjects;
    private final int cachedBands;
    private final String cachedVersion;
    private final String cachedNote;
    private final java.util.HashMap<String, Integer> countCache =
            new java.util.HashMap<String, Integer>();

    /** Ready instance only; never triggers blocking open/copy. */
    public static synchronized QuizDb peek() { return instance; }

    private QuizDb(SQLiteDatabase db, int count, int knowledge, int subjects,
                   int bands, String version, String note) {
        this.db = db;
        this.cachedCount = count;
        this.cachedKnowledge = knowledge;
        this.cachedSubjects = subjects;
        this.cachedBands = bands;
        this.cachedVersion = version;
        this.cachedNote = note;
    }

    /** 打开内置题库；失败返回 null，调用方必须能优雅回退到生成器。 */
    public static synchronized QuizDb open(Context c) {
        if (instance != null) return instance;
        try {
            Context app = c.getApplicationContext();
            File dest = app.getDatabasePath(NAME);
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return null;

            String assetV = assetVersion(app);
            String localV = dest.exists() ? localVersion(dest) : null;
            if (localV == null || !localV.equals(assetV)) {
                if (!copyAtomically(app, dest)) return null;
            }
            SQLiteDatabase d = SQLiteDatabase.openDatabase(
                    dest.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            int qCount = scalar(d, "SELECT COUNT(*) FROM questions");
            if (qCount <= 0) {
                d.close();
                return null;
            }
            // 聚合元数据只在此处查询一次，之后 getter 直接返回缓存值。
            instance = new QuizDb(d, qCount,
                    scalar(d, "SELECT COUNT(*) FROM knowledge"),
                    scalar(d, "SELECT COUNT(*) FROM (SELECT DISTINCT subject FROM knowledge)"),
                    scalar(d, "SELECT COUNT(*) FROM (SELECT DISTINCT grade_label FROM knowledge)"),
                    readString(d, "SELECT v FROM meta WHERE k='version'"),
                    readString(d, "SELECT v FROM meta WHERE k='licenseNote'"));
            return instance;
        } catch (Exception e) {
            Log.e(TAG, "内置题库打开失败，回退生成器", e);
            instance = null;
            return null;
        }
    }

    /** assets 中随库发布的版本戳；旧包没有该文件时用长度兜底。 */
    private static String assetVersion(Context c) {
        InputStream in = null;
        try {
            in = c.getAssets().open(VERSION_ASSET);
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n > 0) {
                String v = new String(buf, 0, n, "UTF-8").trim();
                if (!v.isEmpty()) return v;
            }
        } catch (Exception ignored) {
            // 兼容旧产物
        } finally {
            closeQuietly(in);
        }
        return "size:" + assetSize(c);
    }

    private static long assetSize(Context c) {
        InputStream in = null;
        long total = 0L;
        try {
            in = c.getAssets().open(NAME);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) total += n;
        } catch (Exception e) {
            return -1L;
        } finally {
            closeQuietly(in);
        }
        return total;
    }

    private static String localVersion(File f) {
        SQLiteDatabase d = null;
        Cursor c = null;
        try {
            d = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            c = d.rawQuery("SELECT v FROM meta WHERE k='version'", null);
            return c.moveToFirst() ? c.getString(0) : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) { try { c.close(); } catch (Exception ignored) { } }
            if (d != null) { try { d.close(); } catch (Exception ignored) { } }
        }
    }

    /** 先写 .tmp 并 fsync，校验可读后再原子替换，绝不留下半截数据库。 */
    private static boolean copyAtomically(Context c, File dest) {
        File tmp = new File(dest.getParentFile(), NAME + ".tmp");
        InputStream in = null;
        OutputStream out = null;
        SQLiteDatabase probe = null;
        try {
            in = c.getAssets().open(NAME);
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            ((FileOutputStream) out).getFD().sync();
            out.close();
            out = null;
            in.close();
            in = null;

            probe = SQLiteDatabase.openDatabase(tmp.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            if (scalar(probe, "SELECT COUNT(*) FROM questions") <= 0) {
                tmp.delete();
                return false;
            }
            probe.close();
            probe = null;
            if (dest.exists() && !dest.delete()) {
                tmp.delete();
                return false;
            }
            return tmp.renameTo(dest);
        } catch (Exception e) {
            Log.e(TAG, "题库复制失败", e);
            tmp.delete();
            return false;
        } finally {
            closeQuietly(out);
            closeQuietly(in);
            if (probe != null) { try { probe.close(); } catch (Exception ignored) { } }
        }
    }

    private static int scalar(SQLiteDatabase d, String sql) {
        try (Cursor c = d.rawQuery(sql, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void closeQuietly(java.io.Closeable x) {
        if (x != null) { try { x.close(); } catch (Exception ignored) { } }
    }

    public int count() { return cachedCount; }
    public int knowledgeCount() { return cachedKnowledge; }
    public int subjectCount() { return cachedSubjects; }
    public int gradeBandCount() { return cachedBands; }
    public String version() { return cachedVersion; }
    public String note() { return cachedNote; }

    private static String readString(SQLiteDatabase d, String sql) {
        try (Cursor c = d.rawQuery(sql, null)) {
            return c.moveToFirst() ? c.getString(0) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 指定年级 + 指定学科的题目数；结果是内置静态数据，按 grade+subject 缓存。 */
    public int countFor(int grade, int subject) {
        String key = grade + ":" + subject;
        synchronized (countCache) {
            Integer v = countCache.get(key);
            if (v != null) return v.intValue();
        }
        int result;
        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM questions WHERE grade<=? AND subject=?",
                new String[]{Integer.toString(grade), subjectName(subject)})) {
            result = c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            result = 0;
        }
        synchronized (countCache) { countCache.put(key, Integer.valueOf(result)); }
        return result;
    }

    /** 指定年级的全部学科题目数（「全学科」模式用）；同样缓存。 */
    public int countForGrade(int grade) {
        String key = grade + ":*";
        synchronized (countCache) {
            Integer v = countCache.get(key);
            if (v != null) return v.intValue();
        }
        int result;
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM questions WHERE grade<=?",
                new String[]{Integer.toString(grade)})) {
            result = c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            result = 0;
        }
        synchronized (countCache) { countCache.put(key, Integer.valueOf(result)); }
        return result;
    }

    /** 全库是否存在非三科题；QuizEngine 据此决定 OTHER 槽是否参与轮盘赌。 */
    public boolean hasOtherQuestions() {
        synchronized (countCache) {
            Integer v = countCache.get("other_pool");
            if (v != null) return v.intValue() > 0;
        }
        int n = scalar(db, "SELECT COUNT(*) FROM questions "
                + "WHERE subject NOT IN ('数学','语文','英语')");
        synchronized (countCache) { countCache.put("other_pool", Integer.valueOf(n)); }
        return n > 0;
    }

    /** 兼容旧调用：不带学习进度，行为与 code17 完全一致（全学年 / 不限单元）。 */
    public QuizEngine.Question random(int grade, int subject, boolean allSubjects) {
        return random(grade, subject, allSubjects, null, Syllabus.TERM_ALL, 0);
    }

    /**
     * 本地随机挑一题：三科按 grade + subject 精确抽，
     * 「其他学科」按 grade + subject NOT IN (三科) 抽，由 SubjectWeights 决定进哪个池。
     * 学习进度（方案 B）在池谓词之后叠加学期与单元条件；空池时逐级放宽：
     * 同年级+同学科+学期+单元 -> +学期 -> 同年级+同学科 -> 同学科跨年级 -> 全库。
     * 前四层始终保留学科约束，最后一层仅在题库本身缺该科时才走。
     * tests/study_scope_check.py 用真实 SQL 复刻这条链并逐层断言。
     * 用 OFFSET 采样避免整表排序；COUNT 子查询走 grade/subject 索引。
     */
    public QuizEngine.Question random(int grade, int subject, boolean allSubjects,
                                      Syllabus syllabus, int termIndex, int unitLimit) {
        return random(grade, subject, allSubjects, syllabus, termIndex, unitLimit, false);
    }

    /** 兼容重载：不带难度倾向，行为与 code23 逐字一致。 */
    public QuizEngine.Question random(int grade, int subject, boolean allSubjects,
                                      Syllabus syllabus, int termIndex, int unitLimit,
                                      boolean knowledgeOnly) {
        return random(grade, subject, allSubjects, syllabus, termIndex, unitLimit,
                knowledgeOnly, DifficultyPolicy.LEVEL_ALL, false);
    }

    /** 难度只做倾向加权：matchLevel=true 时所有兜底层都带 difficulty_level 约束。 */
    public QuizEngine.Question random(int grade, int subject, boolean allSubjects,
                                      Syllabus syllabus, int termIndex, int unitLimit,
                                      boolean knowledgeOnly, int difficultyLevel,
                                      boolean matchLevel) {
        String diffW = matchLevel ? DifficultyPolicy.predicate(difficultyLevel) : "";
        String diffRoot = matchLevel ? DifficultyPolicy.rootPredicate(difficultyLevel) : "";
        int s = Subjects.norm(subject);
        // 路由条件必须与此字面量一致：只有「选中 OTHER 且开启全学科」才进其他池。
        // tests/layout_contract.py 与 build.sh 都钉死了这一行。
        boolean other = s == Subjects.OTHER && allSubjects;
        String g = Integer.toString(grade);
        String where, poolWhere;
        String[] args, poolArgs;
        if (other) {
            where = POOL_OTHER_GRADE;
            args = new String[]{g};
            poolWhere = POOL_OTHER_ALL;
            poolArgs = new String[]{g};
        } else {
            String name = subjectName(s);
            if (name == null) name = "数学";
            where = POOL_CORE_GRADE;
            args = new String[]{g, name};
            poolWhere = POOL_CORE_ALL;
            poolArgs = new String[]{g, name};
        }

        if (knowledgeOnly) {
            // 810 道程序出题全部 term=''、topic 是题型名，学期/单元条件会把它们全部排除。
            // 所以偏好模式不用 termW/unitW；且每一层兜底都必须保留 source 约束。
            // 两层都空时返回 null，交给 QuizEngine 的参数化生成器出真计算 / 词库题，
            // 绝不回退到含 4319 道纲目定位题的全库。
            QuizEngine.Question kq = pick(where + KNOWLEDGE_ONLY_SQL + diffW, args);
            if (kq == null) kq = pick(poolWhere + KNOWLEDGE_ONLY_SQL + diffW, poolArgs);
            return kq;
        }

        String termW = Syllabus.termWhere(termIndex);
        // 其他学科池与未覆盖的册没有有序单元表：只做学期过滤，绝不假装精确。
        String[] names = (syllabus != null && unitLimit > 0 && !other)
                ? syllabus.matchNames(grade, termIndex, s, unitLimit) : new String[0];
        String unitW = Syllabus.unitWhere(names);
        String[] unitA = Syllabus.unitArgs(names);

        QuizEngine.Question q;
        String strict = where + termW + unitW + diffW;
        String tried = strict;
        q = pick(strict, concat(args, unitA));
        if (q == null) {
            String termOnly = where + termW + diffW;
            if (!termOnly.equals(tried)) {
                q = pick(termOnly, args);
                tried = termOnly;
            }
        }
        if (q == null) q = pick(where + diffW, args);
        if (q == null) q = pick(poolWhere + diffW, poolArgs);
        if (q == null) {
            // 最后一层保底也必须带年级上限：绝不为了「有题可出」放出高年级内容。
            String rootW = diffRoot.isEmpty() ? "WHERE grade<=?" : diffRoot + " AND grade<=?";
            q = pick(rootW, new String[]{g});
        }
        return q;
    }

    /** 基础池参数与单元别名参数拼接；顺序必须与 unitWhere() 的占位符一致。 */
    private static String[] concat(String[] base, String[] extra) {
        if (extra == null || extra.length == 0) return base;
        String[] out = new String[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }

    private QuizEngine.Question pick(String where, String[] args) {
        // The WHERE params must be bound twice: main query and COUNT subquery.
        String[] bind = null;
        if (args != null) {
            bind = new String[args.length * 2];
            System.arraycopy(args, 0, bind, 0, args.length);
            System.arraycopy(args, 0, bind, args.length, args.length);
        }
        // 空池时 COUNT=0 会让 OFFSET 变成 NULL（SQLite 报 datatype mismatch），
        // MAX(1, COUNT) 让主查询正常返回「无行」，由调用方走下一层兜底，不靠吞异常。
        String sql = SELECT + where
                + " LIMIT 1 OFFSET (ABS(RANDOM()) % MAX(1, (SELECT COUNT(*) FROM questions "
                + where + ")))";
        try (Cursor c = db.rawQuery(sql, bind)) {
            if (!c.moveToFirst()) return null;
            String bankSubject = c.getString(8);
            String topic = c.getString(0);
            // 全学科题在题目标签上带学科名，避免物理/历史题被误认成三科题
            if (bankSubject != null && !isCoreSubject(bankSubject)) {
                topic = bankSubject + " · " + (topic == null ? "" : topic);
            }
            String[] opts = new String[]{
                    c.getString(2), c.getString(3), c.getString(4), c.getString(5)};
            // 用题目真实学科反推标签：配比选中的学科池可能与兜底结果不同，
            // 错题库与统计必须以题库里的 subject 为准，不能沿用传入的池编号。
            return QuizEngine.Question.fromBank(subjectFromBank(bankSubject), topic,
                    c.getString(1), opts, c.getInt(6), c.getString(7));
        } catch (Exception e) {
            Log.e(TAG, "抽题查询失败，回退下一层兜底", e);
            return null;
        }
    }

    private static boolean isCoreSubject(String s) {
        return "数学".equals(s) || "语文".equals(s) || "英语".equals(s);
    }

    private static int subjectFromBank(String s) {
        if ("数学".equals(s)) return QuizEngine.MATH;
        if ("语文".equals(s)) return QuizEngine.CHINESE;
        if ("英语".equals(s)) return QuizEngine.ENGLISH;
        return Subjects.OTHER;
    }

    private static String subjectName(int subject) {
        if (subject == QuizEngine.CHINESE) return "语文";
        if (subject == QuizEngine.ENGLISH) return "英语";
        if (subject == QuizEngine.MATH) return "数学";
        return null; // Subjects.OTHER 及其他未知值：没有对应的内置离线学科名
    }
}
