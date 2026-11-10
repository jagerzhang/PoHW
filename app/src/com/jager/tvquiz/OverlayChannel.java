package com.jager.tvquiz;

/**
 * 弹题窗口的通道决策：纯 Java，零 Android 依赖，便于 JVM 单测。
 *
 * <p>v1.3.3：借鉴同类项目的做法，悬浮窗授权被电视固件撤销后，
 * 已开启的无障碍服务仍可用 TYPE_ACCESSIBILITY_OVERLAY 承载同一份答题遮罩。
 * 决策只依赖三个可观测布尔/整数，不在这里碰任何系统 API。
 */
public final class OverlayChannel {

    public static final int NONE = 0;
    public static final int APPLICATION = 1;
    public static final int ACCESSIBILITY = 2;

    /** WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY 自 API 22 起可用。 */
    public static final int MIN_A11Y_SDK = 22;

    private OverlayChannel() {}

    /** 悬浮窗优先；无悬浮窗但无障碍已连接且系统版本支持时降级到无障碍窗口。 */
    public static int pick(boolean canDrawOverlays, boolean a11yConnected, int sdkInt) {
        if (canDrawOverlays) return APPLICATION;
        if (a11yConnected && sdkInt >= MIN_A11Y_SDK) return ACCESSIBILITY;
        return NONE;
    }

    public static boolean available(int channel) {
        return channel == APPLICATION || channel == ACCESSIBILITY;
    }
}
