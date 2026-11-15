package com.chromaflow.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;

/**
 * Hosts the WebGL fluid engine (assets/index.html) full screen.
 * The page tells us when it is in play mode, so Back returns to the menu
 * while playing and closes the app from the menu.
 */
public class MainActivity extends Activity {

    private WebView web;
    private static final int REQUEST_WALLPAPER = 1;
    private static final String KEY_PENDING_WHERE = "pending_where";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        web = new WebView(this);
        web.setBackgroundColor(0xFF07080C);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        // Follow the phone's font size setting (Settings > Display > Font size).
        s.setTextZoom(Math.round(getResources().getConfiguration().fontScale * 100));

        web.addJavascriptInterface(new Bridge(), "ChromaAndroid");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d("ChromaFlow", m.message() + " (line " + m.lineNumber() + ")");
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // The page never navigates away from the bundled file. Web links
                // (privacy policy, source code) open in the phone's browser instead.
                Uri url = request.getUrl();
                String scheme = url.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, url));
                    } catch (ActivityNotFoundException ignored) {
                        // No browser installed; nothing to open the link with.
                    }
                }
                return !"file".equals(scheme);
            }
        });

        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        goImmersive();

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    // The page handles Back while playing, in Settings or in the wallpaper setup; otherwise the app closes.
    private void handleBack() {
        web.evaluateJavascript("window.chromaBack ? window.chromaBack() : false", handled -> {
            if (!"true".equals(handled)) finish();
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    @SuppressWarnings("deprecation")
    private void goImmersive() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController c = window.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goImmersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        goImmersive();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    private SharedPreferences wallpaperPrefs() {
        return getSharedPreferences(FluidWallpaperService.PREFS, MODE_PRIVATE);
    }

    private boolean isOurs(WallpaperInfo info) {
        return info != null && new ComponentName(this, FluidWallpaperService.class).equals(info.getComponent());
    }

    private boolean onHomeScreen() {
        return isOurs(WallpaperManager.getInstance(this).getWallpaperInfo());
    }

    // The lock screen shows the home screen's wallpaper unless it has its own (negative id).
    private boolean onLockScreen() {
        WallpaperManager wm = WallpaperManager.getInstance(this);
        if (Build.VERSION.SDK_INT >= 34) {
            WallpaperInfo lock = wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
            if (lock != null) return isOurs(lock);
        }
        return wm.getWallpaperId(WallpaperManager.FLAG_LOCK) < 0 && onHomeScreen();
    }

    // Saves the look for the picked screen(s) ("home", "lock" or "both"). Returns false if
    // Chroma Flow isn't on one of those screens, so the phone's wallpaper screen must set it first.
    private boolean saveForScreens(String where, String look, String options, boolean onlyIfShown) {
        boolean home = !"lock".equals(where), lock = !"home".equals(where);
        boolean homeOk = onHomeScreen(), lockOk = onLockScreen();
        if (onlyIfShown && ((home && !homeOk) || (lock && !lockOk))) return false;
        if ((!home || !homeOk) && (!lock || !lockOk)) return false;
        SharedPreferences.Editor e = wallpaperPrefs().edit();
        if (home && homeOk) e.putString(FluidWallpaperService.KEY_LOOK_HOME, look);
        if (lock && lockOk) e.putString(FluidWallpaperService.KEY_LOOK_LOCK, look);
        e.putString(FluidWallpaperService.KEY_OPTIONS, options).apply();
        return true;
    }

    // Back from the phone's wallpaper screen (first time only). If Chroma Flow is now the
    // wallpaper, the picked screen(s) get the waiting look; if they backed out, nothing changes.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_WALLPAPER) return;
        SharedPreferences p = wallpaperPrefs();
        boolean applied = saveForScreens(p.getString(KEY_PENDING_WHERE, "both"),
                p.getString(FluidWallpaperService.KEY_PENDING_LOOK, null),
                p.getString(FluidWallpaperService.KEY_PENDING_OPTIONS, null), false);
        p.edit().remove(FluidWallpaperService.KEY_PENDING_LOOK).remove(FluidWallpaperService.KEY_PENDING_OPTIONS)
                .remove(KEY_PENDING_WHERE).apply();
        web.evaluateJavascript("window.chromaWallpaperDone && window.chromaWallpaperDone(" + applied + ")", null);
    }

    private class Bridge {
        @JavascriptInterface
        public void setPlaying(boolean value) {
            runOnUiThread(() -> {
                if (value) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        /** True when Chroma Flow is the home or lock screen wallpaper right now. */
        @JavascriptInterface
        public boolean wallpaperActive() {
            return onHomeScreen() || onLockScreen();
        }

        /**
         * Sets the look on the screen(s) the user picked in the app: "home", "lock" or "both".
         * If Chroma Flow is already on those screens it updates them straight away ("updated").
         * Otherwise the phone's wallpaper screen opens to set it ("opened"); see onActivityResult.
         */
        @JavascriptInterface
        public String setWallpaper(String lookJson, String optionsJson, String where) {
            if (saveForScreens(where, lookJson, optionsJson, true)) return "updated";
            runOnUiThread(() -> {
                wallpaperPrefs().edit()
                        .putString(FluidWallpaperService.KEY_PENDING_LOOK, lookJson)
                        .putString(FluidWallpaperService.KEY_PENDING_OPTIONS, optionsJson)
                        .putString(KEY_PENDING_WHERE, where).apply();
                ComponentName ours = new ComponentName(MainActivity.this, FluidWallpaperService.class);
                try {
                    startActivityForResult(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ours), REQUEST_WALLPAPER);
                } catch (ActivityNotFoundException e) {
                    try {
                        startActivityForResult(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER), REQUEST_WALLPAPER);
                    } catch (ActivityNotFoundException e2) {
                        web.evaluateJavascript("window.chromaWallpaperFailed && window.chromaWallpaperFailed()", null);
                    }
                }
            });
            return "opened";
        }
    }
}
