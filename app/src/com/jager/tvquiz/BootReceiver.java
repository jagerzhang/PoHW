package com.jager.tvquiz;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * 开机自启：由 BootPolicy 统一判定。
 * 「开机自动启动」关闭时完全不响应；「开机自动开启守护」开启时
 * 无论上次守护是否开启都拉起服务，否则只恢复上次已开启的守护。
 * 厂商（尤其小米）还需要在系统「自启动」设置里允许本应用接收开机广播，
 * 入口见 BootSettings.open()。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "PoHW.BootReceiver";

    @Override public void onReceive(Context c, Intent i) {
        String action = i == null ? null : i.getAction();
        SettingsStore st = new SettingsStore(c);
        boolean canDraw = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
        if (!BootPolicy.shouldStartGuard(st.bootAutostart(), st.autoGuardOnBoot(),
                st.guardEnabled(), canDraw, action)) {
            return;
        }

        Intent s = new Intent(c, QuizOverlayService.class);
        s.setAction(QuizOverlayService.ACTION_START);
        try {
            // BOOT_COMPLETED 属于后台启动 FGS 的豁免广播，Android 8+ 允许直接拉起。
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Exception e) {
            Log.e(TAG, "开机拉起守护服务失败", e);
        }
    }
}
