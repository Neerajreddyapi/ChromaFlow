package com.chromaflow.app;

import static android.opengl.GLES30.*;

import android.opengl.GLSurfaceView;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The fluid engine from assets/index.html, ported to native OpenGL ES 3 for the live wallpaper
 * (a wallpaper can't host a WebView). The shaders are copied unchanged and the passes run in the
 * same order, so a style looks the same here as in the app. Keep both in sync when the engine changes.
 * Everything runs on the GL thread; the wallpaper engine hands input over with queueEvent and
 * new looks with setLook, which is applied at the start of the next frame.
 */
final class FluidRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "ChromaFlowWallpaper";
    // sim resolution, dye resolution, pressure iterations: low, medium, high, ultra
    private static final int[][] QUALITY = {{96, 256, 12}, {128, 512, 16}, {128, 1024, 20}, {256, 2048, 24}};
    private static final int BLOOM_RES = 256, BLOOM_ITERS = 8, RAYS_RES = 196;
    private static final float BLOOM_THRESHOLD = 0.6f, BLOOM_KNEE = 0.7f;

    /**
     * Stir on its own (the home screen switch). It pauses while a finger is down and picks up again
     * shortly after, so your strokes take over. The lock screen always stirs, since it can't be touched.
     */
    volatile boolean auto = true;
    volatile boolean locked = false;

    private final Random rnd = new Random();
    private WallpaperLook look;
    private volatile WallpaperLook nextLook;

    // GL objects; all rebuilt in onSurfaceCreated because a new context drops the old ones.
    private boolean ready;
    private Fmt rgba, rg, r;
    private int texType;
    private boolean linear;
    private Prog copyProg, clearProg, splatProg, advectProg, divergenceProg, curlProg, vorticityProg, forceProg,
            pressureProg, gradientProg, prefilterProg, blurProg, bloomFinalProg, raysMaskProg, raysProg, displayProg;
    private int width = 1, height = 1;
    private DoubleFBO dye, velocity, pressure;
    private FBO divergence, curlFB, bloom, sunrays, sunraysTemp;
    private final List<FBO> bloomFBs = new ArrayList<>();
    private int fbQuality = -1;

    private long last;
    private float time;
    private int pendingSplats;
    private final Map<Integer, Pointer> pointers = new HashMap<>();
    private static final float AUTO_RESUME = 1.5f;  // seconds after the last touch
    private float autoPhase = rnd.nextFloat() * 10, autoAge, sinceTouch = AUTO_RESUME;
    private float[] autoPrev, autoColor;

    FluidRenderer(WallpaperLook look) {
        this.look = look;
        pendingSplats = 10 + rnd.nextInt(8);
    }

    /* ---------- Settings and input (called through queueEvent) ---------- */

    /**
     * Any thread. The look is applied when the next frame is drawn: only then is the GL context
     * current. (A paused wallpaper has no context, and rebuilding buffers there broke the fluid.)
     */
    void setLook(WallpaperLook next) {
        nextLook = next;
    }

    private void applyLook(WallpaperLook next) {
        boolean restyle = !next.style.equals(look.style) || next.mode != look.mode;
        look = next;
        autoColor = null;
        for (Pointer p : pointers.values()) p.color = genColor();
        if (!ready) return;
        if (look.quality != fbQuality) initFramebuffers();
        if (restyle) {
            clearDye();
            pendingSplats += 10 + rnd.nextInt(8);
        }
    }

    void pointerDown(int id, float x, float y) {
        Pointer p = new Pointer();
        p.x = x; p.y = y; p.drop = true; p.color = genColor();
        pointers.put(id, p);
    }

    void pointerMove(int id, float x, float y) {
        Pointer p = pointers.get(id);
        if (p == null) return;
        p.dx += correctDX(x - p.x);
        p.dy += correctDY(y - p.y);
        p.x = x; p.y = y;
        p.moved = p.dx != 0 || p.dy != 0;
    }

    void pointerUp(int id) { pointers.remove(id); }

    void pointersCancel() { pointers.clear(); }

    /* ---------- GLSurfaceView.Renderer ---------- */

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        dye = velocity = pressure = null;
        divergence = curlFB = bloom = sunrays = sunraysTemp = null;
        bloomFBs.clear();
        fbQuality = -1;
        pointers.clear();
        ready = setupFormats();
        if (!ready) {
            Log.w(TAG, "No renderable half-float or float textures; showing the background only");
            return;
        }
        glClearColor(0, 0, 0, 1);
        buildPrograms();

        ByteBuffer verts = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder());
        verts.asFloatBuffer().put(new float[]{-1, -1, -1, 1, 1, 1, 1, -1});
        ByteBuffer idx = ByteBuffer.allocateDirect(6 * 2).order(ByteOrder.nativeOrder());
        idx.asShortBuffer().put(new short[]{0, 1, 2, 0, 2, 3});
        int[] buf = new int[2];
        glGenBuffers(2, buf, 0);
        glBindBuffer(GL_ARRAY_BUFFER, buf[0]);
        glBufferData(GL_ARRAY_BUFFER, 8 * 4, verts, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buf[1]);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, 6 * 2, idx, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        pendingSplats = Math.max(pendingSplats, 10 + rnd.nextInt(8));
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        if (ready) initFramebuffers();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        // Real elapsed time, capped so a pause (screen off, app switch) doesn't jump the fluid.
        float dt = last == 0 ? 1 / 60f : Math.min((now - last) / 1e9f, 1 / 30f);
        last = now;
        WallpaperLook next = nextLook;
        if (next != null) {
            nextLook = null;
            applyLook(next);
        }
        if (!ready) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, width, height);
            glClearColor(look.bg[0], look.bg[1], look.bg[2], 1);
            glClear(GL_COLOR_BUFFER_BIT);
            return;
        }
        applyInputs(dt);
        time += dt;
        autoStir(dt);
        step(dt);
        render();
    }

    /* ---------- Formats ---------- */

    private static final class Fmt {
        final int internal, format;
        Fmt(int internal, int format) { this.internal = internal; this.format = format; }
    }

    private boolean setupFormats() {
        String exts = glGetString(GL_EXTENSIONS);
        boolean floatLinear = exts != null && exts.contains("OES_texture_float_linear");
        if (tryFormats(GL_HALF_FLOAT, true,
                new Fmt[]{new Fmt(GL_RGBA16F, GL_RGBA)},
                new Fmt[]{new Fmt(GL_RG16F, GL_RG), new Fmt(GL_RGBA16F, GL_RGBA)},
                new Fmt[]{new Fmt(GL_R16F, GL_RED), new Fmt(GL_RG16F, GL_RG), new Fmt(GL_RGBA16F, GL_RGBA)})) return true;
        return tryFormats(GL_FLOAT, floatLinear,
                new Fmt[]{new Fmt(GL_RGBA32F, GL_RGBA)},
                new Fmt[]{new Fmt(GL_RG32F, GL_RG), new Fmt(GL_RGBA32F, GL_RGBA)},
                new Fmt[]{new Fmt(GL_R32F, GL_RED), new Fmt(GL_RG32F, GL_RG), new Fmt(GL_RGBA32F, GL_RGBA)});
    }

    private boolean tryFormats(int type, boolean lin, Fmt[] rgbaList, Fmt[] rgList, Fmt[] rList) {
        Fmt a = pick(rgbaList, type), b = pick(rgList, type), c = pick(rList, type);
        if (a == null || b == null || c == null) return false;
        rgba = a; rg = b; r = c; texType = type; linear = lin;
        Log.i(TAG, "Using " + (type == GL_HALF_FLOAT ? "half float" : "float") + (lin ? "" : " (manual filtering)"));
        return true;
    }

    private Fmt pick(Fmt[] list, int type) {
        for (Fmt f : list) if (supportsRenderTarget(f, type)) return f;
        return null;
    }

    private boolean supportsRenderTarget(Fmt f, int type) {
        int[] tex = new int[1], fbo = new int[1];
        glGenTextures(1, tex, 0);
        glBindTexture(GL_TEXTURE_2D, tex[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, f.internal, 4, 4, 0, f.format, type, null);
        glGenFramebuffers(1, fbo, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo[0]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex[0], 0);
        boolean ok = glGetError() == GL_NO_ERROR && glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(1, fbo, 0);
        glDeleteTextures(1, tex, 0);
        return ok;
    }

    /* ---------- Programs ---------- */

    private static final class Prog {
        final int id;
        private final Map<String, Integer> locs = new HashMap<>();
        Prog(int id) { this.id = id; }
        void bind() { glUseProgram(id); }
        int u(String name) {
            Integer l = locs.get(name);
            if (l == null) { l = glGetUniformLocation(id, name); locs.put(name, l); }
            return l;
        }
    }

    private int compile(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        int[] ok = new int[1];
        glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) Log.w(TAG, "Shader: " + glGetShaderInfoLog(s));
        return s;
    }

    private Prog program(int vs, String fs) {
        int p = glCreateProgram();
        glAttachShader(p, vs);
        glAttachShader(p, compile(GL_FRAGMENT_SHADER, fs));
        glBindAttribLocation(p, 0, "aPosition");
        glLinkProgram(p);
        int[] ok = new int[1];
        glGetProgramiv(p, GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) Log.w(TAG, "Program: " + glGetProgramInfoLog(p));
        return new Prog(p);
    }

    private void buildPrograms() {
        int vs = compile(GL_VERTEX_SHADER, Shaders.BASE_VS);
        copyProg = program(vs, Shaders.COPY);
        clearProg = program(vs, Shaders.CLEAR);
        splatProg = program(vs, Shaders.SPLAT);
        advectProg = program(vs, (linear ? "" : "#define MANUAL_FILTERING\n") + Shaders.ADVECTION);
        divergenceProg = program(vs, Shaders.DIVERGENCE);
        curlProg = program(vs, Shaders.CURL);
        vorticityProg = program(vs, Shaders.VORTICITY);
        forceProg = program(vs, Shaders.FORCE);
        pressureProg = program(vs, Shaders.PRESSURE);
        gradientProg = program(vs, Shaders.GRADIENT);
        prefilterProg = program(vs, Shaders.PREFILTER);
        blurProg = program(vs, Shaders.BLUR);
        bloomFinalProg = program(vs, Shaders.BLOOM_FINAL);
        raysMaskProg = program(vs, Shaders.RAYS_MASK);
        raysProg = program(vs, Shaders.RAYS);
        displayProg = program(vs, Shaders.DISPLAY);
    }

    private void blit(FBO target) {
        if (target != null) {
            glViewport(0, 0, target.w, target.h);
            glBindFramebuffer(GL_FRAMEBUFFER, target.fbo);
        } else {
            glViewport(0, 0, width, height);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, 0);
    }

    /* ---------- Framebuffers ---------- */

    private static final class FBO {
        int tex, fbo, w, h;
        float tx, ty;
        int attach(int id) {
            glActiveTexture(GL_TEXTURE0 + id);
            glBindTexture(GL_TEXTURE_2D, tex);
            return id;
        }
    }

    private static final class DoubleFBO {
        FBO read, write;
        int w, h;
        float tx, ty;
        void swap() { FBO t = read; read = write; write = t; }
    }

    private FBO createFBO(int w, int h, Fmt fmt, int filter) {
        int[] ids = new int[1];
        glActiveTexture(GL_TEXTURE0);
        glGenTextures(1, ids, 0);
        FBO f = new FBO();
        f.tex = ids[0];
        glBindTexture(GL_TEXTURE_2D, f.tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, fmt.internal, w, h, 0, fmt.format, texType, null);
        glGenFramebuffers(1, ids, 0);
        f.fbo = ids[0];
        glBindFramebuffer(GL_FRAMEBUFFER, f.fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, f.tex, 0);
        glViewport(0, 0, w, h);
        glClear(GL_COLOR_BUFFER_BIT);
        f.w = w; f.h = h; f.tx = 1f / w; f.ty = 1f / h;
        return f;
    }

    private static void free(FBO f) {
        if (f == null) return;
        glDeleteTextures(1, new int[]{f.tex}, 0);
        glDeleteFramebuffers(1, new int[]{f.fbo}, 0);
    }

    private DoubleFBO createDouble(int w, int h, Fmt fmt, int filter) {
        DoubleFBO d = new DoubleFBO();
        d.read = createFBO(w, h, fmt, filter);
        d.write = createFBO(w, h, fmt, filter);
        d.w = w; d.h = h; d.tx = 1f / w; d.ty = 1f / h;
        return d;
    }

    private FBO resizeFBO(FBO target, int w, int h, Fmt fmt, int filter) {
        FBO next = createFBO(w, h, fmt, filter);
        copyProg.bind();
        glUniform1i(copyProg.u("uTexture"), target.attach(0));
        blit(next);
        free(target);
        return next;
    }

    private DoubleFBO resizeDouble(DoubleFBO t, int w, int h, Fmt fmt, int filter) {
        if (t.w == w && t.h == h) return t;
        t.read = resizeFBO(t.read, w, h, fmt, filter);
        free(t.write);
        t.write = createFBO(w, h, fmt, filter);
        t.w = w; t.h = h; t.tx = 1f / w; t.ty = 1f / h;
        return t;
    }

    private int[] getResolution(int res) {
        float aspect = (float) width / height;
        if (aspect < 1) aspect = 1 / aspect;
        int min = res, max = Math.round(res * aspect);
        return width > height ? new int[]{max, min} : new int[]{min, max};
    }

    private void initFramebuffers() {
        int[] q = QUALITY[look.quality];
        fbQuality = look.quality;
        int[] simRes = getResolution(q[0]), dyeRes = getResolution(q[1]);
        int filter = linear ? GL_LINEAR : GL_NEAREST;
        glDisable(GL_BLEND);
        dye = dye != null ? resizeDouble(dye, dyeRes[0], dyeRes[1], rgba, filter) : createDouble(dyeRes[0], dyeRes[1], rgba, filter);
        velocity = velocity != null ? resizeDouble(velocity, simRes[0], simRes[1], rg, filter) : createDouble(simRes[0], simRes[1], rg, filter);
        free(divergence);
        free(curlFB);
        if (pressure != null) { free(pressure.read); free(pressure.write); }
        divergence = createFBO(simRes[0], simRes[1], r, GL_NEAREST);
        curlFB = createFBO(simRes[0], simRes[1], r, GL_NEAREST);
        pressure = createDouble(simRes[0], simRes[1], r, GL_NEAREST);

        int[] br = getResolution(BLOOM_RES);
        free(bloom);
        for (FBO f : bloomFBs) free(f);
        bloomFBs.clear();
        bloom = createFBO(br[0], br[1], rgba, filter);
        for (int i = 0; i < BLOOM_ITERS; i++) {
            int w = br[0] >> (i + 1), h = br[1] >> (i + 1);
            if (w < 2 || h < 2) break;
            bloomFBs.add(createFBO(w, h, rgba, filter));
        }
        int[] rr = getResolution(RAYS_RES);
        free(sunrays);
        free(sunraysTemp);
        sunrays = createFBO(rr[0], rr[1], r, filter);
        sunraysTemp = createFBO(rr[0], rr[1], r, filter);
    }

    /* ---------- Simulation ---------- */

    private void step(float dt) {
        WallpaperLook c = look;
        glDisable(GL_BLEND);

        curlProg.bind();
        glUniform2f(curlProg.u("texelSize"), velocity.tx, velocity.ty);
        glUniform1i(curlProg.u("uVelocity"), velocity.read.attach(0));
        blit(curlFB);

        vorticityProg.bind();
        glUniform2f(vorticityProg.u("texelSize"), velocity.tx, velocity.ty);
        glUniform1i(vorticityProg.u("uVelocity"), velocity.read.attach(0));
        glUniform1i(vorticityProg.u("uCurl"), curlFB.attach(1));
        glUniform1f(vorticityProg.u("curl"), c.curl);
        glUniform1f(vorticityProg.u("dt"), dt);
        blit(velocity.write);
        velocity.swap();

        float rise = c.mode == 4 ? 0 : c.buoy;
        if (rise != 0 || c.turb > 0) {
            forceProg.bind();
            glUniform1i(forceProg.u("uVelocity"), velocity.read.attach(0));
            glUniform1i(forceProg.u("uDye"), dye.read.attach(1));
            glUniform2f(forceProg.u("pull"), 0, rise);
            glUniform1f(forceProg.u("turbulence"), c.turb);
            glUniform1f(forceProg.u("time"), time);
            glUniform1f(forceProg.u("dt"), dt);
            glUniform1f(forceProg.u("aspect"), (float) width / height);
            blit(velocity.write);
            velocity.swap();
        }

        divergenceProg.bind();
        glUniform2f(divergenceProg.u("texelSize"), velocity.tx, velocity.ty);
        glUniform1i(divergenceProg.u("uVelocity"), velocity.read.attach(0));
        blit(divergence);

        clearProg.bind();
        glUniform1i(clearProg.u("uTexture"), pressure.read.attach(0));
        glUniform1f(clearProg.u("value"), c.pressure);
        blit(pressure.write);
        pressure.swap();

        pressureProg.bind();
        glUniform2f(pressureProg.u("texelSize"), velocity.tx, velocity.ty);
        glUniform1i(pressureProg.u("uDivergence"), divergence.attach(0));
        for (int i = 0; i < QUALITY[c.quality][2]; i++) {
            glUniform1i(pressureProg.u("uPressure"), pressure.read.attach(1));
            blit(pressure.write);
            pressure.swap();
        }

        gradientProg.bind();
        glUniform2f(gradientProg.u("texelSize"), velocity.tx, velocity.ty);
        glUniform1i(gradientProg.u("uPressure"), pressure.read.attach(0));
        glUniform1i(gradientProg.u("uVelocity"), velocity.read.attach(1));
        blit(velocity.write);
        velocity.swap();

        advectProg.bind();
        glUniform2f(advectProg.u("texelSize"), velocity.tx, velocity.ty);
        if (!linear) glUniform2f(advectProg.u("dyeTexelSize"), velocity.tx, velocity.ty);
        int vId = velocity.read.attach(0);
        glUniform1i(advectProg.u("uVelocity"), vId);
        glUniform1i(advectProg.u("uSource"), vId);
        glUniform1f(advectProg.u("dt"), dt);
        glUniform1f(advectProg.u("dissipation"), c.vel);
        blit(velocity.write);
        velocity.swap();

        if (!linear) glUniform2f(advectProg.u("dyeTexelSize"), dye.tx, dye.ty);
        glUniform1i(advectProg.u("uVelocity"), velocity.read.attach(0));
        glUniform1i(advectProg.u("uSource"), dye.read.attach(1));
        glUniform1f(advectProg.u("dissipation"), c.dye);
        blit(dye.write);
        dye.swap();
    }

    private void splat(float x, float y, float dx, float dy, float[] color) {
        splatProg.bind();
        float aspect = (float) width / height;
        float rad = look.radius / 100;
        if (aspect > 1) rad *= aspect;
        glUniform1i(splatProg.u("uTarget"), velocity.read.attach(0));
        glUniform1f(splatProg.u("aspectRatio"), aspect);
        glUniform2f(splatProg.u("point"), x, y);
        glUniform3f(splatProg.u("color"), dx, dy, 0);
        glUniform1f(splatProg.u("radius"), rad);
        blit(velocity.write);
        velocity.swap();
        glUniform1i(splatProg.u("uTarget"), dye.read.attach(0));
        glUniform3f(splatProg.u("color"), color[0], color[1], color[2]);
        blit(dye.write);
        dye.swap();
    }

    // A splat plus its mirrored or rotated copies, depending on the symmetry setting.
    private void splatSym(float x, float y, float dx, float dy, float[] color) {
        int n = look.symmetry;
        if (n > 1) {
            float k = (float) Math.sqrt(n);
            color = new float[]{color[0] / k, color[1] / k, color[2] / k};
        }
        if (n == 2 || n == 4) {
            splat(x, y, dx, dy, color);
            splat(1 - x, y, -dx, dy, color);
            if (n == 4) {
                splat(x, 1 - y, dx, -dy, color);
                splat(1 - x, 1 - y, -dx, -dy, color);
            }
        } else if (n == 6) {
            float a = (float) width / height, px = (x - 0.5f) * a, py = y - 0.5f;
            for (int k = 0; k < 6; k++) {
                double t = k * Math.PI / 3;
                float c = (float) Math.cos(t), s = (float) Math.sin(t);
                splat(0.5f + (px * c - py * s) / a, 0.5f + px * s + py * c, dx * c - dy * s, dx * s + dy * c, color);
            }
        } else {
            splat(x, y, dx, dy, color);
        }
    }

    private void randomSplats(int count) {
        float boost = look.mode == 1 ? 3 : Math.min(10, Math.max(2, 1.2f / look.amt));
        for (int i = 0; i < count; i++) {
            float[] c = genColor();
            float x = rnd.nextFloat();
            float y = look.buoy > 0.5f && look.mode != 4 ? rnd.nextFloat() * 0.45f : rnd.nextFloat();
            splat(x, y, 1000 * (rnd.nextFloat() - 0.5f), 1000 * (rnd.nextFloat() - 0.5f),
                    new float[]{c[0] * boost, c[1] * boost, c[2] * boost});
        }
    }

    private void clearDye() {
        clearProg.bind();
        glUniform1i(clearProg.u("uTexture"), dye.read.attach(0));
        glUniform1f(clearProg.u("value"), 0);
        blit(dye.write);
        dye.swap();
        glUniform1i(clearProg.u("uTexture"), velocity.read.attach(0));
        glUniform1f(clearProg.u("value"), 0.3f);
        blit(velocity.write);
        velocity.swap();
    }

    /* ---------- Post effects ---------- */

    private void applyBloom(FBO source, FBO destination) {
        if (bloomFBs.size() < 2) return;
        FBO lastFB = destination;
        glDisable(GL_BLEND);
        prefilterProg.bind();
        float knee = BLOOM_THRESHOLD * BLOOM_KNEE + 0.0001f;
        glUniform3f(prefilterProg.u("curve"), BLOOM_THRESHOLD - knee, knee * 2, 0.25f / knee);
        glUniform1f(prefilterProg.u("threshold"), BLOOM_THRESHOLD);
        glUniform1i(prefilterProg.u("uTexture"), source.attach(0));
        blit(lastFB);

        blurProg.bind();
        for (FBO dest : bloomFBs) {
            glUniform2f(blurProg.u("texelSize"), lastFB.tx, lastFB.ty);
            glUniform1i(blurProg.u("uTexture"), lastFB.attach(0));
            blit(dest);
            lastFB = dest;
        }
        glBlendFunc(GL_ONE, GL_ONE);
        glEnable(GL_BLEND);
        for (int i = bloomFBs.size() - 2; i >= 0; i--) {
            FBO base = bloomFBs.get(i);
            glUniform2f(blurProg.u("texelSize"), lastFB.tx, lastFB.ty);
            glUniform1i(blurProg.u("uTexture"), lastFB.attach(0));
            blit(base);
            lastFB = base;
        }
        glDisable(GL_BLEND);
        bloomFinalProg.bind();
        glUniform2f(bloomFinalProg.u("texelSize"), lastFB.tx, lastFB.ty);
        glUniform1i(bloomFinalProg.u("uTexture"), lastFB.attach(0));
        glUniform1f(bloomFinalProg.u("intensity"), look.bloom);
        blit(destination);
    }

    private void applySunrays(FBO source, FBO mask, FBO destination) {
        glDisable(GL_BLEND);
        raysMaskProg.bind();
        glUniform1i(raysMaskProg.u("uTexture"), source.attach(0));
        blit(mask);
        raysProg.bind();
        glUniform1i(raysProg.u("uTexture"), mask.attach(0));
        blit(destination);
        blurProg.bind();
        glUniform2f(blurProg.u("texelSize"), destination.tx, destination.ty);
        glUniform1i(blurProg.u("uTexture"), destination.attach(0));
        blit(sunraysTemp);
        glUniform1i(blurProg.u("uTexture"), sunraysTemp.attach(0));
        blit(destination);
    }

    /* ---------- Color ---------- */

    private static float[] hsv(float h, float s, float v) {
        h = ((h % 1) + 1) % 1;
        int i = (int) Math.floor(h * 6);
        float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0: return new float[]{v, t, p};
            case 1: return new float[]{q, v, p};
            case 2: return new float[]{p, v, t};
            case 3: return new float[]{p, q, v};
            case 4: return new float[]{t, p, v};
            default: return new float[]{v, p, q};
        }
    }

    private float[] genColor() {
        WallpaperLook l = look;
        float a = l.amt * l.ink;
        float[] c = l.rainbow ? hsv(rnd.nextFloat(), 1, 1) : l.colors[rnd.nextInt(4)].clone();
        if (l.mode == 1) return new float[]{a, a, a};
        if (l.mode == 4) return new float[]{(1 - c[0]) * a, (1 - c[1]) * a, (1 - c[2]) * a};
        if (l.mode == 3) {
            float m = Math.max(Math.max(c[0], c[1]), Math.max(c[2], 0.001f));
            c[0] /= m; c[1] /= m; c[2] /= m;
        }
        return new float[]{c[0] * a, c[1] * a, c[2] * a};
    }

    private float[][] paletteUniforms() {
        if (!look.rainbow) return look.colors;
        float h = time * 0.04f;
        float[][] p = new float[4][];
        for (int i = 0; i < 4; i++) {
            p[i] = look.mode == 1 ? hsv(h + i * 0.07f, 1 - i * 0.2f, 0.45f + i * 0.18f) : hsv(h + i * 0.25f, 0.75f, 1);
        }
        return p;
    }

    /* ---------- Render ---------- */

    private void render() {
        WallpaperLook c = look;
        boolean useBloom = c.bloom > 0.001f && c.mode != 4;
        boolean useRays = c.rays && c.mode == 0;
        if (useBloom) applyBloom(dye.read, bloom);
        if (useRays) applySunrays(dye.read, dye.write, sunrays);
        glDisable(GL_BLEND);
        Prog d = displayProg;
        d.bind();
        float[][] p = paletteUniforms();
        glUniform1i(d.u("uTexture"), dye.read.attach(0));
        glUniform1i(d.u("uBloom"), bloom.attach(1));
        glUniform1i(d.u("uSunrays"), sunrays.attach(2));
        glUniform2f(d.u("uDyeTexel"), dye.tx, dye.ty);
        glUniform1f(d.u("uAspect"), (float) width / height);
        glUniform1f(d.u("uMode"), c.mode);
        glUniform1f(d.u("uShading"), c.shading ? 1 : 0);
        glUniform1f(d.u("uBloomOn"), useBloom ? 1 : 0);
        glUniform1f(d.u("uRaysOn"), useRays ? 1 : 0);
        glUniform1f(d.u("uTime"), time);
        glUniform1f(d.u("uStars"), c.stars && c.mode != 4 ? 1 : 0);
        glUniform1f(d.u("uHue"), (float) (c.hue * Math.PI / 180));
        glUniform1f(d.u("uSat"), c.sat);
        // Ink is drawn on white paper; dimming it would only turn the paper grey.
        glUniform1f(d.u("uBright"), c.mode == 4 ? 1 : c.bright);
        glUniform1f(d.u("uVignette"), c.vignette);
        glUniform3f(d.u("uBg"), c.bg[0], c.bg[1], c.bg[2]);
        glUniform3f(d.u("uP0"), p[0][0], p[0][1], p[0][2]);
        glUniform3f(d.u("uP1"), p[1][0], p[1][1], p[1][2]);
        glUniform3f(d.u("uP2"), p[2][0], p[2][1], p[2][2]);
        glUniform3f(d.u("uP3"), p[3][0], p[3][1], p[3][2]);
        blit(null);
    }

    /* ---------- Input and auto-stir ---------- */

    private static final class Pointer {
        float x, y, dx, dy, age;
        boolean moved, drop;
        float[] color;
    }

    private float correctDX(float d) { float a = (float) width / height; return a < 1 ? d * a : d; }

    private float correctDY(float d) { float a = (float) width / height; return a > 1 ? d / a : d; }

    private void applyInputs(float dt) {
        if (pendingSplats > 0) { randomSplats(pendingSplats); pendingSplats = 0; }
        WallpaperLook l = look;
        sinceTouch = pointers.isEmpty() ? sinceTouch + dt : 0;
        for (Pointer p : pointers.values()) {
            p.age += dt;
            if (l.colorSpeed > 0 && p.age > 1 / l.colorSpeed) { p.age = 0; p.color = genColor(); }
            if (p.drop) {
                p.drop = false;
                if (l.tapSplash) {
                    float[] c = p.color;
                    splatSym(p.x, p.y, 0, 0, new float[]{c[0] * 2.5f, c[1] * 2.5f, c[2] * 2.5f});
                }
            }
            if (p.moved) {
                splatSym(p.x, p.y, p.dx * l.force, p.dy * l.force, p.color);
                p.dx = 0; p.dy = 0; p.moved = false;
            }
        }
    }

    private void autoStir(float dt) {
        if (!locked && (!auto || sinceTouch < AUTO_RESUME)) { autoPrev = null; return; }
        WallpaperLook l = look;
        autoPhase += dt;
        float t = autoPhase;
        float x = (float) (0.5 + 0.32 * Math.sin(t * 0.9) * Math.cos(t * 0.23));
        float y = (float) ((l.buoy > 0.5f && l.mode != 4 ? 0.3 : 0.5) + 0.25 * Math.sin(t * 1.37 + 1));
        autoAge += dt;
        if (autoColor == null || (l.colorSpeed > 0 && autoAge > 1 / l.colorSpeed)) { autoAge = 0; autoColor = genColor(); }
        if (autoPrev != null) {
            splatSym(x, y, correctDX(x - autoPrev[0]) * l.force * 0.8f, correctDY(y - autoPrev[1]) * l.force * 0.8f, autoColor);
        }
        autoPrev = new float[]{x, y};
    }
}
