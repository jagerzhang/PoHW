package com.jager.tvquiz;

/**
 * 进程内共享的更新检查结果：MainActivity 冷启动检查，首页与家长设置读取同一份结果。
 * 只存清单里的版本信息，不落盘、不上报；进程重启后由 24 小时节流决定是否再查。
 */
public final class UpdateState {

    public static volatile UpdateConfig.Info pending;
    public static volatile long checkedAt;
    public static volatile String message = "";

    private UpdateState() {}

    public static void clear() {
        pending = null;
        message = "";
    }
}
