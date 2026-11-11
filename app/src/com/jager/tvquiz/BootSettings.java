package com.jager.tvquiz;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

/**
 * 打开厂商「自启动」设置页。Android 没有统一开关，各品牌入口都是私有
 * Activity；逐项尝试显式 ComponentName，全部找不到就降级到应用详情页。
 *
 * <p>很多电视（尤其老固件 / Android TV）压根没有单独的自启动界面，落到
 * 「应用信息」页属正常。竞品同样把它当作「家长一次性手动授权 + 按机型文档」，
 * 所以降级时必须按品牌给出路径并如实说明，不能让家长对着应用详情页发愣。
 */
public final class BootSettings {

    private BootSettings() {}

    private static final String[][] CANDIDATES = {
            {"com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            {"com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            // 电视 ROM 常见入口（尽力尝试，找不到会自动降级到应用详情页）
            {"com.hisense.tvsettings", "com.hisense.tvsettings.SettingsActivity"},
            {"com.tcl.settings", "com.tcl.settings.AutoStartActivity"},
    };

    /**
     * 点击「开机自启引导」先解释清楚再跳转。很多电视没有厂商自启动页，
     * 直接跳到「应用信息」会让家长以为按钮坏了；Toast 在 TV 上又太短。
     */
    public static void showGuide(final Context c) {
        String msg = "Android 没有统一的自启动开关，需按电视品牌在系统设置里允许本应用。\n\n"
                + BootGuide.pathFor(android.os.Build.MANUFACTURER) + "\n"
                + BootGuide.fallbackHint() + "\n\n"
                + "厂商菜单名随固件变化；若找不到对应入口，断电冷启动后手动打开一次 APP 即可。";
        new AlertDialog.Builder(c)
                .setTitle("开机自启设置")
                .setMessage(msg)
                .setPositiveButton("去系统设置", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { open(c); }
                })
                .setNegativeButton("知道了", null)
                .show();
    }

    public static boolean open(Context c) {
        for (String[] p : CANDIDATES) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(p[0], p[1]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (Exception ignored) {
                // 该 ROM 没有这个入口，继续尝试下一个
            }
        }
        try {
            // 降级前先说明：这台电视没有厂商自启动入口，看到应用详情页是正常的。
            Toast.makeText(c,
                    BootGuide.fallbackHint() + "\n"
                            + BootGuide.pathFor(android.os.Build.MANUFACTURER),
                    Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception ignored) {
            // fall through
        }
        Toast.makeText(c,
                "未找到厂商自启动入口：海信 / TCL 请在「设置 → 应用 → 自启动」允许本应用，"
                        + "并在「悬浮窗 / 显示在其他应用上层」中确认授权",
                Toast.LENGTH_LONG).show();
        return false;
    }
}
