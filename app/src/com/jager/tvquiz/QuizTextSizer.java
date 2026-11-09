package com.jager.tvquiz;

public final class QuizTextSizer {

    /** 遥控器四选一卡片能完整显示的最长选项字数；超过则在抽题时跳过。 */
    public static final int TV_OPTION_MAX_CHARS = 48;
    /** 题干超过此长度在 1080p 卡片里放不下；依赖课文原文、多子项的题也无法独立作答。 */
    public static final int TV_STEM_MAX_CHARS = 90;
    private static final String[] STEM_DEPENDENT = {"本文结尾", "第四节诗",
            "加横线", "加线", "这段材料", "上文"};
    private static final java.util.regex.Pattern MULTI_SUBITEM =
            java.util.regex.Pattern.compile(
                    "(^|[^0-9.])\\d{1,2}\\s*[.．、]\\s*[^，。]{0,10}[（(]\\s*[）)]");

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
        // 模拟器 ADB 回归抓到 54-71 字的极端选项：18sp 需要 4 行，3 行上限
        // 会把末行裁成半截。按长度继续降档，配合 4 行上限保证整段可读。
        // 19sp/22sp 档在 3 行时仍会顶出固定高度按钮（模拟器 35 字题实测）。
        // 统一压到 16sp：3 行 16sp 约 56dp，按钮文字区约 68dp，留足余量。
        if (max > 48) return 14;
        if (max > 24) return 16;
        if (max > 14) return 20;
        return 24;
    }

    /** 超长选项题不适合 TV 四选一：字号再小也会被固定高度的按钮裁掉末行。 */
    public static boolean optionsFitTv(String[] options) {
        if (options == null) return true;
        for (String s : options) {
            if (s != null && s.length() > TV_OPTION_MAX_CHARS) return false;
        }
        return true;
    }

    /** 只挡可确证的显示/结构问题，不按学科内容判超纲（需教材目录才能签认）。 */
    public static boolean questionFitTv(String text) {
        if (text == null) return true;
        if (text.length() > TV_STEM_MAX_CHARS) return false;
        for (String k : STEM_DEPENDENT) if (text.contains(k)) return false;
        return !MULTI_SUBITEM.matcher(text).find();
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
