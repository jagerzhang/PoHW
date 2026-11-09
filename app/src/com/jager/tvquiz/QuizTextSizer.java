package com.jager.tvquiz;

public final class QuizTextSizer {

    private QuizTextSizer() {}

    public static int sp(String s) {
        int n = s == null ? 0 : s.length();
        if (n > 72) return 19;
        if (n > 48) return 22;
        if (n > 28) return 25;
        if (n > 16) return 28;
        return 30;
    }

    /**
     * v1.3.0 未发布：四个选项统一字号，按最长的一项分档。
     *
     * <p>真机截图反馈：选项框固定高度、24sp 写死时，两行以上长文本的第二行
     * 会被裁掉下半截。字号按最长选项整体缩小，保证四选项视觉一致且都能放下。
     */
    public static int optionSp(String[] options) {
        int max = 0;
        if (options != null) {
            for (String s : options) {
                if (s != null) max = Math.max(max, s.length());
            }
        }
        if (max > 44) return 18;
        if (max > 32) return 20;
        if (max > 20) return 22;
        return 24;
    }

    /** 选项网格内单个按钮的可用文字高度（dp），用于 JVM 回归估算。 */
    public static int optionTextHeightDp(int gridDp, int marginDp, int paddingDp) {
        return (gridDp - marginDp * 4) / 2 - paddingDp * 2;
    }

    /** sp 字号下 lines 行文字所需高度（dp，1.15 行距），向上取整。 */
    public static int optionNeedHeightDp(int sp, int lines) {
        return (int) Math.ceil(sp * 1.15f * lines);
    }

    /** 给定网格高度与字号，lines 行文字是否放得下。纯计算，无 Android 依赖。 */
    public static boolean optionFits(int gridDp, int marginDp, int paddingDp,
                                     int sp, int lines) {
        return optionNeedHeightDp(sp, lines) <= optionTextHeightDp(gridDp, marginDp, paddingDp);
    }
}
