package com.jager.tvquiz;

import android.content.Context;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;

import java.util.HashMap;
import java.util.Locale;

/**
 * 系统 TTS 朗读：只使用 API 4 就存在的 framework 接口，不打包任何语音引擎或音频资源。
 *
 * 设计约束：
 *  - 低配包 minSdk 14 的硬门禁要求 dex 里不出现 API>14 的类，因此刻意避开
 *    UtteranceProgressListener(15) / Voice(21) / Bundle 版 speak(21)。
 *  - 设备没装中文语音引擎是常态（电视盒子尤其常见），所以状态必须可查询、可如实提示，
 *    朗读失败一律静默降级，绝不阻塞答题。
 *  - 全局单例：设置页与守护服务共用一次初始化，避免多个 TTS 实例抢占音频通道。
 */
public final class SpeechReader {

    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_READY = 1;
    public static final int STATE_NO_ENGINE = 2;
    public static final int STATE_NO_DATA = 3;

    public interface StateListener { void onState(int state); }

    private static volatile SpeechReader instance;

    private final TextToSpeech tts;
    private volatile int state = STATE_UNKNOWN;
    private volatile StateListener listener;

    private SpeechReader(Context c, StateListener l) {
        this.listener = l;
        this.tts = new TextToSpeech(c.getApplicationContext(),
                new TextToSpeech.OnInitListener() {
                    @Override public void onInit(int status) {
                        int s = STATE_NO_ENGINE;
                        if (status == TextToSpeech.SUCCESS) {
                            int r = tts.setLanguage(Locale.CHINA);
                            s = (r == TextToSpeech.LANG_MISSING_DATA
                                    || r == TextToSpeech.LANG_NOT_SUPPORTED)
                                    ? STATE_NO_DATA : STATE_READY;
                        }
                        setState(s);
                    }
                });
    }

    public static SpeechReader get(Context c, StateListener l) {
        SpeechReader r = instance;
        if (r == null) {
            synchronized (SpeechReader.class) {
                r = instance;
                if (r == null) {
                    r = new SpeechReader(c, l);
                    instance = r;
                }
            }
        }
        r.listener = l;
        return r;
    }

    private void setState(int s) {
        state = s;
        StateListener l = listener;
        if (l != null) l.onState(s);
    }

    public int state() { return state; }

    /** 面向家长的状态说明；引擎缺失时明确写出，不假装能用。 */
    public static String stateLabel(int s) {
        if (s == STATE_READY) return "已就绪";
        if (s == STATE_NO_ENGINE) return "未检测到中文语音引擎";
        if (s == STATE_NO_DATA) return "语音引擎缺少中文数据";
        return "尚未检测";
    }

    public void speak(String text) {
        if (state != STATE_READY || text == null || text.length() == 0) return;
        HashMap<String, String> p = new HashMap<String, String>();
        p.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                String.valueOf(AudioManager.STREAM_MUSIC));
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, p);
    }

    /** 题目结束立刻停读，避免解析播报盖住恢复播放的声音。 */
    public void stop() {
        if (state == STATE_READY) tts.stop();
    }

    /** 仅在守护服务销毁时调用：释放系统 TTS 连接并清空单例。 */
    public void close() {
        state = STATE_UNKNOWN;
        try { tts.stop(); } catch (Exception ignored) { }
        tts.shutdown();
        instance = null;
    }
}
