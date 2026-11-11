package com.jager.tvquiz;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置节假日日历（纯 Java，零 android 依赖，可在 JVM 单测里直接跑）。
 *
 * <p>assets/holidays.json 由 tools/holidays/fetch_holidays.py 从
 * NateScarlet/holiday-cn（MIT）生成，其原始依据是国务院办公厅每年发布的
 * 《关于 XXXX 年部分节假日安排的通知》。表里**只有例外日**：法定放假日
 * （off=1）与调休补班日（off=0）。与周末连休的普通周末不入库，由本类
 * 按星期推算。
 *
 * <p>为什么不能用「周末 = 不上学」：以 2026 年为例，1/4、2/14、2/28、
 * 5/9、9/20、10/10 都是周末但要上学；反过来春节、国庆里的周一至周五
 * 又放假。这张表就是用来修正这些例外的。
 *
 * <p>数据只覆盖 years 声明的年份，超出范围返回 DAY_UNKNOWN，调用方必须
 * 降级到星期掩码并在界面提示升级，绝不静默按普通星期算。
 */
public final class HolidayCalendar {

    public static final int DAY_SCHOOL = 0;
    public static final int DAY_HOME = 1;
    public static final int DAY_UNKNOWN = 2;

    private final String version;
    private final int minYear;
    private final int maxYear;
    /** "YYYY-MM-DD" -> 1 放假 / 0 调休补班。 */
    private final Map<String, Integer> exceptions;

    private HolidayCalendar(String version, int minYear, int maxYear,
                            Map<String, Integer> exceptions) {
        this.version = version;
        this.minYear = minYear;
        this.maxYear = maxYear;
        this.exceptions = exceptions;
    }

    /** 表缺失或 JSON 损坏时的降级对象：不覆盖任何年份，一律交给星期掩码。 */
    public static HolidayCalendar empty() {
        return new HolidayCalendar("", Integer.MAX_VALUE, Integer.MIN_VALUE,
                new HashMap<String, Integer>());
    }

    @SuppressWarnings("unchecked")
    public static HolidayCalendar fromJson(String json) {
        Map<String, Object> top = (Map<String, Object>) Json.parse(json);
        String version = String.valueOf(top.get("version"));
        List<Object> years = (List<Object>) top.get("years");
        if (years == null || years.isEmpty()) {
            throw new IllegalArgumentException("holidays.json has no coverage years");
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Object o : years) {
            int y = ((Number) o).intValue();
            if (y < min) min = y;
            if (y > max) max = y;
        }
        Map<String, Integer> ex = new HashMap<String, Integer>();
        List<Object> days = (List<Object>) top.get("days");
        if (days == null) {
            throw new IllegalArgumentException("holidays.json has no days");
        }
        for (Object o : days) {
            Map<String, Object> row = (Map<String, Object>) o;
            String date = String.valueOf(row.get("d"));
            if (date.length() != 10 || date.charAt(4) != '-' || date.charAt(7) != '-') {
                throw new IllegalArgumentException("bad holiday date: " + date);
            }
            int off = ((Number) row.get("off")).intValue();
            if (off != 0 && off != 1) {
                throw new IllegalArgumentException("bad holiday off flag: " + off);
            }
            if (ex.containsKey(date)) {
                throw new IllegalArgumentException("duplicate holiday date: " + date);
            }
            ex.put(date, Integer.valueOf(off));
        }
        return new HolidayCalendar(version, min, max, ex);
    }

    public String version() { return version; }
    public int firstCoveredYear() { return minYear; }
    public int lastCoveredYear() { return maxYear; }
    public int exceptionCount() { return exceptions.size(); }
    public boolean covers(int year) { return year >= minYear && year <= maxYear; }
    public boolean usable() { return minYear <= maxYear && !exceptions.isEmpty(); }

    /** 例外日表里的登记值；未登记返回 -1。 */
    public int exception(String date) {
        Integer v = exceptions.get(date);
        return v == null ? -1 : v.intValue();
    }

    /** 完整日期类型判定：例外日优先，未命中按星期，超覆盖返回 UNKNOWN。 */
    public int dayType(Calendar c) {
        int year = c.get(Calendar.YEAR);
        if (!covers(year)) return DAY_UNKNOWN;
        int e = exception(dateKey(c));
        if (e == 1) return DAY_HOME;
        if (e == 0) return DAY_SCHOOL;
        return weekdayType(c);
    }

    /** 纯星期兜底：周六日在家里，周一至周五上学。 */
    public static int weekdayType(Calendar c) {
        int dow = c.get(Calendar.DAY_OF_WEEK);
        return (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)
                ? DAY_HOME : DAY_SCHOOL;
    }

    /** 不依赖 SimpleDateFormat：手工补零，API 1 可用且无 Locale/线程问题。 */
    public static String dateKey(Calendar c) {
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        return y + "-" + (m < 10 ? "0" : "") + m
                + "-" + (d < 10 ? "0" : "") + d;
    }
}
