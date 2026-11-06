package com.jager.tvquiz;

/**
 * 跨题「连续答对 N 题才解锁」策略：纯 Java，零 Android 依赖，可 JVM 单测。
 *
 * QuizFlow 是单题状态机（bind 时 reset），连对进度必须放在跨题层。
 * 默认 required=1：第一次答对即返回 true，与 v1.0.2 的行为逐字等价。
 * 家长可在设置里把门槛调到 2–5；答错则进度清零。
 */
public final class UnlockPolicy {

    public static final int MIN_STREAK = 1;
    public static final int MAX_STREAK = 5;
    public static final int DEFAULT_STREAK = 1;

    private final int required;
    private int streak = 0;

    public UnlockPolicy(int required) { this.required = clamp(required); }

    public static int clamp(int n) {
        if (n < MIN_STREAK) return MIN_STREAK;
        if (n > MAX_STREAK) return MAX_STREAK;
        return n;
    }

    public void reset() { streak = 0; }

    /** 答对一题；返回 true 表示已达到门槛、可以解锁。 */
    public boolean onCorrect() {
        streak++;
        return streak >= required;
    }

    /** 答错一题：连对进度清零，需要从头再答 N 题。 */
    public void onWrong() { streak = 0; }

    public int streak() { return streak; }
    public int required() { return required; }

    /** 未达门槛时显示给孩子的进度文案。 */
    public String progress() {
        return "✓ 答对 " + streak + "/" + required + " 题，再来一题";
    }
}
