package com.jager.tvquiz;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * v1.3.0 未发布：可选的「系统常驻看门狗」。
 *
 * <p>只监听窗口状态变化，canRetrieveWindowContent=false（不读取任何界面内容），
 * 在守护服务被清掉、或孩子切应用 / 回桌面时把服务拉回来。
 * 默认不启用，家长须到系统「设置 → 无障碍 → 答题守护」手动开启。
 *
 * <p>注意：本服务不承载答题遮罩，也不拦截 HOME 键（系统保留键，第三方无法拦截）。
 * HOME 的处理在 QuizOverlayService 里通过 ACTION_CLOSE_SYSTEM_DIALOGS 检测 +
 * rebuildOverlay() 重抢焦点完成。
 */
public class GuardAccessibilityService extends AccessibilityService {

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!QuizOverlayService.isRunning()) QuizOverlayService.ensureRunning(this, "accessibility");
    }

    @Override public void onInterrupt() { }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        QuizOverlayService.ensureRunning(this, "accessibility");
    }
}
