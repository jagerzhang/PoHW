package com.jager.tvquiz;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * v1.3.0 起：可选的「系统常驻看门狗」。
 * v1.3.3 起：兼作无悬浮窗权限时的降级遮罩宿主。
 *
 * <p>只监听窗口状态变化，canRetrieveWindowContent=false（不读取任何界面内容）。
 * 默认不启用，家长须到系统「设置 → 无障碍 → 答题守护」手动开启。
 *
 * <p>当电视固件撤销 SYSTEM_ALERT_WINDOW 后，本服务用
 * TYPE_ACCESSIBILITY_OVERLAY 承载由 QuizOverlayService 创建的同一份
 * OverlayQuizView：答题状态机、抽题与判题全部复用，不重写一套。
 * 该窗口类型自 API 22 起可用；更老的系统退回「无通道」并给家长通知。
 *
 * <p>本服务不拦截 HOME 键（系统保留键，第三方无法拦截）。
 */
public class GuardAccessibilityService extends AccessibilityService {

    private static final String TAG = "PoHW.A11y";
    private static volatile GuardAccessibilityService instance = null;
    private static volatile boolean connected = false;
    private WindowManager wm;

    public static boolean canHost() {
        GuardAccessibilityService s = instance;
        // 用活跃实例而非独立布尔：系统重绑时 onDestroy 可能晚于 onServiceConnected，
        // 独立布尔会被旧实例清成 false，导致通道误判为「未就绪」。
        return s != null && s.wm != null
                && Build.VERSION.SDK_INT >= OverlayChannel.MIN_A11Y_SDK;
    }

    /** 由 QuizOverlayService 调用；View 的 Context 与宿主不同但同进程同 UID，可安全挂载。 */
    public static boolean addOverlay(View v, WindowManager.LayoutParams lp) {
        GuardAccessibilityService s = instance;
        if (s == null || s.wm == null || v == null) return false;
        try {
            s.wm.addView(v, lp);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "accessibility overlay add failed", e);
            return false;
        }
    }

    public static void updateOverlay(View v, WindowManager.LayoutParams lp) {
        GuardAccessibilityService s = instance;
        if (s == null || s.wm == null || v == null) return;
        try { s.wm.updateViewLayout(v, lp); }
        catch (Exception e) { Log.w(TAG, "accessibility overlay update failed", e); }
    }

    public static void removeOverlay(View v) {
        GuardAccessibilityService s = instance;
        if (s == null || s.wm == null || v == null) return;
        try { s.wm.removeViewImmediate(v); }
        catch (Exception e) { Log.w(TAG, "accessibility overlay remove failed", e); }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!QuizOverlayService.isRunning()) QuizOverlayService.ensureRunning(this, "accessibility");
    }

    @Override public void onInterrupt() { }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        connected = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Log.i(TAG, "onServiceConnected hostReady=" + canHost());
        QuizOverlayService.ensureRunning(this, "accessibility");
    }

    @Override public void onDestroy() {
        if (instance == this) {
            // 只有活跃实例才撤遮罩、清引用；迟到的旧实例不得清掉新实例的宿主状态。
            QuizOverlayService.onOverlayChannelLost();
            instance = null;
            connected = false;
            wm = null;
            Log.i(TAG, "onDestroy active instance cleared");
        } else {
            Log.i(TAG, "onDestroy stale instance ignored");
        }
        super.onDestroy();
    }
}
