package com.jager.tvquiz;

/** 答题遮罩右上角「朗读」按钮的遥控器焦点转移；纯 Java 便于 JVM 回归。 */
public final class SpeakFocus {

    private SpeakFocus() {}

    public static final int DIR_LEFT = 0;
    public static final int DIR_RIGHT = 1;
    public static final int DIR_UP = 2;
    public static final int DIR_DOWN = 3;

    /** 只有停在选项第一行（A/B）按上键才进入朗读按钮，避免抢走正常导航。 */
    public static boolean shouldEnter(boolean focused, int dir, int selected) {
        return !focused && dir == DIR_UP && (selected == 0 || selected == 1);
    }

    /** 朗读按钮按下键回到选项网格，保持原高亮项。 */
    public static boolean shouldLeave(boolean focused, int dir) {
        return focused && dir == DIR_DOWN;
    }
}
