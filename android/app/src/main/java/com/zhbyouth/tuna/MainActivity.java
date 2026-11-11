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
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.activity.OnBackPressedCallback;
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
 * 并把安全区尺寸（单位 dp）传给网页。返回键交给网页逐层返回，
 * 只有在主界面才会退到后台。语音引导使用系统自带的中文语音引擎。
 */
public class MainActivity extends BridgeActivity {

    private volatile int insetTop = 0;
    private volatile int insetRight = 0;
    private volatile int insetBottom = 0;
    private volatile int insetLeft = 0;

    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    /** 语音列表只在引擎就绪后算一次并缓存，网页读取时不会卡住。 */
    private volatile String voicesJson = "[]";
    private volatile String currentVoice = "";
    private volatile float currentRate = -1f;
    private volatile float currentPitch = -1f;

    private class VoiceBridge {
        @JavascriptInterface
        public String list() {
            return voicesJson;
        }

        @JavascriptInterface
        public void speak(final String text, final String voiceName, final float rate, final float pitch) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (tts == null || !ttsReady) return;
                        if (rate != currentRate) {
                            tts.setSpeechRate(rate);
                            currentRate = rate;
                        }
                        if (pitch != currentPitch) {
                            tts.setPitch(pitch);
                            currentPitch = pitch;
                        }
                        // 只有换了声音才重新设置，反复设置会让朗读出现停顿
                        if (voiceName != null && !voiceName.isEmpty() && !voiceName.equals(currentVoice)) {
                            Set<Voice> all = tts.getVoices();
                            if (all != null) {
                                for (Voice v : all) {
                                    if (v.getName().equals(voiceName)) {
                                        tts.setVoice(v);
                                        currentVoice = voiceName;
                                        break;
                                    }
                                }
                            }
                        }
                        android.os.Bundle b = new android.os.Bundle();
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

    private void buildVoiceList() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONArray arr = new JSONArray();
                try {
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
                } catch (Exception ignored) {
                }
                voicesJson = arr.toString();
            }
        }).start();
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
                    buildVoiceList();
                }
            }
        });

        // 返回键：先交给网页逐层返回（练习 → 主界面，第二层 → 主界面），
        // 网页说没有可返回的层，才退到后台。
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript(
                    "(function(){try{return window.__tunaBack?window.__tunaBack():false}catch(e){return false}})()",
                    new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            if (!"true".equals(value)) moveTaskToBack(true);
                        }
                    });
            }
        });

        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            float d = getResources().getDisplayMetrics().density;
            int t = Math.round(bars.top / d), r = Math.round(bars.right / d);
            int b = Math.round(bars.bottom / d), l = Math.round(bars.left / d);
            if (t != insetTop || r != insetRight || b != insetBottom || l != insetLeft) {
                insetTop = t;
                insetRight = r;
                insetBottom = b;
                insetLeft = l;
                final String js = "window.__setInsets&&window.__setInsets(" + t + "," + r + "," + b + "," + l + ")";
                web.post(() -> web.evaluateJavascript(js, null));
            }
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
