package com.lumifold.wallpaper.render

import com.lumifold.wallpaper.model.Scene

/**
 * AGSL sources. A shader is assembled from a common header, one content stage
 * (`content(st, sigma)`: photo blur pyramid or a procedural scene) and the shared fold effect stage.
 *
 * Content contract: `st` is the shared image plane in [0,1]^2, `sigma` is the Gaussian blur in
 * panel widths. Scenes receive blur in plane-height units and band-limit their own detail, so a
 * frosted frame costs the same as a sharp one.
 */
object FoldShaders {

    const val PYRAMID_LEVELS = 9

    fun photoSource(): String = COMMON + NOISE + PHOTO_CONTENT + EFFECT_MAIN

    fun sceneSource(scene: Scene): String =
        COMMON + PALETTE + NOISE + sceneBody(scene) + SCENE_CONTENT + EFFECT_MAIN

    private const val COMMON = """
uniform float2 uSize;
uniform float uRot;
uniform float uInner;
uniform float uAngle;
uniform float uPlaneH;
uniform float uSurfH;
uniform float uIntensity;
uniform float uTime;
uniform float uEffect;
uniform float3 uEye;

const float PI = 3.14159265;
const float SIGMA_MAX = 0.17;
const float DUO_SIGMA = 0.09;

float hash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
"""

    private const val PHOTO_CONTENT = """
uniform shader L0;
uniform shader L1;
uniform shader L2;
uniform shader L3;
uniform shader L4;
uniform shader L5;
uniform shader L6;
uniform shader L7;
uniform shader L8;
uniform float2 uTexSize;
uniform float uMotion;
uniform float uStyle;

half4 levels(float2 st, float sigma) {
    float2 p = st * uTexSize;
    float sp = sigma * uTexSize.x * 0.5;
    float f = sp <= 2.0 ? sp * 0.5 : log2(sp);
    f = clamp(f, 0.0, 8.0);
    half4 c = half4(0.0);
    float w;
    w = 1.0 - abs(f - 0.0); if (w > 0.0) { c += L0.eval(p) * half(w); }
    w = 1.0 - abs(f - 1.0); if (w > 0.0) { c += L1.eval(p) * half(w); }
    w = 1.0 - abs(f - 2.0); if (w > 0.0) { c += L2.eval(p) * half(w); }
    w = 1.0 - abs(f - 3.0); if (w > 0.0) { c += L3.eval(p) * half(w); }
    w = 1.0 - abs(f - 4.0); if (w > 0.0) { c += L4.eval(p) * half(w); }
    w = 1.0 - abs(f - 5.0); if (w > 0.0) { c += L5.eval(p) * half(w); }
    w = 1.0 - abs(f - 6.0); if (w > 0.0) { c += L6.eval(p) * half(w); }
    w = 1.0 - abs(f - 7.0); if (w > 0.0) { c += L7.eval(p) * half(w); }
    w = 1.0 - abs(f - 8.0); if (w > 0.0) { c += L8.eval(p) * half(w); }
    return c;
}

// Two-phase flow map: two copies slide along the flow half a cycle apart and cross-fade, so the
// motion never visibly resets. A noise phase offset keeps regions from pulsing in sync.
half4 flowSample(float2 st, float2 flow, float period, float sigma, float t) {
    float jitter = vnoise(st * 3.0) * 0.5;
    float ph0 = fract(t / period + jitter);
    float ph1 = fract(t / period + jitter + 0.5);
    float w = abs(1.0 - 2.0 * ph0);
    half4 a = levels(st - flow * (ph0 - 0.5) * period, sigma);
    half4 b = levels(st - flow * (ph1 - 0.5) * period, sigma);
    return mix(a, b, half(w));
}

half4 content(float2 st, float sigma) {
    if (uMotion < 0.5 || uStyle < 0.5) { return levels(st, sigma); }
    float t = uTime;
    st = (st - 0.5) / 1.025 + 0.5;

    if (uStyle < 1.5) {
        float2 p = st * 2.0;
        float ang = sin(p.x * 1.6 + t * 0.11) * 1.7 + sin(p.y * 2.1 - t * 0.09) * 1.3
            + sin((p.x - p.y) * 1.2 + t * 0.07);
        float2 flow = float2(cos(ang), sin(ang)) * 0.010;
        float2 wob = 0.006 * float2(sin(p.y * 4.0 + t * 0.35), cos(p.x * 3.4 - t * 0.3));
        half4 c = flowSample(st + wob, flow, 7.0, sigma, t);
        float sheen = 0.5 + 0.5 * sin(dot(st, float2(2.3, 1.7)) * 3.0 - t * 0.25 + ang * 0.6);
        c.rgb *= half(0.97 + 0.06 * sheen * sheen);
        return c;
    }

    half4 c = levels(st, sigma);
    float luma = dot(float3(c.rgb), float3(0.299, 0.587, 0.114));
    float wave = pow(0.5 + 0.5 * sin(dot(st, float2(9.0, 6.0)) - t * 1.6), 6.0);
    c.rgb *= half(1.0 + 0.55 * smoothstep(0.35, 0.8, luma) * wave);
    return c;
}
"""

    private const val PALETTE = """
uniform float3 uC0;
uniform float3 uC1;
uniform float3 uC2;
uniform float3 uC3;
uniform float3 uC4;
"""

    private const val NOISE = """
float hash11(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

float hash21(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Octaves whose wavelength falls under the blur radius fade to their mean.
float fbm4(float2 p, float b) {
    float sum = 0.0;
    float amp = 0.5;
    float f = 1.0;
    for (int i = 0; i < 4; i++) {
        float keep = clamp(1.0 - b * f * 4.0, 0.0, 1.0);
        sum += amp * mix(0.5, vnoise(p * f), keep);
        f *= 2.0;
        amp *= 0.5;
        p += float2(17.13, 9.71);
    }
    return sum / 0.9375;
}

float stars(float2 p, float b, float t, float threshold) {
    float2 g = p * 70.0;
    float2 cell = floor(g);
    float h = hash21(cell);
    if (h < threshold) { return 0.0; }
    float2 sp = cell + 0.5 + 0.7 * float2(hash21(cell + 3.1) - 0.5, hash21(cell + 7.7) - 0.5);
    float dd = length(g - sp) / 70.0;
    float r = 0.0008 + 0.0012 * hash21(cell + 1.3);
    float re2 = r * r + b * b;
    float tw = 0.75 + 0.25 * sin(t * (0.6 + h) + h * 40.0);
    return exp(-dd * dd / re2) * (r * r) / re2 * tw;
}
"""

    private const val SCENE_CONTENT = """
half4 content(float2 st, float sigma) {
    return scene(st, sigma / uPlaneH, uTime);
}
"""

    // Palette: c0 background, c1..c4 ribbon colors.
    private const val SCENE_RIBBONS = """
half4 scene(float2 st, float b, float t) {
    float asp = 2.0 / uPlaneH;
    float2 p = float2((st.x - 0.5) * asp, st.y - 0.5);
    float ca = cos(0.62);
    float sa = sin(0.62);
    float2 q = float2(ca * p.x + sa * p.y, -sa * p.x + ca * p.y);
    float3 col = mix(uC0 * 1.35, uC0 * 0.5, smoothstep(-0.7, 0.7, q.y + 0.25 * q.x));
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float y = (fi - 2.5) * 0.085
            + 0.07 * sin(q.x * 2.3 - t * 0.32 + fi * 1.3)
            + 0.03 * sin(q.x * 4.7 + t * 0.21 + fi * 2.1)
            + 0.015 * sin(t * 0.17 + fi * 2.9);
        float w = 0.034 + 0.018 * sin(fi * 1.7 + q.x * 1.3 - t * 0.15);
        float we = sqrt(w * w + b * b);
        float dd = (q.y - y) / we;
        float band = exp(-dd * dd) * (w / we);
        float sheen = 0.6 + 0.4 * sin(q.x * 3.1 + fi * 0.8 - t * 0.45);
        float3 c = i == 0 ? uC1 : (i == 1 ? uC2 : (i == 2 ? uC3 : (i == 3 ? uC4 : (i == 4 ? uC2 : uC1))));
        col += c * band * sheen * 0.8;
    }
    col = 1.0 - exp(-col * 1.3);
    col *= 1.0 - 0.3 * dot(p, p);
    return half4(half3(col), 1.0);
}
"""

    // Palette: c0 sky top, c1 horizon, c2 aurora base, c3 aurora crown, c4 ground.
    private const val SCENE_AURORA = """
half4 scene(float2 st, float b, float t) {
    float asp = 2.0 / uPlaneH;
    float2 p = float2(st.x * asp, st.y);
    float3 col = mix(uC0, uC1, smoothstep(0.05, 0.8, st.y));
    col += float3(0.9, 0.95, 1.0) * stars(p, b, t, 0.93) * smoothstep(0.75, 0.2, st.y);
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float base = 0.42 + fk * 0.06
            + 0.07 * sin(p.x * 2.2 + t * 0.16 + fk * 2.0)
            + 0.08 * (fbm4(float2(p.x * 1.5 + t * 0.04, fk * 3.0 + t * 0.02), b * 1.5) - 0.5);
        float above = base - st.y;
        float prof = smoothstep(-0.02 - b, 0.01 + b, above) * exp(-max(above, 0.0) * (5.0 + fk * 2.0));
        prof *= 0.75 + 0.25 * sin(t * 0.4 + p.x * 3.0 + fk * 1.7);
        float streak = fbm4(float2(p.x * 22.0 + fk * 5.0 + t * 0.18, fk + t * 0.05), b * 22.0);
        streak = mix(0.35, 1.0, smoothstep(0.3, 0.75, streak));
        float3 ac = mix(uC2, uC3, smoothstep(0.0, 0.32, above));
        col += ac * prof * streak * (0.8 - fk * 0.2);
    }
    float ridge = 0.8 + 0.05 * sin(p.x * 3.3 + 1.0) + 0.07 * (fbm4(float2(p.x * 3.0, 4.0), b * 3.0) - 0.5);
    float e = 0.0015 + b;
    float m = smoothstep(ridge - e, ridge + e, st.y);
    float3 ground = uC4 + uC2 * 0.05 * (1.0 - smoothstep(ridge, ridge + 0.15, st.y));
    col = mix(col, ground, m);
    col = 1.0 - exp(-col * 1.2);
    return half4(half3(col), 1.0);
}
"""

    // Palette: c0 base, c1..c4 drifting color fields.
    private const val SCENE_MESH = """
half4 scene(float2 st, float b, float t) {
    float asp = 2.0 / uPlaneH;
    float2 p = float2(st.x * asp, st.y);
    p += 0.07 * float2(sin(p.y * 3.1 + t * 0.21), cos(p.x * 2.7 - t * 0.18));
    float3 acc = uC0 * 2.0;
    float wsum = 2.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float2 c = float2(asp * (0.5 + 0.38 * sin(t * (0.06 + fi * 0.017) + fi * 1.9)),
                          0.5 + 0.4 * cos(t * (0.05 + fi * 0.015) + fi * 2.7));
        float d2 = dot(p - c, p - c);
        float w = 1.0 / pow(d2 + 0.03, 1.6);
        float3 c4 = i == 0 ? uC1 : (i == 1 ? uC2 : (i == 2 ? uC3 : uC4));
        acc += c4 * w;
        wsum += w;
    }
    return half4(half3(acc / wsum), 1.0);
}
"""

    // Palette: c0 sky top, c1 horizon haze, c2 sun, c3 lit sand, c4 shadowed sand.
    private const val SCENE_DUNES = """
half4 scene(float2 st, float b, float t) {
    float asp = 2.0 / uPlaneH;
    float2 p = float2(st.x * asp, st.y);
    float3 col = mix(uC0, uC1, smoothstep(0.0, 0.55, st.y));
    float2 sun = float2(0.68 * asp, 0.36);
    float ds = length(p - sun);
    float se = 0.002 + b;
    col = mix(col, uC2, smoothstep(0.065 + se, 0.065 - se, ds));
    col += uC2 * 0.3 * exp(-ds * 7.0);
    float cloud = fbm4(float2(p.x * 2.2 - t * 0.025, st.y * 9.0), b * 2.2);
    col = mix(col, mix(uC1, uC2, 0.35), smoothstep(0.55, 0.8, cloud) * 0.35 * (1.0 - smoothstep(0.1, 0.5, st.y)));
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float depth = fi / 4.0;
        float y0 = 0.5 + fi * 0.095;
        float x = p.x * (1.6 + fi * 0.5) + fi * 1.7 + t * 0.006 * (fi + 1.0);
        float ridge = y0 + 0.045 * sin(x) + 0.022 * sin(x * 2.3 + 1.0) + 0.01 * sin(x * 5.1);
        float slope = cos(x) + 0.5 * cos(x * 2.3 + 1.0);
        float e = 0.0012 + b;
        float m = smoothstep(ridge - e, ridge + e, st.y);
        float shade = clamp(0.5 + 0.45 * slope, 0.0, 1.0);
        shade *= 1.0 - 0.35 * smoothstep(ridge, ridge + 0.15, st.y);
        float3 dune = mix(uC4, uC3, shade);
        dune = mix(dune, uC1, (1.0 - depth) * 0.5);
        float crest = 1.0 - smoothstep(0.0, 0.035, st.y - ridge);
        float haze = fbm4(float2(p.x * 7.0 - t * 0.35 * (1.0 + depth), st.y * 30.0 + fi), b * 7.0);
        dune = mix(dune, uC3 * 1.15, crest * smoothstep(0.5, 0.85, haze) * 0.45);
        col = mix(col, dune, m);
    }
    return half4(half3(col), 1.0);
}
"""

    // Palette: c0 background, c1..c4 light colors.
    private const val SCENE_BOKEH = """
half4 scene(float2 st, float b, float t) {
    float asp = 2.0 / uPlaneH;
    float2 p = float2(st.x * asp, st.y);
    float3 col = mix(uC0 * 1.8, uC0, st.y);
    for (int i = 0; i < 26; i++) {
        float fi = float(i);
        float h1 = hash11(fi * 3.17 + 0.5);
        float h2 = hash11(fi * 7.31 + 1.1);
        float h3 = hash11(fi * 1.93 + 2.7);
        float2 c = float2(h1 * asp, h2)
            + 0.05 * float2(sin(t * 0.16 * (0.5 + h3) + fi), cos(t * 0.13 * (0.5 + h1) + fi * 1.3));
        float r = 0.025 + 0.075 * h3 * h3;
        float re = sqrt(r * r + 4.0 * b * b);
        float dd = length(p - c);
        float disk = 1.0 - smoothstep(re * 0.86 - 0.002, re, dd);
        float rim = smoothstep(re * 0.6, re * 0.95, dd) * disk * 0.35;
        float sel = fract(fi * 0.25);
        float3 lc = sel < 0.125 ? uC1 : (sel < 0.375 ? uC2 : (sel < 0.625 ? uC3 : uC4));
        float a = (0.22 + 0.25 * hash11(fi * 5.7)) * (r * r) / (re * re) * (0.8 + 0.2 * sin(t * 0.3 + fi));
        col += lc * (disk + rim) * a;
    }
    col = 1.0 - exp(-col * 1.4);
    return half4(half3(col), 1.0);
}
"""

    // Palette: c0 top, c1 bottom, c2 line, c3 major line, c4 unused.
    private const val SCENE_CONTOURS = """
half4 scene(float2 st, float b, float t) {
    float asp = 2.0 / uPlaneH;
    float2 p = float2(st.x * asp, st.y) * 2.0 + float2(t * 0.02, -t * 0.014)
        + 0.04 * float2(sin(st.y * 5.0 + t * 0.2), cos(st.x * 4.0 - t * 0.17));
    float bb = b * 2.0;
    float h = fbm4(p, bb);
    float e = 0.004;
    float hx = fbm4(p + float2(e, 0.0), bb);
    float hy = fbm4(p + float2(0.0, e), bb);
    float grad = length(float2(hx - h, hy - h)) / e;
    float n = 18.0;
    float v = h * n;
    float dv = abs(fract(v + 0.5) - 0.5);
    float dist = dv / max(grad * n, 0.001);
    float w = 0.0022;
    float we = sqrt(w * w + bb * bb);
    float line = exp(-(dist * dist) / (we * we)) * (w / we);
    float major = mod(floor(v + 0.5), 5.0) < 0.5 ? 1.0 : 0.0;
    float3 col = mix(uC0, uC1, st.y);
    col += mix(uC2, uC3, major) * line * (0.55 + 0.45 * major);
    return half4(half3(col), 1.0);
}
"""

    // Palette: c0 space, c1 gas A, c2 gas B, c3 hot core, c4 star tint.
    private const val SCENE_NEBULA = """
half4 scene(float2 st, float b, float t) {
    float asp = 2.0 / uPlaneH;
    float2 p = float2(st.x * asp, st.y);
    float bb = b * 1.8;
    float2 q = p * 1.8;
    float2 w = float2(fbm4(q + float2(0.0, t * 0.02), bb),
                      fbm4(q + float2(5.2, 1.3) - float2(t * 0.016, 0.0), bb));
    float n = fbm4(q + 1.6 * w + float2(t * 0.01, 0.0), bb);
    float dens = smoothstep(0.38, 0.9, n);
    float3 col = uC0;
    col += mix(uC1, uC2, smoothstep(0.3, 0.7, w.x)) * dens * 0.9;
    col += uC3 * pow(dens, 3.0) * 0.7;
    col *= 1.0 - 0.5 * smoothstep(0.55, 0.8, w.y) * dens;
    col += uC4 * stars(p, b, t, 0.9);
    col = 1.0 - exp(-col * 1.3);
    return half4(half3(col), 1.0);
}
"""

    // Palette: c0 sky top, c1 horizon, c2 sun, c3 sea, c4 sun glitter.
    private const val SCENE_HORIZON = """
half4 scene(float2 st, float b, float t) {
    float asp = 2.0 / uPlaneH;
    float2 p = float2(st.x * asp, st.y);
    float hz = 0.6;
    float e = 0.0012 + b;
    float3 sky = mix(uC0, uC1, smoothstep(0.0, hz, st.y));
    float2 sun = float2(0.5 * asp, hz - 0.06 + 0.004 * sin(t * 0.05));
    float ds = length(p - sun);
    sky = mix(sky, uC2, smoothstep(0.07 + e, 0.07 - e, ds));
    sky += uC2 * 0.22 * exp(-ds * 6.0);
    float cloud = fbm4(float2(p.x * 1.8 - t * 0.02, st.y * 7.0), b * 1.8);
    sky = mix(sky, uC1 * 1.1, smoothstep(0.55, 0.85, cloud) * 0.3 * smoothstep(0.05, 0.4, st.y) * (1.0 - smoothstep(0.45, hz, st.y)));
    float depth = clamp((st.y - hz) / (1.0 - hz), 0.0, 1.0);
    float3 sea = mix(uC1 * 0.5 + uC3 * 0.5, uC3, smoothstep(0.0, 0.6, depth));
    float swell = sin(st.y * 260.0 / (0.25 + depth) - t * 1.1 + 0.6 * sin(p.x * 6.0 + t * 0.3));
    sea *= 1.0 + 0.06 * swell * clamp(1.0 - b * 120.0, 0.0, 1.0) * smoothstep(0.0, 0.15, depth);
    float colw = 0.03 + 0.18 * depth;
    float cd = (p.x - sun.x) / colw;
    float colm = exp(-cd * cd);
    float keep = clamp(1.0 - b * 160.0, 0.0, 1.0);
    float gl = vnoise(float2(p.x * 90.0 + sin(t * 0.7 + st.y * 40.0), st.y * 420.0 / (0.3 + depth) - t * 1.4));
    gl = mix(0.5, smoothstep(0.55, 0.85, gl), keep);
    sea += uC4 * colm * gl * (0.5 + 0.5 * (1.0 - depth)) * 0.8;
    float m = smoothstep(hz - e, hz + e, st.y);
    return half4(half3(mix(sky, sea, m)), 1.0);
}
"""

    // Palette: c0 shadow, c1 band A, c2 band B, c3 highlight, c4 filament.
    private const val SCENE_IRIS = """
half4 scene(float2 st, float b, float t) {
    float asp = 2.0 / uPlaneH;
    float2 q = float2(st.x * asp, st.y) * 1.3;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        q += 0.32 * float2(sin(q.y * 2.0 + t * 0.17 + fi), sin(q.x * 1.8 - t * 0.15 + fi * 1.7));
    }
    float v = 0.5 + 0.5 * sin(q.x * 2.6 + q.y * 1.9);
    float v2 = 0.5 + 0.5 * sin(q.x * 1.1 - q.y * 2.7 + 1.3);
    float3 col = mix(uC1, uC2, smoothstep(0.1, 0.9, v));
    col = mix(col, uC3, smoothstep(0.55, 1.0, v2) * 0.7);
    col = mix(uC0, col, 0.35 + 0.65 * smoothstep(0.05, 0.6, v * v2 + 0.2));
    float keep = clamp(1.0 - b * 60.0, 0.0, 1.0);
    float lines = abs(sin(q.x * 9.0 + q.y * 7.0));
    col += uC4 * pow(1.0 - lines, 18.0) * 0.35 * keep;
    return half4(half3(col), 1.0);
}
"""

    private fun sceneBody(scene: Scene): String = when (scene) {
        Scene.RIBBONS -> SCENE_RIBBONS
        Scene.AURORA -> SCENE_AURORA
        Scene.MESH -> SCENE_MESH
        Scene.DUNES -> SCENE_DUNES
        Scene.BOKEH -> SCENE_BOKEH
        Scene.CONTOURS -> SCENE_CONTOURS
        Scene.NEBULA -> SCENE_NEBULA
        Scene.HORIZON -> SCENE_HORIZON
        Scene.IRIS -> SCENE_IRIS
    }

    /**
     * Fold effect stage. Maps each surface pixel to the shared image plane along the ray from
     * `uEye` (plane coordinates, z toward the viewer), then applies the selected effect. Cover: the
     * whole display is the moving panel with the hinge on its left edge. Inner: the left half
     * (natural orientation) moves, the right half is stationary.
     */
    private const val EFFECT_MAIN = """
float2 toNatural(float2 p) {
    if (uRot < 0.5) { return p; }
    if (uRot < 1.5) { return float2(uSize.y - p.y, p.x); }
    if (uRot < 2.5) { return float2(uSize.x - p.x, uSize.y - p.y); }
    return float2(p.y, uSize.x - p.x);
}

float2 planeToSt(float2 q, float s) {
    float2 zc = float2(0.5, uPlaneH * 0.5);
    q = zc + (q - zc) / (1.0 + 0.03 * s);
    return float2((q.x + 1.0) * 0.5, q.y / uPlaneH);
}

half4 main(float2 fragCoord) {
    bool swapped = (uRot > 0.5 && uRot < 1.5) || uRot > 2.5;
    float2 nsz = swapped ? uSize.yx : uSize;
    float2 n = toNatural(fragCoord);
    bool inner = uInner > 0.5;
    float unit = inner ? nsz.x * 0.5 : nsz.x;
    float yPlane = n.y / unit + (uPlaneH - uSurfH) * 0.5;

    bool moving = true;
    float u = 0.0;
    float xStat = 0.0;
    if (!inner) {
        u = n.x / unit;
    } else if (n.x < nsz.x * 0.5) {
        u = (nsz.x * 0.5 - n.x) / unit;
    } else {
        moving = false;
        xStat = (n.x - nsz.x * 0.5) / unit;
    }

    float a = clamp(uAngle, 0.0, PI);
    float s = sin(a);
    float c = cos(a);
    float2 qFlat = moving ? float2(inner ? -u : u, yPlane) : float2(xStat, yPlane);
    float2 qPersp = qFlat;
    if (moving) {
        float k = uEye.z / max(uEye.z - u * s, 0.05);
        qPersp = uEye.xy + (float2(u * c, yPlane) - uEye.xy) * k;
    }
    float d = moving ? u : 0.0;
    float I = uIntensity;
    float darkK = min(I, 1.2);
    int fx = int(uEffect + 0.5);

    float2 q = qFlat;
    float sigma = 0.0;
    float dark = 0.0;
    float3 tint = float3(1.0);
    float disp = 0.0;
    float satK = 1.0;
    float edge = 0.0;
    float zoomK = 0.0;
    float2 zoomC = float2(0.0);

    if (fx == 0) {
        // Duo Frost, after Apple's model: the glass lifts away from a fixed picture, so blur and
        // shade grow toward the free edge until it falls into black at full fold.
        float prog = inner ? clamp((PI - a) / (0.5 * PI), 0.0, 1.0) : clamp(a / (0.5 * PI), 0.0, 1.0);
        float motion = smoothstep(0.0, 0.75, prog);
        q = qPersp;
        if (moving) {
            sigma = DUO_SIGMA * I * motion * pow(d, 1.5);
            float dg = clamp((d - 0.2) / 0.8, 0.0, 1.0);
            dark = 2.0 * motion * pow(dg, 1.35);
        } else {
            dark = 0.1 * motion;
        }
    } else if (fx == 1) {
        q = mix(qFlat, qPersp, 0.6);
        if (moving) {
            float ph = u * 16.0 * 2.0 * PI;
            float amp = 0.03 * I * s * (0.25 + 0.75 * d);
            q.x += sin(ph) * amp * (inner ? -1.0 : 1.0);
            sigma = SIGMA_MAX * 0.3 * I * s * (0.3 + 0.7 * d);
            dark = 0.4 * s * (0.5 + 0.5 * d) - 0.05 * s * cos(ph);
        } else {
            dark = 0.2 * s;
        }
    } else if (fx == 2) {
        q = mix(qFlat, qPersp, 0.4);
        float2 center = inner ? float2(0.0, uPlaneH * 0.5) : float2(0.5, uPlaneH * 0.5);
        float r = length(qFlat - center);
        float R = (1.0 - s) * 1.6 + 0.08;
        float m = smoothstep(R, R + 0.55, r);
        float e = clamp(s * 1.6, 0.0, 1.0);
        sigma = SIGMA_MAX * 0.9 * I * m * e;
        dark = 0.6 * m * e;
    } else if (fx == 3) {
        q = mix(qFlat, qPersp, 0.4);
        float dc = moving ? u : xStat;
        float p = inner ? clamp((PI - a) / (0.5 * PI), 0.0, 1.0) : clamp(a / (0.5 * PI), 0.0, 1.0);
        float front = 1.2 - 1.45 * pow(p, 1.5);
        float m = smoothstep(front, front + 0.35, dc);
        float gd = (dc - front) / 0.12;
        float g = exp(-gd * gd) * s;
        tint = mix(float3(1.0), float3(1.18, 1.0, 0.8), clamp(g * 0.6, 0.0, 1.0));
        sigma = SIGMA_MAX * 0.45 * I * m * s;
        dark = 0.93 * m;
    } else if (fx == 4) {
        q = qPersp;
        dark = moving ? 0.25 * s * d : 0.1 * s;
    } else if (fx == 5) {
        q = mix(qFlat, qPersp, 0.8);
        disp = moving ? 0.045 * I * s * d * d : 0.0;
        sigma = moving ? SIGMA_MAX * 0.22 * I * s * d * d * d : 0.0;
        dark = moving ? 0.35 * s * d : 0.12 * s;
    } else if (fx == 6) {
        // Ripple: rings travel outward from the hinge as the angle changes.
        q = mix(qFlat, qPersp, 0.5);
        float2 dv = qFlat - float2(0.0, uPlaneH * 0.5);
        float r = length(dv);
        float ph = r * 16.0 - a * 7.0;
        float fall = exp(-r * 0.9);
        q += (dv / max(r, 0.001)) * sin(ph) * 0.04 * I * s * fall;
        sigma = SIGMA_MAX * 0.15 * I * s;
        dark = 0.2 * s - 0.08 * s * cos(ph) * fall;
    } else if (fx == 7) {
        // Mosaic: square tiles that shrink to pixels as the panel settles.
        q = mix(qFlat, qPersp, 0.5);
        float cell = (moving ? 0.11 * (0.4 + 0.6 * d) : 0.035) * I * s;
        if (cell > 0.002) { q = (floor(q / cell) + 0.5) * cell; }
        dark = moving ? 0.3 * s * d : 0.1 * s;
    } else if (fx == 8) {
        // Crystal: Voronoi facets, each showing a slightly shifted view, with lit edges.
        q = mix(qFlat, qPersp, 0.6);
        float dens = 5.0;
        float2 g = q * dens;
        float2 gi = floor(g);
        float best = 9.0;
        float second = 9.0;
        float2 bestC = float2(0.0);
        for (int yy = -1; yy <= 1; yy++) {
            for (int xx = -1; xx <= 1; xx++) {
                float2 cellId = gi + float2(float(xx), float(yy));
                float2 pt = cellId + float2(hash12(cellId), hash12(cellId + 17.3));
                float dd2 = length(g - pt);
                if (dd2 < best) { second = best; best = dd2; bestC = pt; }
                else if (dd2 < second) { second = dd2; }
            }
        }
        float k = (moving ? (0.35 + 0.65 * d) : 0.25) * I * s;
        q += (bestC / dens - q) * 0.35 * k;
        edge = (1.0 - smoothstep(0.0, 0.06, second - best)) * k;
        sigma = SIGMA_MAX * 0.12 * k;
        dark = 0.25 * k;
    } else if (fx == 9) {
        // Color Bloom: color drains from the free edge and floods back from the crease.
        q = mix(qFlat, qPersp, 0.4);
        float dc = moving ? u : xStat;
        float p = inner ? clamp((PI - a) / (0.5 * PI), 0.0, 1.0) : clamp(a / (0.5 * PI), 0.0, 1.0);
        float front = 1.25 - 1.5 * p;
        float m = smoothstep(front, front + 0.5, dc);
        satK = 1.0 - 0.95 * m * min(I, 1.0);
        dark = 0.35 * m;
        sigma = SIGMA_MAX * 0.1 * I * m * s;
    } else if (fx == 10) {
        // Zoom Burst: radial motion blur toward the screen center.
        q = mix(qFlat, qPersp, 0.5);
        zoomK = 0.28 * I * s * (moving ? (0.4 + 0.6 * d) : 0.6);
        zoomC = inner ? float2(0.0, uPlaneH * 0.5) : float2(0.5, uPlaneH * 0.5);
        dark = 0.2 * s;
    } else {
        // Silk Wave: a fabric fold travels across the panel with the hinge.
        q = mix(qFlat, qPersp, 0.7);
        float reach = moving ? d : 0.3;
        float ph = (moving ? u : xStat) * 11.0 - a * 6.0;
        float amp = 0.05 * I * s * (moving ? d : 0.25);
        q.y += sin(ph) * amp;
        q.x += cos(ph * 0.5) * amp * 0.4;
        dark = 0.25 * s * reach - 0.12 * s * cos(ph) * reach;
    }

    float2 st = planeToSt(q, s);

    half4 col;
    if (disp > 0.0) {
        float2 o = float2(disp * 0.5, 0.0);
        col = content(st, sigma);
        col.r = content(st + o, sigma).r;
        col.b = content(st - o, sigma).b;
    } else if (zoomK > 0.0005) {
        half4 acc = half4(0.0);
        for (int i = 0; i < 5; i++) {
            float2 qi = zoomC + (q - zoomC) * (1.0 - zoomK * float(i) * 0.25);
            acc += content(planeToSt(qi, s), sigma);
        }
        col = acc * 0.2;
    } else {
        col = content(st, sigma);
    }

    // Rays that miss the picture see black; the frost blurs that edge into the image.
    float2 cw = float2(max(sigma * 0.5, 0.003), max(sigma / uPlaneH, 0.003));
    float2 cv = clamp(st / cw, 0.0, 1.0) * clamp((1.0 - st) / cw, 0.0, 1.0);
    float coverage = cv.x * cv.y;

    float bright = clamp(1.0 - dark * darkK, 0.0, 1.0) * coverage;
    float3 rgb = float3(col.rgb);
    float luma = dot(rgb, float3(0.299, 0.587, 0.114));
    rgb = mix(float3(luma), rgb, satK) * bright * tint + edge * 0.1;
    float grain = clamp(sigma / (SIGMA_MAX * 0.5), 0.0, 1.0);
    rgb += (hash12(fragCoord) - 0.5) * 0.035 * grain;
    return half4(half3(rgb), 1.0);
}
"""
}
