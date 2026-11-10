package com.jager.tvquiz;

/**
 * 悬浮窗授权状态转移检测：纯 Java，零 Android 依赖。
 *
 * <p>部分电视（海信 VIDAA / TCL 等）固件会在重启后撤销 SYSTEM_ALERT_WINDOW，
 * 家长看到守护没起来，容易误以为「设置没保存」。这里只在
 * 「上次已授权 → 本次未授权」时报警一次，首次安装 / 覆盖安装不误报。
 */
public final class OverlayPermissionTracker {

    private OverlayPermissionTracker() {}

    /** 是否发生了系统撤销（调用方应发一条高优先级通知）。 */
    public static boolean revokeDetected(boolean wasGranted, boolean nowGranted) {
        return wasGranted && !nowGranted;
    }

    /** 是否需要把新状态落盘（含首次授权与恢复授权）。 */
    public static boolean shouldRecord(boolean wasGranted, boolean nowGranted) {
        return wasGranted != nowGranted;
    }

    /** 授权恢复：上次未授权、本次已授权，可触发守护自愈。 */
    public static boolean restored(boolean wasGranted, boolean nowGranted) {
        return !wasGranted && nowGranted;
    }
}
