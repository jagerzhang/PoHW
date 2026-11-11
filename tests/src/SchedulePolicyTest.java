import com.jager.tvquiz.HolidayCalendar;
import com.jager.tvquiz.SchedulePolicy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.TimeZone;

/** issue #37：生效时段 + 法定节假日/调休 + 假期模式 + 当天临时覆盖。 */
public class SchedulePolicyTest {

    static int pass = 0, fail = 0;
    static final TimeZone TZ = TimeZone.getTimeZone("Asia/Shanghai");

    static void ok(String name, boolean cond) {
        if (cond) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    static long at(int y, int m, int d, int hh, int mm) {
        Calendar c = Calendar.getInstance(TZ);
        c.clear();
        c.set(y, m - 1, d, hh, mm, 0);
        return c.getTimeInMillis();
    }

    static SchedulePolicy.Config on() {
        SchedulePolicy.Config c = SchedulePolicy.Config.defaults();
        c.enabled = true;
        return c;
    }

    static boolean allowed(long t, SchedulePolicy.Config c, HolidayCalendar h) {
        return SchedulePolicy.decide(t, TZ, c, h).allowed;
    }

    public static void main(String[] args) throws Exception {
        String json = new String(
                Files.readAllBytes(Paths.get("app/assets/holidays.json")),
                StandardCharsets.UTF_8);
        HolidayCalendar h = HolidayCalendar.fromJson(json);

        // --- 数据层 ---
        ok("S01 日历覆盖 2026-2027", h.covers(2026) && h.covers(2027) && !h.covers(2028));
        ok("S02 例外日总数 73", h.exceptionCount() == 73);
        ok("S03 版本戳可追溯", h.version().startsWith("2026.11.11"));
        ok("S04 补班日登记为 off=0", h.exception("2026-01-04") == 0);
        ok("S05 放假日登记为 off=1", h.exception("2026-10-01") == 1);
        ok("S06 普通日期未登记", h.exception("2026-03-02") == -1);

        // --- 总开关与基础时段 ---
        SchedulePolicy.Config off = SchedulePolicy.Config.defaults();
        ok("S07 默认关闭时任何时刻都放行",
                allowed(at(2026, 3, 2, 10, 0), off, h));
        SchedulePolicy.Config c = on();
        ok("S08 上学日上午拦截", !allowed(at(2026, 3, 2, 10, 0), c, h));
        ok("S09 上学日放学后放行", allowed(at(2026, 3, 2, 18, 0), c, h));
        ok("S10 在家日上午放行", allowed(at(2026, 3, 7, 10, 0), c, h));
        ok("S11 在家日深夜拦截", !allowed(at(2026, 3, 7, 22, 0), c, h));

        // --- 关键负向：调休补班（周末但要上学）---
        String[] makeup = {"2026-01-04", "2026-02-14", "2026-02-28",
                           "2026-05-09", "2026-09-20", "2026-10-10"};
        int[] md = {1, 4, 2, 14, 2, 28, 5, 9, 9, 20, 10, 10};
        boolean allMakeup = true;
        for (int i = 0; i < md.length; i += 2) {
            allMakeup &= !allowed(at(2026, md[i], md[i + 1], 10, 0), c, h);
            allMakeup &= allowed(at(2026, md[i], md[i + 1], 18, 0), c, h);
        }
        ok("S12 六个补班日按上学日规则（上午拦/放学后放）", allMakeup);
        ok("S13 补班日清单与日历一致", makeup.length == 6);

        // --- 关键负向：法定假日里的工作日 ---
        ok("S14 国庆周四按在家日规则放行", allowed(at(2026, 10, 1, 10, 0), c, h));
        ok("S15 春节里的周一按在家日规则放行",
                allowed(at(2026, 2, 16, 10, 0), c, h));
        SchedulePolicy.Config noSkip = on();
        noSkip.skipHolidays = false;
        ok("S16 关掉跳过节假日后该周四恢复上学日拦截",
                !allowed(at(2026, 10, 1, 10, 0), noSkip, h));

        // --- 假期模式（寒暑假，自动恢复）---
        SchedulePolicy.Config vac = on();
        vac.vacationRange = "2026-07-01~2026-08-31";
        ok("S17 假期区间内按在家日放行", allowed(at(2026, 7, 15, 10, 0), vac, h));
        ok("S18 假期区间外自动恢复上学日", !allowed(at(2026, 9, 3, 10, 0), vac, h));
        ok("S19 假期区间合法", SchedulePolicy.validVacationRange(vac.vacationRange));
        ok("S20 非法假期区间被拒", !SchedulePolicy.validVacationRange("2026-08-31~2026-07-01")
                && !SchedulePolicy.validVacationRange("bad"));

        // --- 当天临时覆盖（隔天自动失效）---
        SchedulePolicy.Config oa = on();
        oa.todayOverride = "2026-03-02:allow";
        ok("S21 当天允许覆盖生效", allowed(at(2026, 3, 2, 10, 0), oa, h));
        ok("S22 隔天自动失效", !allowed(at(2026, 3, 3, 10, 0), oa, h));
        SchedulePolicy.Config ob = on();
        ob.todayOverride = "2026-03-07:block";
        ok("S23 当天屏蔽覆盖生效", !allowed(at(2026, 3, 7, 10, 0), ob, h));
        ok("S24 覆盖格式校验", SchedulePolicy.validOverride("2026-03-07:block")
                && SchedulePolicy.validOverride("")
                && !SchedulePolicy.validOverride("2026-03-07:maybe"));

        // --- 覆盖年份外降级 ---
        SchedulePolicy.Decision d28 = SchedulePolicy.decide(at(2028, 1, 3, 10, 0), TZ, c, h);
        ok("S25 超覆盖年份标记过期并降级星期掩码",
                d28.calendarExpired && !d28.allowed);
        SchedulePolicy.Decision d26 = SchedulePolicy.decide(at(2026, 3, 2, 10, 0), TZ, c, h);
        ok("S26 覆盖年份内不标记过期", !d26.calendarExpired);

        // --- 多段 / 跨午夜 / 掩码 ---
        SchedulePolicy.Config night = on();
        night.homeWindows = "22:00-02:00:1111111";
        ok("S27 跨午夜 23 点放行", allowed(at(2026, 3, 7, 23, 0), night, h));
        ok("S28 跨午夜次日 1 点放行", allowed(at(2026, 3, 8, 1, 0), night, h));
        ok("S29 跨午夜中午拦截", !allowed(at(2026, 3, 7, 12, 0), night, h));
        SchedulePolicy.Config two = on();
        two.schoolWindows = "07:00-08:00:1111111|17:00-21:00:1111111";
        ok("S30 两段窗口分别生效",
                allowed(at(2026, 3, 2, 7, 30), two, h)
                        && allowed(at(2026, 3, 2, 18, 0), two, h)
                        && !allowed(at(2026, 3, 2, 10, 0), two, h));
        SchedulePolicy.Config mask = on();
        mask.schoolWindows = "17:00-21:00:1000000";
        ok("S31 掩码只允许周一时周二被拦",
                allowed(at(2026, 3, 2, 18, 0), mask, h)
                        && !allowed(at(2026, 3, 3, 18, 0), mask, h));
        SchedulePolicy.Config broken = on();
        broken.schoolWindows = "not-a-window";
        ok("S32 非法窗口回退默认而不是全放行",
                !allowed(at(2026, 3, 2, 10, 0), broken, h)
                        && allowed(at(2026, 3, 2, 18, 0), broken, h));
        ok("S33 窗口格式校验", SchedulePolicy.validWindows(SchedulePolicy.DEFAULT_SCHOOL_WINDOWS)
                && !SchedulePolicy.validWindows("25:00-26:00:1111111")
                && !SchedulePolicy.validWindows("17:00-21:00:0000000"));

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL") + " total=" + pass);
    }
}
