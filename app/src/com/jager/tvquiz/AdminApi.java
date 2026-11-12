package com.jager.tvquiz;

import android.content.Context;
import android.content.Intent;
import java.security.SecureRandom;

/**
 * v1.3.4 未发布：局域网管理 API。所有写操作与状态读取都要 Bearer token。
 *
 * <p>token 只存内存、进程重启失效；PIN 连续失败 5 次锁 60 秒；
 * 只暴露白名单路径，不提供任意文件读取。数据全部留在设备本地。
 *
 * <p>手机端开放绝大部分家长设置（31 个可写键），每个值都做范围/枚举/格式校验；
 * 有意不开放：修改家长密码、清空错题库与统计、关闭手机管理自身、
 * 在线题库地址、立即检查更新等破坏性或安全敏感操作。
 */
public final class AdminApi {

    private static volatile String token = null;
    private static volatile int failures = 0;
    private static volatile long lockUntil = 0L;
    private static final SecureRandom RND = new SecureRandom();

    private static final String[] NUM_KEYS = {
            "grade", "interval", "questionsPerRound", "passPercent", "roundMode",
            "termIndex", "unitLimit", "editionMode", "unlockStreak",
            "questionSource", "difficultyLevel", "syncIndex",
            "w0", "w1", "w2", "w3",
            "readMode"
    };
    private static final String[] BOOL_KEYS = {
            "allSubjects", "strictOneShot", "cooldownEnabled", "autoContinueOnCorrect",
            "bootAutostart", "autoGuardOnBoot", "pauseOnQuiz", "resumePlayback",
            "autoCheckUpdate", "scheduleEnabled", "skipHolidays"
    };
    /** issue #37：字符串型设置，逐个走 SchedulePolicy 的严格格式校验。 */
    private static final String[] STR_KEYS = {
            "schoolWindows", "homeWindows", "vacationRange", "todayOverride"
    };

    private AdminApi() { }

    public static void reset() { token = null; failures = 0; lockUntil = 0L; }

    public static boolean authorized(String auth) {
        return token != null && auth != null && auth.equals("Bearer " + token);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** 返回 HTTP 状态码与 JSON 正文。 */
    public static Result handle(Context c, String method, String path,
                                String auth, String body) {
        if ("/api/login".equals(path) && "POST".equals(method)) {
            if (System.currentTimeMillis() < lockUntil) {
                return new Result(429, "{\"error\":\"尝试过多，请稍后再试\"}");
            }
            String pin = jsonString(body, "pin");
            SettingsStore st = new SettingsStore(c);
            if (!st.hasPin() || pin == null || !st.checkPin(pin)) {
                failures++;
                if (failures >= 5) lockUntil = System.currentTimeMillis() + 60000L;
                return new Result(401, "{\"error\":\"密码不正确\"}");
            }
            failures = 0;
            byte[] b = new byte[24];
            RND.nextBytes(b);
            token = Hashes.hex(b);
            return new Result(200, "{\"token\":\"" + token + "\"}");
        }
        if (!authorized(auth)) return new Result(401, "{\"error\":\"请先验证密码\"}");

        SettingsStore st = new SettingsStore(c);
        if ("/api/status".equals(path) && "GET".equals(method)) {
            return new Result(200, statusJson(c, st));
        }
        if ("/api/config".equals(path) && "POST".equals(method)) {
            applyConfig(st, body);
            try {
                Intent i = new Intent(c, QuizOverlayService.class)
                        .setAction(QuizOverlayService.ACTION_RELOAD);
                c.startService(i);
            } catch (Exception ignored) { }
            return new Result(200, "{\"ok\":true}");
        }
        return new Result(404, "{\"error\":\"unknown api\"}");
    }

    /** 手机端能读到的全部状态与枚举选项，保证页面与源码同源。 */
    private static String statusJson(Context c, SettingsStore st) {
        StringBuilder s = new StringBuilder();
        s.append("{\"guard\":").append(QuizOverlayService.isRunning());
        s.append(",\"wrong\":").append(WrongBook.cachedCount());
        s.append(",\"grade\":").append(st.grade());
        s.append(",\"interval\":").append(st.intervalSec());
        s.append(",\"questionsPerRound\":").append(st.questionsPerRound());
        s.append(",\"passPercent\":").append(st.passPercent());
        s.append(",\"roundMode\":").append(st.roundMode());
        s.append(",\"termIndex\":").append(st.termIndex());
        s.append(",\"unitLimit\":").append(st.unitLimit());
        s.append(",\"editionMode\":").append(st.editionMode());
        s.append(",\"unlockStreak\":").append(st.unlockStreak());
        s.append(",\"questionSource\":").append(st.questionSource());
        s.append(",\"difficultyLevel\":").append(st.difficultyLevel());
        s.append(",\"syncIndex\":").append(st.syncIndex());
        s.append(",\"allSubjects\":").append(st.allSubjects());
        s.append(",\"strictOneShot\":").append(st.strictOneShot());
        s.append(",\"cooldownEnabled\":").append(st.cooldownEnabled());
        s.append(",\"autoContinueOnCorrect\":").append(st.autoContinueOnCorrect());
        s.append(",\"bootAutostart\":").append(st.bootAutostart());
        s.append(",\"autoGuardOnBoot\":").append(st.autoGuardOnBoot());
        s.append(",\"pauseOnQuiz\":").append(st.pauseOnQuiz());
        s.append(",\"resumePlayback\":").append(st.resumePlayback());
        s.append(",\"autoCheckUpdate\":").append(st.autoCheckUpdate());
        s.append(",\"scheduleEnabled\":").append(st.scheduleEnabled());
        s.append(",\"skipHolidays\":").append(st.skipHolidays());
        s.append(",\"readMode\":").append(st.readMode());
        s.append(",\"ttsState\":").append(st.ttsState());
        s.append(",\"ttsStateText\":\"")
                .append(SpeechReader.stateLabel(st.ttsState())).append("\"");
        s.append(",\"schoolWindows\":\"").append(st.schoolWindows()).append("\"");
        s.append(",\"homeWindows\":\"").append(st.homeWindows()).append("\"");
        s.append(",\"vacationRange\":\"").append(st.vacationRange()).append("\"");
        s.append(",\"todayOverride\":\"").append(st.todayOverride()).append("\"");
        HolidayCalendar hc = HolidayAssets.get(c);
        s.append(",\"holidayCoverageStart\":").append(hc.usable() ? hc.firstCoveredYear() : 0);
        s.append(",\"holidayCoverageEnd\":").append(hc.usable() ? hc.lastCoveredYear() : 0);
        s.append(",\"scheduleAllowedNow\":").append(
                SchedulePolicy.decide(System.currentTimeMillis(),
                        java.util.TimeZone.getDefault(), st.scheduleConfig(), hc).allowed);
        for (int i = 0; i < Subjects.COUNT; i++) {
            s.append(",\"w").append(i).append("\":").append(st.weight(i));
        }
        s.append(",\"intervals\":");
        appendInts(s, SettingsStore.INTERVALS);
        s.append(",\"intervalLabels\":");
        appendStrings(s, SettingsStore.INTERVAL_LABELS);
        s.append(",\"editionLabels\":");
        appendStrings(s, Syllabus.EDITION_LABELS);
        s.append(",\"syncLabels\":");
        appendStrings(s, SettingsStore.SYNC_LABELS);
        s.append(",\"maxWeight\":").append(SubjectWeights.MAX_WEIGHT);
        s.append(",\"maxGrade\":").append(SettingsStore.MAX_GRADE);
        String ver = "unknown";
        try {
            ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception ignored) { }
        s.append(",\"version\":\"").append(ver).append("\"}");
        return s.toString();
    }

    private static void appendInts(StringBuilder s, int[] a) {
        s.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) s.append(',');
            s.append(a[i]);
        }
        s.append(']');
    }

    private static void appendStrings(StringBuilder s, String[] a) {
        s.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) s.append(',');
            s.append('"').append(a[i]).append('"');
        }
        s.append(']');
    }

    /** 逐字段校验后再落盘；缺失字段保持原值，方便手机端增量提交。 */
    private static void applyConfig(SettingsStore st, String body) {
        for (String key : NUM_KEYS) {
            int v = jsonInt(body, key);
            if (v < 0) continue;
            if ("grade".equals(key)) st.grade(clamp(v, SettingsStore.MIN_GRADE,
                    SettingsStore.MAX_GRADE));
            else if ("interval".equals(key)) {
                boolean allowed = false;
                for (int x : SettingsStore.INTERVALS) if (x == v) allowed = true;
                if (allowed) st.intervalSec(v);
            }
            else if ("questionsPerRound".equals(key)) st.questionsPerRound(clamp(v, 1, 10));
            else if ("passPercent".equals(key)) st.passPercent(clamp(v, 1, 100));
            else if ("roundMode".equals(key)) st.roundMode(clamp(v, 0, 2));
            else if ("termIndex".equals(key)) st.termIndex(clamp(v, Syllabus.TERM_ALL,
                    Syllabus.TERM_SECOND));
            else if ("unitLimit".equals(key)) st.unitLimit(clamp(v, 0, Syllabus.MAX_UNITS));
            else if ("editionMode".equals(key)) st.editionMode(clamp(v, 0,
                    Syllabus.EDITION_LABELS.length - 1));
            else if ("unlockStreak".equals(key)) st.unlockStreak(clamp(v,
                    SettingsStore.STREAK_MIN, SettingsStore.STREAK_MAX));
            else if ("questionSource".equals(key)) st.questionSource(clamp(v,
                    SettingsStore.SOURCE_ALL, SettingsStore.SOURCE_KNOWLEDGE_ONLY));
            else if ("difficultyLevel".equals(key)) st.difficultyLevel(clamp(v, 0, 3));
            else if ("syncIndex".equals(key)) st.syncIndex(clamp(v, 0,
                    SettingsStore.SYNC_HOURS.length - 1));
            else if ("readMode".equals(key)) st.readMode(clamp(v, 0,
                    SettingsStore.READ_MODE_LABELS.length - 1));
            else if (key.length() == 2 && key.charAt(0) == 'w') {
                int idx = key.charAt(1) - '0';
                if (idx >= 0 && idx < Subjects.COUNT) {
                    st.weight(idx, clamp(v, 0, SubjectWeights.MAX_WEIGHT));
                }
            }
        }
        for (String key : BOOL_KEYS) {
            int v = jsonBool(body, key);
            if (v < 0) continue;
            boolean on = v == 1;
            if ("allSubjects".equals(key)) st.allSubjects(on);
            else if ("strictOneShot".equals(key)) st.strictOneShot(on);
            else if ("cooldownEnabled".equals(key)) st.cooldownEnabled(on);
            else if ("autoContinueOnCorrect".equals(key)) st.autoContinueOnCorrect(on);
            else if ("bootAutostart".equals(key)) st.bootAutostart(on);
            else if ("autoGuardOnBoot".equals(key)) st.autoGuardOnBoot(on);
            else if ("pauseOnQuiz".equals(key)) st.pauseOnQuiz(on);
            else if ("resumePlayback".equals(key)) st.resumePlayback(on);
            else if ("autoCheckUpdate".equals(key)) st.autoCheckUpdate(on);
            else if ("scheduleEnabled".equals(key)) st.scheduleEnabled(on);
            else if ("skipHolidays".equals(key)) st.skipHolidays(on);
        }
        for (String key : STR_KEYS) {
            String v = jsonString(body, key);
            if (v == null) continue;
            if ("schoolWindows".equals(key) || "homeWindows".equals(key)) {
                if (!SchedulePolicy.validWindows(v)) continue;
                if ("schoolWindows".equals(key)) st.schoolWindows(v);
                else st.homeWindows(v);
            } else if ("vacationRange".equals(key)) {
                if (SchedulePolicy.validVacationRange(v)) st.vacationRange(v);
            } else if ("todayOverride".equals(key)) {
                if (SchedulePolicy.validOverride(v)) st.todayOverride(v);
            }
        }
    }

    private static String jsonString(String body, String key) {
        if (body == null) return null;
        int i = body.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int c = body.indexOf(':', i);
        int q1 = body.indexOf('"', c + 1);
        if (q1 < 0) return null;
        int q2 = body.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return body.substring(q1 + 1, q2);
    }

    /** JSON 数字或数字字符串。旧实现先走 jsonString()，多字段时只有最后一个
     *  数字能被解析（它会把下一个 key 的引号当成值），导致 /api/config 静默
     *  丢掉 questionsPerRound / passPercent。这里只按冒号后的字面值解析。 */
    private static int jsonInt(String body, String key) {
        if (body == null) return -1;
        int i = body.indexOf("\"" + key + "\"");
        if (i < 0) return -1;
        int c = body.indexOf(':', i);
        if (c < 0) return -1;
        int k = c + 1;
        while (k < body.length() && Character.isWhitespace(body.charAt(k))) k++;
        StringBuilder b = new StringBuilder();
        for (; k < body.length(); k++) {
            char ch = body.charAt(k);
            if (ch >= '0' && ch <= '9') b.append(ch);
            else break;
        }
        if (b.length() == 0) return -1;
        try { return Integer.parseInt(b.toString()); } catch (Exception e) { return -1; }
    }

    /** 缺失返回 -1，true/1 返回 1，false/0 返回 0。 */
    private static int jsonBool(String body, String key) {
        if (body == null) return -1;
        int i = body.indexOf("\"" + key + "\"");
        if (i < 0) return -1;
        int c = body.indexOf(':', i);
        if (c < 0) return -1;
        int k = c + 1;
        while (k < body.length() && Character.isWhitespace(body.charAt(k))) k++;
        if (body.startsWith("true", k)) return 1;
        if (body.startsWith("false", k)) return 0;
        if (k < body.length() && body.charAt(k) == '1') return 1;
        if (k < body.length() && body.charAt(k) == '0') return 0;
        return -1;
    }

    public static final class Result {
        public final int code;
        public final String json;
        Result(int code, String json) { this.code = code; this.json = json; }
    }
}
