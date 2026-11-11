package com.jager.tvquiz;

/**
 * 按电视品牌给出「开机自启」的授权路径。纯 Java，不依赖 Android API，
 * 便于 JVM 回归；厂商菜单名随固件版本变化，这里给的是社区实测口径。
 *
 * <p>Android 没有统一的自启动开关，第三方应用也拿不到开机自启权限。
 * 竞品（StudyOnTV / Aerial）同样把它当作「家长一次性手动授权 + 按机型文档」，
 * 而不是应用内可解决的能力；部分电视压根没有该界面，属正常。
 */
public final class BootGuide {

    private BootGuide() {}

    public static String pathFor(String manufacturer) {
        String m = (manufacturer == null ? "" : manufacturer)
                .toLowerCase(java.util.Locale.US);
        if (m.contains("hisense") || m.contains("vidaa")) {
            return "海信 / Vidda：小聚助手 → 启动管理，允许「答题守护」；"
                    + "部分固件在 设置 → 应用 → 自启动。";
        }
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("mitv")) {
            return "小米 / Redmi：电视管家 → 自启动管理（或白名单管理），允许「答题守护」。";
        }
        if (m.contains("tcl") || m.contains("ffalcon") || m.contains("thunder")) {
            return "TCL / 雷鸟：安全卫士（Safety Guard）→ 权限护盾 → 自启动。";
        }
        if (m.contains("skyworth") || m.contains("coocaa")) {
            return "创维 / 酷开：我的应用 → 管理 → 启动管理（或酷开应用圈 → 自启管理）。";
        }
        if (m.contains("huawei") || m.contains("honor")) {
            return "华为 / 荣耀：设置 → 应用 → 启动管理，允许自启动与关联启动。";
        }
        return "其他品牌：设置 → 应用 → 找到「答题守护」→ 自启动 / 开机启动。";
    }

    /** 降级到「应用信息」页时必须如实说明，别让家长以为按钮坏了。 */
    public static String fallbackHint() {
        return "若打开的是「应用信息」页，说明这台电视没有单独的自启动开关（属正常）；"
                + "断电冷启动后手动打开一次 APP 即可恢复守护，待机唤醒不受影响。";
    }
}
