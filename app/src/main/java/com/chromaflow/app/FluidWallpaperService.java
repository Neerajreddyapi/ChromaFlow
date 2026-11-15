package com.chromaflow.app;

import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

/**
 * Chroma Flow as a live wallpaper. It draws the look set from the app's wallpaper setup
 * (style, colors, Fine-tune) with FluidRenderer, a native port of the app's engine.
 * The home and lock screen each keep their own look, so setting one leaves the other as it was.
 * When one wallpaper covers both screens (always on Android 13 and older, and on phones like
 * OnePlus whose wallpaper screen sets home, lock and always-on display together), it shows the
 * lock look while the phone is locked and switches to the home look when it is unlocked.
 * Home screen: swipes stir the fluid, and it can stir itself while nobody touches it.
 * Lock screen: it always stirs itself.
 * It only draws while visible, at 30 or 60 frames a second.
 */
public class FluidWallpaperService extends WallpaperService {

    static final String PREFS = "wallpaper";
    static final String KEY_LOOK_HOME = "look_home";
    static final String KEY_LOOK_LOCK = "look_lock";
    static final String KEY_LOOK = "look";  // one look for both screens (first test build)
    static final String KEY_OPTIONS = "options";
    // A look waiting in the phone's wallpaper preview, used the first time Chroma Flow is set.
    // MainActivity moves it to the screen(s) the user picked in the app.
    static final String KEY_PENDING_LOOK = "pending_look";
    static final String KEY_PENDING_OPTIONS = "pending_options";
    static final int BOTH = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;

    @Override
    public Engine onCreateEngine() {
        return new FluidEngine();
    }

    private class FluidEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener, Choreographer.FrameCallback {

        private SharedPreferences prefs;
        private WallpaperLook.Options options;
        private FluidRenderer renderer;
        private WallpaperGLView view;
        private KeyguardManager keyguard;
        private boolean visible, locked;
        private long lastRender, lastLockCheck;
        // Locking and unlocking switch between the lock and home looks without waiting.
        private final BroadcastReceiver screenEvents = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) { updateLocked(); }
        };

        /** A GLSurfaceView that draws into the wallpaper's surface instead of a window. */
        private class WallpaperGLView extends GLSurfaceView {
            WallpaperGLView() { super(FluidWallpaperService.this); }

            @Override
            public SurfaceHolder getHolder() { return getSurfaceHolder(); }

            void destroy() { super.onDetachedFromWindow(); }
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            keyguard = getSystemService(KeyguardManager.class);
            locked = !isPreview() && keyguard != null && keyguard.isKeyguardLocked();
            options = WallpaperLook.Options.parse(optionsJson());
            WallpaperLook look = WallpaperLook.parse(lookJson());
            renderer = new FluidRenderer(look);
            logLook(look);
            renderer.auto = options.auto;
            renderer.locked = locked;
            view = new WallpaperGLView();
            view.setEGLContextClientVersion(3);
            view.setPreserveEGLContextOnPause(true);
            view.setRenderer(renderer);
            view.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            setTouchEventsEnabled(true);
            prefs.registerOnSharedPreferenceChangeListener(this);
            IntentFilter f = new IntentFilter(Intent.ACTION_USER_PRESENT);
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenEvents, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(screenEvents, f);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            if (v == visible) return;
            visible = v;
            Choreographer ch = Choreographer.getInstance();
            if (v) {
                updateLocked();
                view.onResume();
                lastRender = 0;
                ch.postFrameCallback(this);
            } else {
                ch.removeFrameCallback(this);
                view.onPause();
                view.queueEvent(renderer::pointersCancel);
            }
        }

        // Paces drawing to the chosen frame rate on any refresh rate (60, 90, 120 Hz).
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!visible) return;
            if (frameTimeNanos - lastLockCheck > 1_000_000_000L) updateLocked();
            long interval = 1_000_000_000L / options.fps;
            if (frameTimeNanos - lastRender >= interval - 3_000_000L) {
                lastRender = frameTimeNanos;
                view.requestRender();
            }
            Choreographer.getInstance().postFrameCallback(this);
        }

        private void updateLocked() {
            lastLockCheck = System.nanoTime();
            boolean now = !isPreview() && keyguard != null && keyguard.isKeyguardLocked();
            renderer.locked = now;
            if (now != locked) {
                locked = now;
                if (screens() == BOTH) showLook();
            }
        }

        @Override
        public void onTouchEvent(MotionEvent e) {
            super.onTouchEvent(e);
            if (!visible || renderer.locked) return;
            android.graphics.Rect frame = getSurfaceHolder().getSurfaceFrame();
            float w = Math.max(1, frame.width()), h = Math.max(1, frame.height());
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int i = e.getActionIndex();
                    int id = e.getPointerId(i);
                    float x = e.getX(i) / w, y = 1 - e.getY(i) / h;
                    view.queueEvent(() -> renderer.pointerDown(id, x, y));
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    int n = e.getPointerCount();
                    int[] ids = new int[n];
                    float[] xs = new float[n], ys = new float[n];
                    for (int i = 0; i < n; i++) {
                        ids[i] = e.getPointerId(i);
                        xs[i] = e.getX(i) / w;
                        ys[i] = 1 - e.getY(i) / h;
                    }
                    view.queueEvent(() -> {
                        for (int i = 0; i < n; i++) renderer.pointerMove(ids[i], xs[i], ys[i]);
                    });
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    int id = e.getPointerId(e.getActionIndex());
                    view.queueEvent(() -> renderer.pointerUp(id));
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    view.queueEvent(renderer::pointersCancel);
                    break;
                default:
                    break;
            }
        }

        // Which screens this wallpaper is on. Android 14+ can run separate home and lock
        // wallpapers; before that one wallpaper covers both.
        private int screens() {
            if (Build.VERSION.SDK_INT >= 34) {
                int f = getWallpaperFlags();
                if (f > 0) return f;
            }
            return BOTH;
        }

        // The phone's wallpaper preview shows the look waiting to be set; a real wallpaper shows
        // its screen's look (the very first time, the one being set). A wallpaper on both
        // screens shows the lock look while locked.
        private String lookJson() {
            String v = isPreview() ? prefs.getString(KEY_PENDING_LOOK, null) : null;
            if (v != null) return v;
            int s = screens();
            boolean home = s == BOTH ? !locked : (s & WallpaperManager.FLAG_SYSTEM) != 0;
            v = prefs.getString(home ? KEY_LOOK_HOME : KEY_LOOK_LOCK, null);
            if (v == null) v = prefs.getString(KEY_LOOK, null);
            if (v == null) v = prefs.getString(home ? KEY_LOOK_LOCK : KEY_LOOK_HOME, null);
            if (v == null) v = prefs.getString(KEY_PENDING_LOOK, null);
            return v;
        }

        private String optionsJson() {
            String v = isPreview() ? prefs.getString(KEY_PENDING_OPTIONS, null) : null;
            if (v == null) v = prefs.getString(KEY_OPTIONS, null);
            return v != null ? v : prefs.getString(KEY_PENDING_OPTIONS, null);
        }

        // The app saves here; a running wallpaper picks the change up straight away.
        @Override
        public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (key == null || key.startsWith("look") || KEY_PENDING_LOOK.equals(key)) {
                showLook();
            }
            if (key == null || KEY_OPTIONS.equals(key) || KEY_PENDING_OPTIONS.equals(key)) {
                options = WallpaperLook.Options.parse(optionsJson());
                renderer.auto = options.auto;
            }
        }

        // Moved between screens, e.g. it was on both and the home screen got a new wallpaper.
        @Override
        public void onWallpaperFlagsChanged(int which) {
            super.onWallpaperFlagsChanged(which);
            showLook();
        }

        private void showLook() {
            WallpaperLook look = WallpaperLook.parse(lookJson());
            renderer.setLook(look);
            logLook(look);
        }

        private void logLook(WallpaperLook look) {
            int s = screens();
            String where = isPreview() ? "preview" : s == BOTH ? (locked ? "home+lock (locked)" : "home+lock (unlocked)")
                    : s == WallpaperManager.FLAG_LOCK ? "lock" : "home";
            Log.i("ChromaFlowWallpaper", where + " shows " + look.style);
        }

        @Override
        public void onDestroy() {
            Choreographer.getInstance().removeFrameCallback(this);
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            unregisterReceiver(screenEvents);
            view.destroy();
            super.onDestroy();
        }
    }
}
