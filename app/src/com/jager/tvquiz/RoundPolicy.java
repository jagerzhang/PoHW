package com.jager.tvquiz;

/**
 * v1.3.0 未发布：按组答题策略。一组 N 题，答对比例达标才算通过。
 *
 * <p>纯 Java、零 Android 依赖，可在 tests/run.sh 里直接 JVM 单测。
 * 默认 target=5 / passPercent=60（与家长设置默认值一致）。
 * 策略对象由 QuizOverlayService 持有（跨题），QuizFlow.bind() 每题 reset 不影响它。
 */
public final class RoundPolicy {

    public static final int MIN_TARGET = 1;
    public static final int MAX_TARGET = 20;
    public static final int DEFAULT_TARGET = 5;
    public static final int DEFAULT_PASS_PERCENT = 60;

    public static final int CONTINUE = 0;
    public static final int PASS = 1;
    public static final int FAIL = 2;

    private final int target;
    private final int passPercent;
    private int answered = 0;
    private int correct = 0;

    public RoundPolicy(int target, int passPercent) {
        this.target = clampTarget(target);
        this.passPercent = clampPercent(passPercent);
    }

    public static int clampTarget(int n) {
        return n < MIN_TARGET ? MIN_TARGET : (n > MAX_TARGET ? MAX_TARGET : n);
    }

    public static int clampPercent(int p) {
        return p < 1 ? 1 : (p > 100 ? 100 : p);
    }

    /** 需答对题数，整数向上取整，避免浮点比较。5 题 x 60% = 3 题。 */
    public int minCorrect() {
        return (target * passPercent + 99) / 100;
    }

    /**
     * 提交一道题的最终结果（同题可重试时不调用）。
     * CONTINUE=继续出下一题；PASS=本组达标可解锁；FAIL=题量用尽未达标，需重开一组。
     */
    public int onAnswer(boolean isCorrect) {
        answered++;
        if (isCorrect) correct++;
        if (answered < target) return CONTINUE;
        return correct >= minCorrect() ? PASS : FAIL;
    }

    /** 换题时给孩子看的进度文案。answered 已包含刚提交的一题。 */
    public String progress() {
        return "第 " + Math.min(answered + 1, target) + "/" + target
                + " 题 · 已答对 " + correct + " 题（需 " + minCorrect() + " 题）";
    }

    /** 本组结算文案。 */
    public String result(boolean passed) {
        return passed ? "✓ 本组通过：" + correct + "/" + target + " 题答对"
                      : "✗ 本组未达标：" + correct + "/" + target
                        + " 题，需答对 " + minCorrect() + " 题，再来一组";
    }

    public void reset() { answered = 0; correct = 0; }

    public int answered() { return answered; }
    public int correct() { return correct; }
    public int target() { return target; }
    public int passPercent() { return passPercent; }
}
