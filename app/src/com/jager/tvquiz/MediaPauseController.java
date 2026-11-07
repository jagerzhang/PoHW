package com.jager.tvquiz;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

/**
 * 低配版（Android 4.0 / minSdk 14）的音频焦点控制。
 *
 * 4.0 没有 dispatchMediaKeyEvent()（API 19+），也没有 AudioAttributes /
 * AudioFocusRequest（API 21 / 26+）。只用 requestAudioFocus(listener, stream,
 * duration) 与 abandonAudioFocus(listener)。低配版明确不提供暂停 / 恢复播放。
 */
public final class MediaPauseController {

    private static final String TAG = "PoHW.MediaPause";

    private final AudioManager am;
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

    public void quizFinished(SettingsStore st) { releaseInternal(); }

    public void release() { releaseInternal(); }

    @SuppressWarnings("deprecation")
    private void releaseInternal() {
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
