package com.jager.tvquiz;

/** 题目难度倾向：站点系数噪声大，只做 80/20 加权，不做硬过滤。纯 Java，可 JVM 单测。 */
public final class DifficultyPolicy {
    public static final int LEVEL_ALL = 0;
    public static final int LEVEL_LOW = 1;
    public static final int LEVEL_MID = 2;
    public static final int LEVEL_HIGH = 3;
    public static final int MATCH_RATE = 80;

    public static int clamp(int v) { return (v < 0 || v > 3) ? LEVEL_ALL : v; }

    /** roll 为 0..99；默认不限时永远返回 false，行为与旧版逐字一致。 */
    public static boolean useLevel(int level, int roll) {
        return clamp(level) != LEVEL_ALL && roll >= 0 && roll < MATCH_RATE;
    }

    /** 追加到已有 WHERE 之后；所有兜底层都必须带上，避免回退到无约束全库。 */
    public static String predicate(int level) {
        int v = clamp(level);
        return v == LEVEL_ALL ? "" : " AND difficulty_level=" + v;
    }

    /** 最后一层无条件查询时用。 */
    public static String rootPredicate(int level) {
        int v = clamp(level);
        return v == LEVEL_ALL ? "" : "WHERE difficulty_level=" + v;
    }
}
