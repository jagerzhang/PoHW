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
 * questions(grade,grade_label,subject,subject_group,chapter,topic,text,opt_a..opt_d,
 *           answer_index,explain,source,qhash)、knowledge(全量纲目)、chapters、meta。
 * 出题只需一条本地随机查询；任何异常都返回 null，由 QuizEngine 回退到参数化生成器。
 */
public final class QuizDb {

    private static final String TAG = "PoHW.QuizDb";
    private static final String NAME = "quiz.db";
    private static final String VERSION_ASSET = "quiz.db.version";
    private static final String SELECT =
            "SELECT topic,text,opt_a,opt_b,opt_c,opt_d,answer_index,explain,subject "
                    + "FROM questions ";

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
                "SELECT COUNT(*) FROM questions WHERE grade=? AND subject=?",
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
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM questions WHERE grade=?",
                new String[]{Integer.toString(grade)})) {
            result = c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            result = 0;
        }
        synchronized (countCache) { countCache.put(key, Integer.valueOf(result)); }
        return result;
    }

    /** 兼容旧调用：仅在三科内抽题。 */
    public QuizEngine.Question random(int grade, int subject) {
        return random(grade, subject, false);
    }

    /**
     * 本地随机挑一题。
     * allSubjects=true 时从该年级的全部学科抽题（覆盖内置的全量 K12 纲目），
     * 否则只抽数学/语文/英语；两者都取不到时回退全库，保证「一定能出题」。
     * 用 OFFSET 采样避免整表排序；COUNT 子查询走 grade/subject 索引。
     */
    public QuizEngine.Question random(int grade, int subject, boolean allSubjects) {
        String name = subjectName(subject);
        boolean gradeWide = allSubjects || name == null;
        String where = gradeWide ? "WHERE grade=?" : "WHERE grade=? AND subject=?";
        String[] args = gradeWide
                ? new String[]{Integer.toString(grade)}
                : new String[]{Integer.toString(grade), name};
        QuizEngine.Question q = pick(where, args, subject);
        if (q == null) q = pick("", null, subject);
        return q;
    }

    private QuizEngine.Question pick(String where, String[] args, int subject) {
        // The WHERE params must be bound twice: main query and COUNT subquery.
        String[] bind = null;
        if (args != null) {
            bind = new String[args.length * 2];
            System.arraycopy(args, 0, bind, 0, args.length);
            System.arraycopy(args, 0, bind, args.length, args.length);
        }
        String sql = SELECT + where
                + " LIMIT 1 OFFSET (ABS(RANDOM()) % (SELECT COUNT(*) FROM questions "
                + where + "))";
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
            QuizEngine.Question q = QuizEngine.Question.fromBank(subject, topic,
                    c.getString(1), opts, c.getInt(6), c.getString(7));
            if (q != null && bankSubject != null && !isCoreSubject(bankSubject)) {
                q.subject = Subjects.OTHER;
            }
            return q;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isCoreSubject(String s) {
        return "数学".equals(s) || "语文".equals(s) || "英语".equals(s);
    }

    private static String subjectName(int subject) {
        if (subject == QuizEngine.CHINESE) return "语文";
        if (subject == QuizEngine.ENGLISH) return "英语";
        if (subject == QuizEngine.MATH) return "数学";
        return null; // Subjects.OTHER 及其他未知值：没有对应的内置离线学科名
    }
}
