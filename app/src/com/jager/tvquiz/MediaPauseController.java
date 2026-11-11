package com.jager.tvquiz;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;

/**
 * 答题时尽量让后台视频停下来。
 *
 * Android 没有「强制暂停第三方播放器」的公开 API（注入按键需要签名级
 * INJECT_EVENTS 权限，MediaSessionManager.getActiveSessions 需要用户授予
 * 通知使用权）。可用的是两条标准路径：
 *   1) 申请 AUDIOFOCUS_GAIN_TRANSIENT：规范播放器收到 LOSS_TRANSIENT 会自行暂停；
 *   2) 派发 KEYCODE_MEDIA_PAUSE：带 MediaSession 的播放器会直接收到暂停键。
 *
 * 关键：**同一时刻只使用其中一条，并且暂停 / 恢复必须成对**。电视播放器普遍把
 * 媒体键当成 play/pause 切换键。旧实现弹题时既发 MEDIA_PAUSE 又抢音频焦点，
 * 两条暂停信号互相抵消（弹题没暂停）；答对时又「先归还焦点、再补发 MEDIA_PLAY」，
 * 播放器随 AUDIOFOCUS_GAIN 恢复播放后被 PLAY 键再次切换，反而停下
 * （点「继续观看」后视频暂停）。现在拿到焦点就完全靠焦点，拿不到才用媒体键
 * 兜底；媒体键路径的恢复键也提到归还焦点之前，两条路径各自内部配对。
 *
 * 这是 best-effort：不保证所有视频 App 都响应，也不申请任何额外权限。
 */
public final class MediaPauseController {

    private static final String TAG = "PoHW.MediaPause";

    private final AudioManager am;
    /** 音频焦点监听必须非 null：旧平台 abandonAudioFocus 与新平台 AudioFocusRequest 都要求。 */
    private final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override public void onAudioFocusChange(int focusChange) {
                    Log.d(TAG, "onAudioFocusChange=" + focusChange);
                }
            };
    private boolean focusHeld = false;
    private boolean pauseRequested = false;
    /** 只有音频焦点没申请到、改用媒体键兜底时才为 true。 */
    private boolean usedMediaKey = false;

    public MediaPauseController(Context c) {
        Context app = c.getApplicationContext();
        am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
    }

    /** 答题窗口弹出后调用。 */
    @SuppressWarnings("deprecation")
    public void quizShown(SettingsStore st) {
        if (am == null || !st.pauseOnQuiz()) return;
        pauseRequested = true;
        focusHeld = false;
        usedMediaKey = false;
        try {
            // API 1+ 的三参重载；AudioFocusRequest / AudioAttributes 是 API 26/21 类，
            // 低配 dex 里不能引用，否则类加载即崩。
            int r = am.requestAudioFocus(focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            focusHeld = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        } catch (Exception e) {
            Log.w(TAG, "requestAudioFocus failed", e);
            focusHeld = false;
        }
        // 只发一种暂停信号：拿到焦点就完全依赖 LOSS_TRANSIENT；拿不到才补媒体键。
        if (!focusHeld) {
            usedMediaKey = true;
            dispatch(KeyEvent.KEYCODE_MEDIA_PAUSE);
        }
    }

    /** 答对并「继续观看」（或自动继续）后调用；是否尝试恢复由家长设置决定。 */
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

    @SuppressWarnings("deprecation")
    private void releaseInternal(boolean tryResume) {
        // 媒体键兜底路径：先补发 MEDIA_PLAY 再归还焦点。反过来做会让播放器随
        // AUDIOFOCUS_GAIN 恢复播放之后，又把 PLAY 当成切换键切回暂停。
        if (usedMediaKey && pauseRequested && tryResume) {
            dispatch(KeyEvent.KEYCODE_MEDIA_PLAY);
        }
        // 无论 tryResume 与否，都必须归还 TRANSIENT 音频焦点，绝不把别人的焦点扣在手里。
        if (am != null && focusHeld) {
            try {
                am.abandonAudioFocus(focusListener);
            } catch (Exception e) {
                Log.w(TAG, "abandonAudioFocus failed", e);
            }
        }
        focusHeld = false;
        pauseRequested = false;
        usedMediaKey = false;
        // 诚实说明：resumePlayback=false 也只是 best-effort。一旦 abandon 音频焦点，
        // 合规播放器可能随 AUDIOFOCUS_GAIN 自行恢复播放；想彻底阻止只能永久持有
        // 音频焦点，那既不可接受也不礼貌，因此无法保证「绝不恢复」。
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
