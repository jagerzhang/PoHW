package com.jager.tvquiz;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

/**
 * 低配版（Android 4.0 / minSdk 14）的音频焦点控制。
 *
 * 4.0 没有 AudioManager.dispatchMediaKeyEvent()（API 19+），也没有
 * AudioAttributes / AudioFocusRequest（API 21 / 26+）。这一版只使用
 * 自 API 1 起就存在的 requestAudioFocus(listener, stream, duration)
 * 与 abandonAudioFocus(listener)：规范播放器收到 LOSS_TRANSIENT 会自行
 * 暂停，归还焦点后收到 AUDIOFOCUS_GAIN 会自行恢复。
 *
 * 明确的能力边界：低配版不提供「暂停 / 恢复播放」，也不申请额外权限。
 * 反沉迷依靠全屏答题遮罩本身挡住画面（4.0 无 HIDE_OVERLAY_WINDOWS）。
 */
public final class MediaPauseController {

    private static final String TAG = "PoHW.MediaPause";

    private final AudioManager am;
    /** 旧平台 abandonAudioFocus 要求监听非 null 且与申请时是同一个对象。 */
    private final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override public void onAudioFocusChange(int focusChange) {
                    Log.d(TAG, "onAudioFocusChange=" + focusChange);
                }
            };
    private boolean focusHeld = false;

    public MediaPauseController(Context c) {
        Context app = c.getApplicationContext();
        am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
    }

    /** 答题窗口弹出后调用：抢占短暂音频焦点，让规范播放器自行暂停。 */
    @SuppressWarnings("deprecation")
    public void quizShown(SettingsStore st) {
        if (am == null || !st.pauseOnQuiz()) return;
        try {
            int r = am.requestAudioFocus(focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            focusHeld = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        } catch (Exception e) {
            Log.w(TAG, "requestAudioFocus failed", e);
            focusHeld = false;
        }
    }

    /** 答对并点击「继续观看」后调用；低配版只归还焦点，由播放器自行恢复。 */
    public void quizFinished(SettingsStore st) {
        releaseInternal(true);
    }

    /** 服务停止 / 销毁时无条件归还音频焦点。 */
    public void release() {
        releaseInternal(true);
    }

    @SuppressWarnings("deprecation")
    private void releaseInternal(boolean tryResume) {
        if (am != null && focusHeld) {
            try {
                am.abandonAudioFocus(focusListener);
            } catch (Exception e) {
                Log.w(TAG, "abandonAudioFocus failed", e);
            }
        }
        focusHeld = false;
    }
}
