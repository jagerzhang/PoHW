package com.jager.tvquiz;

/**
 * 出题时钟：以 elapsedRealtime 的绝对截止时刻为准，而不是「每秒递减的计数器」。
 *
 * <p>电视「按关机」多为浅待机：CPU 可能被厂商冻结，Handler 的 postDelayed 基于
 * uptimeMillis，冻结期间不走；进程被杀后内存计数直接归零，服务重启又会被重置成
 * 整个 interval，家长观察到的就是「再也不弹题」。elapsedRealtime 在休眠期间照常
 * 推进，deadline 持久化后即使进程死亡也能算出「早就该弹了」。
 *
 * <p>纯 Java，不依赖 android.*，可由 JVM 单测覆盖。
 */
public final class WakeClock {

    private WakeClock() {}

    /** 从 nowElapsed 起 seconds 秒后的截止时刻。 */
    public static long deadline(long nowElapsed, int seconds) {
        if (seconds < 0) seconds = 0;
        return nowElapsed + (long) seconds * 1000L;
    }

    /** 剩余秒数（向上取整）；无存档或已到点返回 0。 */
    public static int remainingSec(long nowElapsed, long deadline) {
        if (deadline <= 0L) return 0;
        long ms = deadline - nowElapsed;
        if (ms <= 0L) return 0;
        return (int) ((ms + 999L) / 1000L);
    }

    public static boolean due(long nowElapsed, long deadline) {
        return deadline > 0L && nowElapsed >= deadline;
    }

    /**
     * 进程重启 / 浅待机唤醒后恢复剩余秒数。
     *
     * @param deadline      上次存档的 elapsedRealtime 截止时刻，0 表示首次开启
     * @param savedWall     存档时的墙钟快照
     * @param savedElapsed  存档时的 elapsedRealtime 快照（冷开机会归零）
     * @param fallbackSec   无存档 / 冷重启 / 墙钟回拨时的正常间隔
     * @return 剩余秒数；0 表示已过期，应立刻补弹
     */
    public static int restoreRemaining(long deadline, long savedWall, long savedElapsed,
                                       long nowWall, long nowElapsed, int fallbackSec) {
        if (fallbackSec < 0) fallbackSec = 0;
        if (deadline <= 0L) return fallbackSec;
        boolean reboot = savedElapsed > 0L && nowElapsed < savedElapsed;
        boolean rollback = savedWall > 0L && nowWall < savedWall;
        if (reboot || rollback) return fallbackSec;
        long ms = deadline - nowElapsed;
        if (ms <= 0L) return 0;
        int sec = (int) ((ms + 999L) / 1000L);
        return sec > fallbackSec ? fallbackSec : sec;
    }
}
