package com.zhbyouth.tuna;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 沉浸式界面：内容延伸到状态栏和导航栏下面，状态栏、导航栏透明，
 * 并把安全区尺寸（单位 dp）传给网页，网页用它给内容留出边距。
 */
public class MainActivity extends BridgeActivity {

    private volatile int insetTop = 0;
    private volatile int insetRight = 0;
    private volatile int insetBottom = 0;
    private volatile int insetLeft = 0;

    private TextToSpeech tts;
    private volatile boolean ttsReady = false;

    /** 语音引导：用系统自带的中文语音引擎朗读，网页可以列出可用声音并选择。 */
    private class VoiceBridge {
        @JavascriptInterface
        public String list() {
            JSONArray arr = new JSONArray();
            try {
                if (tts != null && ttsReady) {
                    Set<Voice> all = tts.getVoices();
                    List<Voice> zh = new ArrayList<>();
                    if (all != null) {
                        for (Voice v : all) {
                            Locale l = v.getLocale();
                            if ("zh".equals(l.getLanguage()) && !"HK".equals(l.getCountry())) zh.add(v);
                        }
                    }
                    Collections.sort(zh, new Comparator<Voice>() {
                        @Override
                        public int compare(Voice a, Voice b) {
                            boolean la = !a.isNetworkConnectionRequired();
                            boolean lb = !b.isNetworkConnectionRequired();
                            if (la != lb) return la ? -1 : 1;
                            return a.getName().compareTo(b.getName());
                        }
                    });
                    for (Voice v : zh) {
                        JSONObject o = new JSONObject();
                        o.put("name", v.getName());
                        o.put("local", !v.isNetworkConnectionRequired());
                        arr.put(o);
                    }
                }
            } catch (Exception ignored) {
            }
            return arr.toString();
        }

        @JavascriptInterface
        public void speak(final String text, final String voiceName, final float rate, final float pitch) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (tts == null || !ttsReady) return;
                        tts.setSpeechRate(rate);
                        tts.setPitch(pitch);
                        if (voiceName != null && !voiceName.isEmpty()) {
                            Set<Voice> all = tts.getVoices();
                            if (all != null) {
                                for (Voice v : all) {
                                    if (v.getName().equals(voiceName)) {
                                        tts.setVoice(v);
                                        break;
                                    }
                                }
                            }
                        }
                        Bundle b = new Bundle();
                        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.95f);
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, b, "tuna");
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (tts != null) tts.stop();
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }

    /** 网页启动时主动读取一次安全区。 */
    private class InsetsBridge {
        @JavascriptInterface
        public String get() {
            return insetTop + "," + insetRight + "," + insetBottom + "," + insetLeft;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        applyBarAppearance();

        final WebView web = getBridge().getWebView();
        web.addJavascriptInterface(new InsetsBridge(), "TunaInsets");
        web.addJavascriptInterface(new VoiceBridge(), "TunaVoice");

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                    } catch (Exception ignored) {
                    }
                    ttsReady = true;
                }
            }
        });

        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            float d = getResources().getDisplayMetrics().density;
            insetTop = Math.round(bars.top / d);
            insetRight = Math.round(bars.right / d);
            insetBottom = Math.round(bars.bottom / d);
            insetLeft = Math.round(bars.left / d);
            final String js = "window.__setInsets&&window.__setInsets("
                + insetTop + "," + insetRight + "," + insetBottom + "," + insetLeft + ")";
            web.post(() -> web.evaluateJavascript(js, null));
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    @Override
    public void onDestroy() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyBarAppearance();
    }

    /** 跟随系统深浅色，切换状态栏和导航栏图标的明暗。 */
    private void applyBarAppearance() {
        boolean night = (getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat c =
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(!night);
        c.setAppearanceLightNavigationBars(!night);
    }
}
