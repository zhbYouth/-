package com.zhbyouth.tuna;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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

/**
 * 沉浸式界面：内容延伸到状态栏和导航栏下面，状态栏、导航栏透明，
 * 并把安全区尺寸（单位 dp）传给网页，网页用它给内容留出边距。
 */
public class MainActivity extends BridgeActivity {

    private volatile int insetTop = 0;
    private volatile int insetRight = 0;
    private volatile int insetBottom = 0;
    private volatile int insetLeft = 0;

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
