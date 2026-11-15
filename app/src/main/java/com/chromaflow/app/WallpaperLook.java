package com.chromaflow.app;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What the live wallpaper draws, as sent by the menu's "Set as wallpaper" button.
 * The page resolves the style, its Fine-tune changes and the colors, so this only reads values.
 * Without a saved look (wallpaper picked before the app was opened) it shows Neon's defaults.
 */
final class WallpaperLook {
    String style = "neon";
    int mode = 0;
    float[] bg = {0, 0, 0};
    float[][] colors = {rgb("#ff2a6d"), rgb("#05d9e8"), rgb("#d1f7ff"), rgb("#ff9f1c")};
    boolean rainbow = true;
    float amt = 0.26f, ink = 1, curl = 30, dye = 1, vel = 0.2f, pressure = 0.8f, radius = 0.25f, force = 6000;
    float buoy = 0, turb = 0, bloom = 0.2f, colorSpeed = 4.5f, hue = 0, sat = 1, bright = 0.7f, vignette = 0;
    boolean rays = true, shading = true, stars = false, tapSplash = true;
    int quality = 2;   // 0 low, 1 medium, 2 high, 3 ultra
    int symmetry = 1;

    static WallpaperLook parse(String json) {
        WallpaperLook l = new WallpaperLook();
        if (json == null) return l;
        try {
            JSONObject o = new JSONObject(json);
            JSONObject c = o.getJSONObject("cfg");
            l.style = o.optString("style", l.style);
            l.mode = c.optInt("mode", l.mode);
            l.bg = rgb(c.optString("bg", "#000000"));
            JSONArray pal = o.optJSONArray("colors");
            if (pal != null && pal.length() == 4) {
                for (int i = 0; i < 4; i++) l.colors[i] = rgb(pal.getString(i));
            }
            l.rainbow = "rainbow".equals(o.optString("source"));
            l.amt = f(c, "amt", l.amt);
            l.ink = f(c, "ink", l.ink);
            l.curl = f(c, "curl", l.curl);
            l.dye = f(c, "dye", l.dye);
            l.vel = f(c, "vel", l.vel);
            l.pressure = f(c, "pressure", l.pressure);
            l.radius = f(c, "radius", l.radius);
            l.force = f(c, "force", l.force);
            l.buoy = f(c, "buoy", l.buoy);
            l.turb = f(c, "turb", l.turb);
            l.bloom = f(c, "bloom", l.bloom);
            l.colorSpeed = f(c, "colorSpeed", l.colorSpeed);
            l.hue = f(c, "hue", l.hue);
            l.sat = f(c, "sat", l.sat);
            l.bright = f(c, "bright", l.bright);
            l.vignette = f(c, "vignette", l.vignette);
            l.rays = c.optBoolean("rays", l.rays);
            l.shading = c.optBoolean("shading", l.shading);
            l.stars = c.optBoolean("stars", l.stars);
            String q = o.optString("quality", "high");
            l.quality = "low".equals(q) ? 0 : "medium".equals(q) ? 1 : "ultra".equals(q) ? 3 : 2;
            l.symmetry = o.optInt("symmetry", 1);
            l.tapSplash = o.optBoolean("tapSplash", true);
        } catch (Exception e) {
            return new WallpaperLook();
        }
        return l;
    }

    private static float f(JSONObject o, String key, float fallback) {
        return (float) o.optDouble(key, fallback);
    }

    static float[] rgb(String hex) {
        int n = Integer.parseInt(hex.substring(1), 16);
        return new float[]{(n >> 16 & 255) / 255f, (n >> 8 & 255) / 255f, (n & 255) / 255f};
    }

    /** Wallpaper switches from the wallpaper setup. */
    static final class Options {
        boolean auto = true;   // home screen stirs itself when not touched (the lock screen always does)
        int fps = 30;

        static Options parse(String json) {
            Options o = new Options();
            if (json == null) return o;
            try {
                JSONObject j = new JSONObject(json);
                o.auto = j.optBoolean("auto", o.auto);
                o.fps = j.optInt("fps", o.fps) >= 60 ? 60 : 30;
            } catch (Exception ignored) {
                // Keep the defaults.
            }
            return o;
        }
    }
}
