package com.jager.tvquiz;

/**
 * 控制台守护开关的视觉状态：单一规则源，零 Android 依赖，便于 JVM 单测。
 *
 * 需求：点击「关闭守护」后宿主界面不退出、不跳转，只用按钮颜色与文字反映状态。
 * 规则：当前可执行的动作给强调色，已完成/不可执行的动作给灰色。
 */
public final class GuardState {

    private GuardState() {}

    public static final int ON_GREEN = 0xFF1D9E75;
    public static final int OFF_GRAY = 0xFF37425A;
    public static final int STOP_RED = 0xFFA32D2D;

    /** 运行中「开启守护」已生效 -> 灰；未运行 -> 绿（可点）。 */
    public static int startColor(boolean running) { return running ? OFF_GRAY : ON_GREEN; }
    /** 运行中「关闭守护」可点 -> 红；已关闭 -> 灰。 */
    public static int stopColor(boolean running) { return running ? STOP_RED : OFF_GRAY; }

    public static String startLabel(boolean running) { return running ? "运行中" : "开启守护"; }
    public static String stopLabel(boolean running) { return running ? "关闭守护" : "已关闭"; }

    /** 两个开关永远只有一个处于强调色，避免用户误判当前状态。 */
    public static boolean mutuallyExclusive(boolean running) {
        int a = startColor(running), b = stopColor(running);
        return (a == OFF_GRAY) != (b == OFF_GRAY);
    }
}
