package com.jager.tvquiz;

/**
 * 答错递增冷却（家长可选，默认关闭）：纯 Java 计算，时间戳由调用方传入便于单测。
 *
 * 设计要点：
 *  - 存「截止时间戳」而不是「剩余秒数」，重启后仍可恢复；
 *  - 冷却只延迟「换题」，不拦截「答对」——答对通道始终开放；
 *  - 墙钟时间可被改系统时间绕过，elapsedRealtime 重启后归零，所以两者结合：
 *    检测到时钟回拨或重启时，按当前档位重新计一次冷却，既不永久锁死也不放行。
 */
public final class CooldownPolicy {

    /** 第 1/2/3/4 次及以后答错的冷却秒数。 */
    public static final int[] STEPS_SEC = {30, 60, 120, 300};

    private int wrongCount = 0;
    private long deadline = 0L;

    public static int secondsFor(int wrongCount) {
        if (wrongCount <= 0) return 0;
        int i = wrongCount - 1;
        if (i >= STEPS_SEC.length) i = STEPS_SEC.length - 1;
        return STEPS_SEC[i];
    }

    public void reset() {
        wrongCount = 0;
        deadline = 0L;
    }

    /** 答错：档位递增，返回新的冷却截止时间戳（墙钟毫秒）。 */
    public long onWrong(long nowMillis) {
        wrongCount++;
        deadline = nowMillis + secondsFor(wrongCount) * 1000L;
        return deadline;
    }

    public long deadline() { return deadline; }
    public int wrongCount() { return wrongCount; }

    public int remainingSec(long nowMillis) {
        if (deadline <= nowMillis) return 0;
        return (int) ((deadline - nowMillis + 999L) / 1000L);
    }

    public boolean isActive(long nowMillis) { return deadline > nowMillis; }

    /**
     * 从持久化状态恢复剩余冷却秒数。
     *
     * @param deadline    保存的墙钟截止时间
     * @param nowWall     当前墙钟时间
     * @param savedWall   写入 deadline 时的墙钟时间
     * @param savedElapsed 写入时的 elapsedRealtime
     * @param nowElapsed  当前 elapsedRealtime
     * @param wrongCount  已累计答错次数（用于按档位封顶 / 重计）
     */
    public static int safeRemaining(long deadline, long nowWall, long savedWall,
                                    long savedElapsed, long nowElapsed, int wrongCount) {
        if (deadline <= 0L || nowWall <= 0L) return 0;
        boolean rollback = savedWall > 0L && nowWall < savedWall;
        boolean reboot = savedElapsed > 0L && nowElapsed < savedElapsed;
        if (rollback || reboot) return secondsFor(wrongCount);
        long ms = deadline - nowWall;
        if (ms <= 0L) return 0;
        int cap = secondsFor(wrongCount);
        int sec = (int) ((ms + 999L) / 1000L);
        return sec > cap ? cap : sec;
    }
}
