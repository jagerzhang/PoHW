package com.jager.tvquiz;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/**
 * v1.3.0 未发布：休眠唤醒 / 进程被清后的兜底拉起。
 *
 * <p>电视「待机 → 唤醒」不会发 BOOT_COMPLETED，只发 SCREEN_ON / USER_PRESENT；
 * 小米等厂商还常在休眠期间清掉后台服务。此 receiver 单独声明，不能挂到
 * BootReceiver 上——后者带 RECEIVE_BOOT_COMPLETED permission，会把非保护广播过滤掉。
 *
 * <p>只在家长此前已开启守护（guardEnabled）且悬浮窗可用时才恢复，
 * 家长主动关闭守护后不会被擅自拉起。
 */
public class ReviveReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context c, Intent i) {
        String action = i == null ? null : i.getAction();
        SettingsStore st = new SettingsStore(c);
        boolean canDraw = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
        boolean channelOk = OverlayChannel.available(OverlayChannel.pick(
                canDraw, GuardAccessibilityService.canHost(), Build.VERSION.SDK_INT));
        if (!BootPolicy.shouldRevive(st.guardEnabled(), channelOk, action)) return;
        if (QuizOverlayService.isRunning()) return;
        // SCREEN_ON / USER_PRESENT 不在 Android 12+ 后台启动 FGS 的豁免白名单里，
        // startForegroundService 可能被拒；ensureRunning 内部吞掉异常，等下一次
        // JobScheduler / AlarmManager 兜底，绝不弹崩溃提示。
        QuizOverlayService.ensureRunning(c, "revive");
    }
}
