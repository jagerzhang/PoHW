package com.jager.tvquiz;

/**
 * 练习模式的参数与统计：纯 Java，无 Android 依赖，便于 JVM 单测。
 *
 * 与守护设置完全隔离：这里改学科 / 年级 / 难度不会影响家长设置的出题参数。
 * 混合模式按已答题数轮转三科，保证每科都练得到。
 */
public final class PracticeConfig {

    public static final int MATH = 0;
    public static final int CHINESE = 1;
    public static final int ENGLISH = 2;
    public static final int MIXED = 3;
    public static final int MIN_GRADE = 1;
    /** 内置题库只覆盖小学 1–6 年级，练习模式不再提供会错配标签的 7–9 年级。 */
    public static final int MAX_GRADE = 6;
    public static final int DIFF_ALL = 0;
    public static final int DIFF_MAX = 3;

    private int subject = MIXED;
    private int grade = 3;
    private int difficulty = DIFF_ALL;
    private int asked = 0;
    private int correct = 0;
    private int streak = 0;

    public int subject() { return subject; }
    public int grade() { return grade; }
    public int difficulty() { return difficulty; }
    public int asked() { return asked; }
    public int correct() { return correct; }
    public int streak() { return streak; }

    public void cycleSubject() { subject = (subject + 1) % 4; }
    public void cycleGrade() { grade = grade % MAX_GRADE + MIN_GRADE; }
    public void cycleDifficulty() { difficulty = (difficulty + 1) % (DIFF_MAX + 1); }

    /** 本次抽题的学科：混合模式按题序轮转三科。 */
    public int subjectForDraw() {
        return subject == MIXED ? (asked % 3) : subject;
    }

    public void onAsked() { asked++; }
    public void onCorrect() { correct++; streak++; }
    public void onWrong() { streak = 0; }

    public String subjectLabel() {
        return subject == MIXED ? "混合" : Subjects.name(subject);
    }

    public String difficultyLabel() {
        return new String[]{"不限", "低 · 偏易", "中", "高 · 偏难"}[difficulty];
    }

    public String summary() {
        return "已练 " + asked + " · 正确 " + correct + " · 连对 " + streak;
    }
}
