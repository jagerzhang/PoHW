package com.jager.tvquiz;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * 开机自启：仅在家长已开启守护且悬浮窗已授权时拉起前台服务。
 * 显式校验 action，避免第三方应用伪造 QUICKBOOT_POWERON 拉起服务。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "PoHW.BootReceiver";
    private static final String QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON";
    private static final String HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON";

    @Override public void onReceive(Context c, Intent i) {
        String action = i == null ? null : i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !QUICKBOOT.equals(action)
                && !HTC_QUICKBOOT.equals(action)) {
            return;
        }
        SettingsStore st = new SettingsStore(c);
        if (!st.guardEnabled()) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(c)) return;

        Intent s = new Intent(c, QuizOverlayService.class);
        s.setAction(QuizOverlayService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Exception e) {
            Log.e(TAG, "开机拉起守护服务失败", e);
        }
    }
}
