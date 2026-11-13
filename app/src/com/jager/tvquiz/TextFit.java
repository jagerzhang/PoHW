package com.jager.tvquiz;

/**
 * 字号自适应决策：纯 Java，不依赖 android.*，可由 JVM 单测覆盖。
 *
 * <p>Android 侧 AutoFitText 用 StaticLayout 按真实可用宽度量出每个候选字号的
 * 折行数与像素高度，再把结果交给这里挑「从大到小第一个放得下」的字号。
 * 旧实现按字数分档猜字号：24 字选项在 TV 上折成 3 行需要约 69dp，
 * 而按钮文字区只有 68dp，末行就会被裁成半截（真机截图反馈）。
 */
public final class TextFit {

    private TextFit() {}

    /**
     * @param ladder      从大到小排列的候选 sp 字号
     * @param lineCounts  每个候选档实测折行数
     * @param heightsDp   每个候选档实测文字总高度（dp，向上取整）
     * @param availableDp TextView 去掉 padding 后的可用高度（dp）
     * @param maxLines    允许的最大行数
     * @return 放得下的最大字号；全都放不下时保守返回最小档
     */
    public static int choose(int[] ladder, int[] lineCounts, int[] heightsDp,
                             int availableDp, int maxLines) {
        if (ladder == null || ladder.length == 0) return 0;
        int n = ladder.length;
        if (lineCounts == null || heightsDp == null
                || lineCounts.length < n || heightsDp.length < n) {
            return ladder[n - 1];
        }
        if (availableDp <= 0 || maxLines <= 0) return ladder[n - 1];
        for (int i = 0; i < n; i++) {
            if (lineCounts[i] > maxLines) continue;
            if (heightsDp[i] > availableDp) continue;
            return ladder[i];
        }
        return ladder[n - 1];
    }
}
