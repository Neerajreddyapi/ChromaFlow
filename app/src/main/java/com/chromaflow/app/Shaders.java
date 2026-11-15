package com.chromaflow.app;

/**
 * GLSL for the live wallpaper, copied unchanged from the engine in assets/index.html.
 * If a shader changes there, copy it here too.
 */
final class Shaders {
    private Shaders() {}

    private static final String HEAD = """
precision highp float;
precision mediump sampler2D;
""" + "\n";

    static final String BASE_VS = """
precision highp float;
attribute vec2 aPosition;
varying vec2 vUv, vL, vR, vT, vB;
uniform vec2 texelSize;
void main () {
  vUv = aPosition * 0.5 + 0.5;
  vL = vUv - vec2(texelSize.x, 0.0);
  vR = vUv + vec2(texelSize.x, 0.0);
  vT = vUv + vec2(0.0, texelSize.y);
  vB = vUv - vec2(0.0, texelSize.y);
  gl_Position = vec4(aPosition, 0.0, 1.0);
}
""";

    static final String COPY = HEAD + """
varying vec2 vUv;
uniform sampler2D uTexture;
void main () { gl_FragColor = texture2D(uTexture, vUv); }
""";

    static final String CLEAR = HEAD + """
varying vec2 vUv;
uniform sampler2D uTexture;
uniform float value;
void main () { gl_FragColor = value * texture2D(uTexture, vUv); }
""";

    static final String SPLAT = HEAD + """
varying vec2 vUv;
uniform sampler2D uTarget;
uniform float aspectRatio;
uniform vec3 color;
uniform vec2 point;
uniform float radius;
void main () {
  vec2 p = vUv - point;
  p.x *= aspectRatio;
  vec3 s = exp(-dot(p, p) / radius) * color;
  gl_FragColor = vec4(texture2D(uTarget, vUv).xyz + s, 1.0);
}
""";

    static final String ADVECTION = HEAD + """
varying vec2 vUv;
uniform sampler2D uVelocity;
uniform sampler2D uSource;
uniform vec2 texelSize;
uniform vec2 dyeTexelSize;
uniform float dt;
uniform float dissipation;
vec4 bilerp (sampler2D sam, vec2 uv, vec2 ts) {
  vec2 st = uv / ts - 0.5;
  vec2 i = floor(st);
  vec2 f = fract(st);
  vec4 a = texture2D(sam, (i + vec2(0.5, 0.5)) * ts);
  vec4 b = texture2D(sam, (i + vec2(1.5, 0.5)) * ts);
  vec4 c = texture2D(sam, (i + vec2(0.5, 1.5)) * ts);
  vec4 d = texture2D(sam, (i + vec2(1.5, 1.5)) * ts);
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
void main () {
#ifdef MANUAL_FILTERING
  vec2 coord = vUv - dt * bilerp(uVelocity, vUv, texelSize).xy * texelSize;
  vec4 result = bilerp(uSource, coord, dyeTexelSize);
#else
  vec2 coord = vUv - dt * texture2D(uVelocity, vUv).xy * texelSize;
  vec4 result = texture2D(uSource, coord);
#endif
  gl_FragColor = result / (1.0 + dissipation * dt);
}
""";

    static final String DIVERGENCE = HEAD + """
varying vec2 vUv, vL, vR, vT, vB;
uniform sampler2D uVelocity;
void main () {
  float L = texture2D(uVelocity, vL).x;
  float R = texture2D(uVelocity, vR).x;
  float T = texture2D(uVelocity, vT).y;
  float B = texture2D(uVelocity, vB).y;
  vec2 C = texture2D(uVelocity, vUv).xy;
  if (vL.x < 0.0) L = -C.x;
  if (vR.x > 1.0) R = -C.x;
  if (vT.y > 1.0) T = -C.y;
  if (vB.y < 0.0) B = -C.y;
  gl_FragColor = vec4(0.5 * (R - L + T - B), 0.0, 0.0, 1.0);
}
""";

    static final String CURL = HEAD + """
varying vec2 vUv, vL, vR, vT, vB;
uniform sampler2D uVelocity;
void main () {
  float L = texture2D(uVelocity, vL).y;
  float R = texture2D(uVelocity, vR).y;
  float T = texture2D(uVelocity, vT).x;
  float B = texture2D(uVelocity, vB).x;
  gl_FragColor = vec4(0.5 * (R - L - T + B), 0.0, 0.0, 1.0);
}
""";

    static final String VORTICITY = HEAD + """
varying vec2 vUv, vL, vR, vT, vB;
uniform sampler2D uVelocity;
uniform sampler2D uCurl;
uniform float curl;
uniform float dt;
void main () {
  float L = texture2D(uCurl, vL).x;
  float R = texture2D(uCurl, vR).x;
  float T = texture2D(uCurl, vT).x;
  float B = texture2D(uCurl, vB).x;
  float C = texture2D(uCurl, vUv).x;
  vec2 force = 0.5 * vec2(abs(T) - abs(B), abs(R) - abs(L));
  force /= length(force) + 0.0001;
  force *= curl * C;
  force.y *= -1.0;
  vec2 v = texture2D(uVelocity, vUv).xy + force * dt;
  v = clamp(v, -1000.0, 1000.0);
  gl_FragColor = vec4(v, 0.0, 1.0);
}
""";

    static final String FORCE = HEAD + """
varying vec2 vUv;
uniform sampler2D uVelocity;
uniform sampler2D uDye;
uniform vec2 pull;
uniform float turbulence;
uniform float time;
uniform float dt;
uniform float aspect;
void main () {
  vec2 v = texture2D(uVelocity, vUv).xy;
  vec3 d = texture2D(uDye, vUv).rgb;
  float m = min(max(d.r, max(d.g, d.b)), 2.0);
  vec2 q = vUv * vec2(aspect, 1.0) * 5.0;
  vec2 n = vec2(sin(q.y * 1.7 + time * 1.3) + sin(q.y * 3.1 - time * 0.9 + q.x),
                cos(q.x * 1.9 - time * 1.1) + cos(q.x * 2.7 + time * 0.7 - q.y));
  v += pull * 120.0 * m * dt;
  v += n * turbulence * 80.0 * m * dt;
  gl_FragColor = vec4(v, 0.0, 1.0);
}
""";

    static final String PRESSURE = HEAD + """
varying vec2 vUv, vL, vR, vT, vB;
uniform sampler2D uPressure;
uniform sampler2D uDivergence;
void main () {
  float L = texture2D(uPressure, vL).x;
  float R = texture2D(uPressure, vR).x;
  float T = texture2D(uPressure, vT).x;
  float B = texture2D(uPressure, vB).x;
  float div = texture2D(uDivergence, vUv).x;
  gl_FragColor = vec4((L + R + B + T - div) * 0.25, 0.0, 0.0, 1.0);
}
""";

    static final String GRADIENT = HEAD + """
varying vec2 vUv, vL, vR, vT, vB;
uniform sampler2D uPressure;
uniform sampler2D uVelocity;
void main () {
  float L = texture2D(uPressure, vL).x;
  float R = texture2D(uPressure, vR).x;
  float T = texture2D(uPressure, vT).x;
  float B = texture2D(uPressure, vB).x;
  vec2 v = texture2D(uVelocity, vUv).xy - vec2(R - L, T - B);
  gl_FragColor = vec4(v, 0.0, 1.0);
}
""";

    static final String PREFILTER = HEAD + """
varying vec2 vUv;
uniform sampler2D uTexture;
uniform vec3 curve;
uniform float threshold;
void main () {
  vec3 c = texture2D(uTexture, vUv).rgb;
  float br = max(c.r, max(c.g, c.b));
  float rq = clamp(br - curve.x, 0.0, curve.y);
  rq = curve.z * rq * rq;
  c *= max(rq, br - threshold) / max(br, 0.0001);
  gl_FragColor = vec4(c, 0.0);
}
""";

    static final String BLUR = HEAD + """
varying vec2 vUv, vL, vR, vT, vB;
uniform sampler2D uTexture;
void main () {
  vec4 s = texture2D(uTexture, vL) + texture2D(uTexture, vR) + texture2D(uTexture, vT) + texture2D(uTexture, vB);
  gl_FragColor = s * 0.25;
}
""";

    static final String BLOOM_FINAL = HEAD + """
varying vec2 vUv, vL, vR, vT, vB;
uniform sampler2D uTexture;
uniform float intensity;
void main () {
  vec4 s = texture2D(uTexture, vL) + texture2D(uTexture, vR) + texture2D(uTexture, vT) + texture2D(uTexture, vB);
  gl_FragColor = s * 0.25 * intensity;
}
""";

    static final String RAYS_MASK = HEAD + """
varying vec2 vUv;
uniform sampler2D uTexture;
void main () {
  vec3 c = texture2D(uTexture, vUv).rgb;
  float br = max(c.r, max(c.g, c.b));
  gl_FragColor = vec4(1.0 - min(br * 20.0, 0.8), 0.0, 0.0, 1.0);
}
""";

    static final String RAYS = HEAD + """
varying vec2 vUv;
uniform sampler2D uTexture;
void main () {
  vec2 coord = vUv;
  vec2 dir = (vUv - 0.5) * (0.3 / 16.0);
  float illum = 1.0;
  float sum = texture2D(uTexture, vUv).r;
  float norm = 1.0;
  for (int i = 0; i < 16; i++) {
    coord -= dir;
    sum += texture2D(uTexture, coord).r * illum;
    norm += illum;
    illum *= 0.95;
  }
  gl_FragColor = vec4(sum / norm, 0.0, 0.0, 1.0);
}
""";

    static final String DISPLAY = HEAD + """
varying vec2 vUv;
uniform sampler2D uTexture;
uniform sampler2D uBloom;
uniform sampler2D uSunrays;
uniform vec2 uDyeTexel;
uniform float uAspect, uMode, uShading, uBloomOn, uRaysOn, uTime, uStars, uHue, uSat, uBright, uVignette;
uniform vec3 uBg, uP0, uP1, uP2, uP3;
const vec3 LIGHT = vec3(-0.36, 0.48, 0.80);

float hash (vec2 p) { vec3 q = fract(vec3(p.xyx) * 0.1031); q += dot(q, q.yzx + 33.33); return fract((q.x + q.y) * q.z); }
float lum (vec3 c) { return max(c.r, max(c.g, c.b)); }
vec3 dyeAt (vec2 uv) { return texture2D(uTexture, uv).rgb; }
vec3 heightNormal (float k) {
  float l = lum(dyeAt(vUv - vec2(uDyeTexel.x, 0.0)));
  float r = lum(dyeAt(vUv + vec2(uDyeTexel.x, 0.0)));
  float b = lum(dyeAt(vUv - vec2(0.0, uDyeTexel.y)));
  float t = lum(dyeAt(vUv + vec2(0.0, uDyeTexel.y)));
  return normalize(vec3(l - r, b - t, k));
}
vec3 ramp (float t) {
  t = clamp(t, 0.0, 1.0) * 4.0;
  if (t < 1.0) return mix(uBg, uP0, t);
  if (t < 2.0) return mix(uP0, uP1, t - 1.0);
  if (t < 3.0) return mix(uP1, uP2, t - 2.0);
  return mix(uP2, uP3, t - 3.0);
}
vec3 hueRotate (vec3 c, float a) {
  const vec3 k = vec3(0.57735);
  float ca = cos(a);
  return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
}
vec3 toGamma (vec3 c) { return max(1.055 * pow(max(c, vec3(0.0)), vec3(0.416667)) - 0.055, vec3(0.0)); }
float caustics (vec2 p, float t) {
  float v = 0.0;
  for (int k = 0; k < 3; k++) {
    float fk = float(k);
    p += 0.5 * vec2(sin(p.y * 1.3 + t * 0.7 + fk * 2.1), cos(p.x * 1.1 - t * 0.6 + fk * 1.7));
    v += abs(sin(p.x + p.y * 0.6) * cos(p.y - p.x * 0.4));
  }
  return pow(max(1.0 - v / 3.0, 0.0), 7.0);
}

void main () {
  vec3 c = dyeAt(vUv);
  float h = lum(c);
  vec3 hue = c / max(h, 0.0001);
  vec3 bloom = uBloomOn > 0.5 ? toGamma(texture2D(uBloom, vUv).rgb) : vec3(0.0);
  vec3 col;

  if (uMode < 0.5) {            // glow
    if (uShading > 0.5) { vec3 n = heightNormal(0.06); c *= 0.55 + 0.6 * clamp(dot(n, LIGHT), 0.0, 1.0); }
    if (uRaysOn > 0.5) { float s = 0.55 + 0.9 * texture2D(uSunrays, vUv).r; c *= s; bloom *= s; }
    c += bloom;
    vec3 bg = uBg;
    if (uStars > 0.5) {
      bg += vec3(0.25, 0.08, 0.4) * 0.22 * max(1.0 - length((vUv - 0.5) * vec2(uAspect, 1.0)) * 1.2, 0.0);
    }
    col = c + bg * (1.0 - clamp(lum(c), 0.0, 1.0));
  } else if (uMode < 1.5) {     // heat ramp
    float t = h;
    if (uShading > 0.5) { vec3 n = heightNormal(0.05); t *= 0.65 + 0.5 * clamp(dot(n, LIGHT), 0.0, 1.0); }
    col = ramp(1.0 - exp(-t * 2.4));
    col += bloom.r * mix(uP1, uP3, 0.4);
  } else if (uMode < 2.5) {     // waves
    vec3 n = heightNormal(0.08);
    float diff = clamp(dot(n, LIGHT), 0.0, 1.0);
    float hh = 1.0 - exp(-h * 1.6);
    float rip = 0.5 + 0.5 * sin(hh * 26.0 - uTime * 2.5);
    float crest = smoothstep(0.82, 1.0, rip);
    float cover = smoothstep(0.0, 0.05, h);
    vec3 w = hue * (0.12 + 0.88 * rip) * (0.5 + 0.6 * diff) + crest * 0.12 * hue;
    col = mix(uBg, w, cover) + bloom * 0.6;
  } else if (uMode < 3.5) {     // chrome
    vec3 n = heightNormal(0.12);
    vec3 r = reflect(vec3(0.0, 0.0, -1.0), n);
    float e = r.y * 0.5 + 0.5;
    vec3 env = mix(uP2, uP1, smoothstep(0.25, 0.55, e));
    env = mix(env, uP0, smoothstep(0.55, 0.9, e));
    env += uP3 * pow(max(r.x, 0.0), 2.0) * 0.7;
    float spec = pow(max(dot(r, LIGHT), 0.0), 40.0);
    vec3 metal = env * mix(vec3(1.0), hue, 0.25) + spec;
    col = mix(uBg, metal, smoothstep(0.03, 0.14, h)) + bloom * 0.4;
  } else if (uMode < 4.5) {     // ink on paper
    vec3 paper = uBg * (0.975 + 0.04 * hash(floor(gl_FragCoord.xy * 0.5)));
    col = paper * exp(-c * 2.6);
  } else {                      // water
    vec3 n = heightNormal(0.06);
    vec2 uv = vUv + n.xy * 0.035;
    vec3 deep = mix(uBg, uP1 * 0.45, clamp(uv.y * 0.7 + 0.15, 0.0, 1.0));
    float cs = caustics(uv * vec2(uAspect, 1.0) * 5.5, uTime * 0.8);
    vec3 floorC = deep + uP2 * cs * 0.28;
    float depth = 1.0 - exp(-h * 1.8);
    vec3 water = mix(floorC, hue * 0.85 + floorC * 0.35, depth * 0.8);
    float spec = pow(max(dot(reflect(vec3(0.0, 0.0, -1.0), n), LIGHT), 0.0), 90.0);
    float fres = pow(1.0 - n.z, 2.0);
    col = water + spec * smoothstep(0.0, 0.05, h) + uP0 * fres * 0.5 + bloom * 0.5;
  }
  if (uStars > 0.5) {
    float r = hash(floor(gl_FragCoord.xy / 3.0));
    float tw = 0.55 + 0.45 * sin(uTime * (1.5 + r * 3.0) + r * 40.0);
    col += vec3(0.9, 0.92, 1.0) * step(0.992, r) * tw * 0.9 * (1.0 - clamp(h * 4.0, 0.0, 1.0));
  }
  col = hueRotate(col, uHue);
  float gray = dot(col, vec3(0.2126, 0.7152, 0.0722));
  col = max(mix(vec3(gray), col, uSat), 0.0) * uBright;
  col *= 1.0 - uVignette * smoothstep(0.5, 1.45, length((vUv - 0.5) * 2.0));
  col += (hash(gl_FragCoord.xy + fract(uTime) * 91.0) - 0.5) / 255.0;
  gl_FragColor = vec4(col, 1.0);
}
""";
}
