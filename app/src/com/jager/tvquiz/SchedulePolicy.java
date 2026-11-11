package com.jager.tvquiz;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * 生效时段与日期类型门禁（纯 Java，零 android 依赖）。
 *
 * <p>issue #37：家长希望「上学的时候不触发答题模式」。判定顺序固定为：
 *
 * <ol>
 *   <li>总开关关闭 -> 永远放行（覆盖安装旧版本行为不变）；</li>
 *   <li>当天临时覆盖（日期不是今天自动失效）；</li>
 *   <li>假期模式区间命中（寒暑假，到期自动恢复，不用家长手动关）；</li>
 *   <li>日期类型：跳过节假日开关打开时查 HolidayCalendar（含调休补班），
 *       关闭或数据超出覆盖年份时退回星期掩码；</li>
 *   <li>按日期类型选「上学日 / 在家日」两组时段，组内最多两段，
 *       每段带 7 位星期掩码，起止时间支持跨午夜。</li>
 * </ol>
 *
 * <p>刻意不用「按星期几直接定时段」：调休补班日是周六却要上学，
 * 法定假日是周三却不上学，星期的语义会被调休打乱。先定日期类型、
 * 再套该类型的时段，才能自动正确处理这两类日子。掩码只用于家长对
 * 同一日期类型内部做进一步细分，默认七位全开。
 */
public final class SchedulePolicy {

    public static final String DEFAULT_SCHOOL_WINDOWS = "17:00-21:00:1111111";
    public static final String DEFAULT_HOME_WINDOWS = "09:00-21:00:1111111";
    public static final int MAX_WINDOWS = 2;

    private SchedulePolicy() { }

    /** 与 SettingsStore 逐字段对应，便于 JVM 单测直接构造。 */
    public static final class Config {
        public boolean enabled;
        public boolean skipHolidays;
        public String schoolWindows = DEFAULT_SCHOOL_WINDOWS;
        public String homeWindows = DEFAULT_HOME_WINDOWS;
        public String vacationRange = "";
        public String todayOverride = "";

        public static Config defaults() {
            Config c = new Config();
            c.enabled = false;          // 默认关：老用户覆盖安装行为完全不变
            c.skipHolidays = true;      // 默认智能跳过法定节假日与调休
            return c;
        }
    }

    public static final class Decision {
        public final boolean allowed;
        public final String reason;
        public final int dayType;
        /** 节假日表超出覆盖年份：已降级到星期掩码，界面应提示升级。 */
        public final boolean calendarExpired;

        Decision(boolean allowed, String reason, int dayType, boolean expired) {
            this.allowed = allowed;
            this.reason = reason;
            this.dayType = dayType;
            this.calendarExpired = expired;
        }
    }

    /** 单个时段：起止分钟（0-1439）+ 7 位星期掩码（周一为 bit0）。 */
    public static final class Window {
        public final int start;
        public final int end;
        public final int mask;
        public final String raw;

        Window(int start, int end, int mask, String raw) {
            this.start = start;
            this.end = end;
            this.mask = mask;
            this.raw = raw;
        }

        /** start > end 表示跨午夜，例如 22:00-02:00。 */
        public boolean contains(int minute) {
            return start <= end
                    ? (minute >= start && minute <= end)
                    : (minute >= start || minute <= end);
        }

        public boolean allows(int dayMaskBit) {
            return (mask & dayMaskBit) != 0;
        }
    }

    /** 主入口。cal 为 null 或不可用时按星期掩码降级，绝不抛异常打断守护。 */
    public static Decision decide(long now, TimeZone tz, Config cfg,
                                  HolidayCalendar cal) {
        Calendar c = Calendar.getInstance(tz == null ? TimeZone.getDefault() : tz);
        c.setTimeInMillis(now);
        String today = HolidayCalendar.dateKey(c);

        if (cfg == null || !cfg.enabled) {
            return new Decision(true, "schedule disabled", HolidayCalendar.DAY_UNKNOWN, false);
        }

        // 1. 当天临时覆盖：只认今天，隔天自动失效。
        String ov = cfg.todayOverride == null ? "" : cfg.todayOverride.trim();
        if (ov.equals(today + ":allow")) {
            return new Decision(true, "today override allow", HolidayCalendar.DAY_UNKNOWN, false);
        }
        if (ov.equals(today + ":block")) {
            return new Decision(false, "today override block", HolidayCalendar.DAY_UNKNOWN, false);
        }

        // 2. 假期模式：命中区间即视为在家日；区间外（含过期）自动恢复。
        boolean vacation = inVacationRange(today, cfg.vacationRange);

        // 3. 日期类型。
        boolean expired = false;
        int dayType;
        if (vacation) {
            dayType = HolidayCalendar.DAY_HOME;
        } else if (cfg.skipHolidays && cal != null && cal.usable()) {
            dayType = cal.dayType(c);
            if (dayType == HolidayCalendar.DAY_UNKNOWN) {
                expired = true;
                dayType = HolidayCalendar.weekdayType(c);
            }
        } else {
            dayType = HolidayCalendar.weekdayType(c);
            if (cfg.skipHolidays && (cal == null || !cal.usable())) expired = true;
        }

        // 4. 该日期类型的时段表。
        String raw = (dayType == HolidayCalendar.DAY_HOME)
                ? cfg.homeWindows : cfg.schoolWindows;
        List<Window> windows = parseWindows(raw);
        if (windows.isEmpty()) {
            // 配置被写坏时不静默全放行，也不全屏蔽：退回该类型默认表。
            windows = parseWindows(dayType == HolidayCalendar.DAY_HOME
                    ? DEFAULT_HOME_WINDOWS : DEFAULT_SCHOOL_WINDOWS);
        }
        int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int bit = maskBit(c);
        for (Window w : windows) {
            if (w.allows(bit) && w.contains(minute)) {
                return new Decision(true, vacation ? "vacation window" : "in window",
                        dayType, expired);
            }
        }
        return new Decision(false, "outside active window", dayType, expired);
    }

    /** Calendar.DAY_OF_WEEK -> 掩码位，周一 bit0 ... 周日 bit6。 */
    public static int maskBit(Calendar c) {
        switch (c.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:    return 1;
            case Calendar.TUESDAY:   return 2;
            case Calendar.WEDNESDAY: return 4;
            case Calendar.THURSDAY:  return 8;
            case Calendar.FRIDAY:    return 16;
            case Calendar.SATURDAY:  return 32;
            case Calendar.SUNDAY:    return 64;
            default:                 return 0;
        }
    }

    /**
     * 严格解析 "HH:mm-HH:mm:1111111|HH:mm-HH:mm:1111111"。
     * 任何非法片段直接返回空表，由 decide() 回退默认值。
     */
    public static List<Window> parseWindows(String raw) {
        List<Window> out = new ArrayList<Window>();
        if (raw == null || raw.trim().length() == 0) return out;
        String[] parts = raw.trim().split("\\|", -1);
        if (parts.length > MAX_WINDOWS) return out;
        for (String part : parts) {
            if (part.length() == 0) return out;
            // 格式 HH:mm-HH:mm:mask —— 时间里也有冒号，必须从最后一个冒号切，
            // 否则 split(":") 会得到 ["17","00-21","00","1111111"] 四段。
            int last = part.lastIndexOf(':');
            if (last < 0) return out;
            String maskStr = part.substring(last + 1);
            if (maskStr.length() != 7) return out;
            String[] times = part.substring(0, last).split("-", -1);
            if (times.length != 2) return out;
            int a = parseMinute(times[0]);
            int b = parseMinute(times[1]);
            if (a < 0 || b < 0 || a == b) return out;
            int mask = 0;
            for (int i = 0; i < 7; i++) {
                char ch = maskStr.charAt(i);
                if (ch == '1') mask |= (1 << i);
                else if (ch != '0') return out;
            }
            if (mask == 0) return out;
            out.add(new Window(a, b, mask, part));
        }
        return out;
    }

    private static int parseMinute(String s) {
        if (s == null || s.length() != 5 || s.charAt(2) != ':') return -1;
        for (int i = 0; i < 5; i++) {
            if (i == 2) continue;
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return -1;
        }
        int h = Integer.parseInt(s.substring(0, 2));
        int m = Integer.parseInt(s.substring(3, 5));
        if (h > 23 || m > 59) return -1;
        return h * 60 + m;
    }

    public static boolean validWindows(String raw) {
        return !parseWindows(raw).isEmpty();
    }

    /** 假期模式："YYYY-MM-DD~YYYY-MM-DD"，空串表示关闭，起止须合法且有序。 */
    public static boolean validVacationRange(String s) {
        if (s == null || s.trim().length() == 0) return true;
        String v = s.trim();
        if (v.length() != 21 || v.charAt(10) != '~') return false;
        String a = v.substring(0, 10);
        String b = v.substring(11);
        return validDate(a) && validDate(b) && a.compareTo(b) <= 0;
    }

    public static boolean inVacationRange(String today, String range) {
        if (range == null || range.trim().length() == 0) return false;
        String v = range.trim();
        if (v.length() != 21 || v.charAt(10) != '~') return false;
        return today.compareTo(v.substring(0, 10)) >= 0
                && today.compareTo(v.substring(11)) <= 0;
    }

    /** 当天覆盖："YYYY-MM-DD:allow" / "YYYY-MM-DD:block"，空串表示无。 */
    public static boolean validOverride(String s) {
        if (s == null || s.trim().length() == 0) return true;
        String v = s.trim();
        if (!(v.endsWith(":allow") || v.endsWith(":block"))) return false;
        int colon = v.indexOf(':');
        return colon == 10 && validDate(v.substring(0, 10));
    }

    private static boolean validDate(String s) {
        if (s == null || s.length() != 10 || s.charAt(4) != '-' || s.charAt(7) != '-') {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (i == 4 || i == 7) continue;
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        }
        int m = Integer.parseInt(s.substring(5, 7));
        int d = Integer.parseInt(s.substring(8, 10));
        return m >= 1 && m <= 12 && d >= 1 && d <= 31;
    }
}
