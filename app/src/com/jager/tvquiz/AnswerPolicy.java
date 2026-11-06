package com.jager.tvquiz;

/**
 * 答题判定与「答错 N 次换题」策略：单一规则源，零 Android 依赖。
 * 需求：同一道题累计答错 2 次就换一道新题，原题记入错题库。
 */
public final class AnswerPolicy {

    private AnswerPolicy() {}

    public static final int WRONG_LIMIT = 2;

    public static boolean isCorrect(int picked, int answerIndex) {
        return picked >= 0 && picked < 4 && answerIndex >= 0 && answerIndex < 4
                && picked == answerIndex;
    }

    /** wrongAttempts 是本题已累计的答错次数（含本次）。 */
    public static boolean shouldSwap(int wrongAttempts) {
        return shouldSwap(wrongAttempts, WRONG_LIMIT);
    }

    /** 严格模式下每题只给 1 次机会：limit=1。默认 limit=WRONG_LIMIT=2。 */
    public static boolean shouldSwap(int wrongAttempts, int limit) {
        return wrongAttempts >= (limit < 1 ? 1 : limit);
    }

    public static String wrongHint(int wrongAttempts) {
        return wrongHint(wrongAttempts, WRONG_LIMIT);
    }

    public static String wrongHint(int wrongAttempts, int limit) {
        int lim = limit < 1 ? 1 : limit;
        int left = lim - wrongAttempts;
        if (left <= 0) {
            return "✗ 已答错 " + lim + " 次，换一题（原题已记入错题库）";
        }
        return "✗ 再想想 · 本题第 " + wrongAttempts + " 次答错，再错 " + left + " 次就换题";
    }
}
