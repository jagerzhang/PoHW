package com.jager.tvquiz;

/**
 * 开机广播策略：action 白名单 + 两个开关的组合判定。
 * 纯 Java，便于 JVM 单测；真机拉起服务仍由 BootReceiver 负责。
 * 「启动即守护」另有 APP 冷启动链路（见 LaunchPolicy），本类只管开机广播。
 */
public final class BootPolicy {

    private BootPolicy() {}

    public static final String BOOT = "android.intent.action.BOOT_COMPLETED";
    public static final String QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON";
    public static final String HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON";
    public static final String LOCKED_BOOT = "android.intent.action.LOCKED_BOOT_COMPLETED";
    public static final String TCL_BOOT = "com.tcl.android.intent.action.BOOT_COMPLETED";
    public static final String HISENSE_BOOT = "com.hisense.intent.action.BOOT_COMPLETED";

    public static final String USER_PRESENT = "android.intent.action.USER_PRESENT";
    public static final String SCREEN_ON = "android.intent.action.SCREEN_ON";
    public static final String MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED";
    public static final String CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE";
    public static final String REVIVE = "com.jager.tvquiz.REVIVE";

    public static boolean isBootAction(String action) {
        return BOOT.equals(action) || QUICKBOOT.equals(action) || HTC_QUICKBOOT.equals(action)
                || LOCKED_BOOT.equals(action) || TCL_BOOT.equals(action) || HISENSE_BOOT.equals(action);
    }

    /** 非开机类「复活」广播：待机唤醒、解锁、覆盖安装、网络变化、定时自检。 */
    public static boolean isReviveAction(String action) {
        return USER_PRESENT.equals(action) || SCREEN_ON.equals(action)
                || MY_PACKAGE_REPLACED.equals(action) || CONNECTIVITY_CHANGE.equals(action)
                || REVIVE.equals(action);
    }

    /**
     * 只在家长此前已开启守护、且悬浮窗可用时才恢复；家长主动关闭守护后不擅自拉起。
     * 与 isBootAction 分开：开机路径还要看 bootAutostart / autoGuardOnBoot 两个开关。
     */
    public static boolean shouldRevive(boolean guardEnabled, boolean canDrawOverlays,
                                       String action) {
        return isReviveAction(action) && guardEnabled && canDrawOverlays;
    }

    /**
     * bootAutostart 关 -> 完全不自启；无可用弹题通道 -> 不拉起（避免后台 FGS 空转）。
     * v1.3.3：布尔实参是「悬浮窗或无障碍降级通道至少有一条可用」，
     * 由调用方用 OverlayChannel.pick() 计算，不再等同于 canDrawOverlays。
     * autoGuardOnBoot 开 -> 无论上次守护是否开启都自动开启；
     * 否则仅恢复上次开启的守护状态。
     */
    public static boolean shouldStartGuard(boolean bootAutostart, boolean autoGuardOnBoot,
                                           boolean wasGuardEnabled, boolean canDrawOverlays,
                                           String action) {
        if (!isBootAction(action)) return false;
        if (!bootAutostart) return false;
        if (!canDrawOverlays) return false;
        return wasGuardEnabled || autoGuardOnBoot;
    }
}
