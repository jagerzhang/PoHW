package com.jager.tvquiz;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;

/**
 * 答题时尽量让后台视频停下来。
 *
 * Android 没有「强制暂停第三方播放器」的公开 API（注入按键需要签名级
 * INJECT_EVENTS 权限，MediaSessionManager.getActiveSessions 需要用户授予
 * 通知使用权）。这里叠加两条所有应用都可用的标准路径：
 *   1) 申请 AUDIOFOCUS_GAIN_TRANSIENT：规范播放器收到 LOSS_TRANSIENT 会自行暂停；
 *   2) 派发 KEYCODE_MEDIA_PAUSE：带 MediaSession 的播放器会直接收到暂停键。
 * 答对解锁时释放音频焦点，并按家长设置派发 MEDIA_PLAY 尝试恢复播放。
 *
 * 这是 best-effort：不保证所有视频 App 都响应，也不申请任何额外权限。
 */
public final class MediaPauseController {

    private static final String TAG = "PoHW.MediaPause";

    private final AudioManager am;
    private boolean focusHeld = false;
    private boolean pauseRequested = false;

    public MediaPauseController(Context c) {
        Context app = c.getApplicationContext();
        am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
    }

    /** 答题窗口弹出后调用。 */
    public void quizShown(SettingsStore st) {
        if (am == null || !st.pauseOnQuiz()) return;
        pauseRequested = true;
        dispatch(KeyEvent.KEYCODE_MEDIA_PAUSE);
        try {
            @SuppressWarnings("deprecation")
            int r = am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            focusHeld = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        } catch (Exception e) {
            Log.w(TAG, "requestAudioFocus failed", e);
            focusHeld = false;
        }
    }

    /** 答对并点击「继续观看」后调用；是否尝试恢复由家长设置决定。 */
    public void quizFinished(SettingsStore st) {
        releaseInternal(st.pauseOnQuiz() && st.resumePlayback());
    }

    /**
     * 服务停止 / 销毁时无条件归还音频焦点并尝试恢复播放：
     * 我们暂停过别人的视频，就不能把它永久留在暂停状态。
     */
    public void release() {
        releaseInternal(true);
    }

    private void releaseInternal(boolean tryResume) {
        // 只要本次派发过暂停键就尝试恢复；音频焦点可能申请失败，
        // 但底层播放器仍可能已经响应了 MEDIA_PAUSE。
        boolean shouldResume = pauseRequested && tryResume;
        if (am != null && focusHeld) {
            try {
                @SuppressWarnings("deprecation")
                int ignored = am.abandonAudioFocus(null);
            } catch (Exception e) {
                Log.w(TAG, "abandonAudioFocus failed", e);
            }
        }
        focusHeld = false;
        pauseRequested = false;
        if (shouldResume) dispatch(KeyEvent.KEYCODE_MEDIA_PLAY);
    }

    private void dispatch(int keycode) {
        if (am == null) return;
        try {
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keycode));
        } catch (Exception e) {
            Log.w(TAG, "dispatchMediaKeyEvent failed", e);
        }
    }
}
