package com.jager.tvquiz;

/** 关闭守护的授权策略：单一规则源，便于 JVM 单测。 */
public final class GuardPolicy {

    private GuardPolicy() {}

    /** 已设置家长密码时，关闭守护必须先验证。 */
    public static boolean requiresPinToStop(boolean hasPin) {
        return hasPin;
    }

    /** 服务侧二次校验：有密码但未验证的 STOP 一律拒绝。 */
    public static boolean allowStop(boolean hasPin, boolean verified) {
        return !hasPin || verified;
    }

    /** v1.3.1：开启守护前必须已设置家长密码，否则孩子可以随手把守护关掉。
     *  MainActivity 与前台服务共用这一条规则，任何旁路拉起都逃不过。 */
    public static boolean canStartGuard(boolean hasPin) {
        return hasPin;
    }
}
