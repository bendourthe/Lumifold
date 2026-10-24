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
uniform float4 uGlobe;
uniform float4 uGlobe2;
uniform float uNight;

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

// Cosmos: a slow drifting camera, with stars (pixels brighter than their surroundings) twinkling.
half4 cosmos(float2 st, float sigma, float t) {
    float z = 1.0 + 0.018 * (0.5 + 0.5 * sin(t * 0.045));
    float2 pan = 0.005 * float2(sin(t * 0.029), cos(t * 0.021));
    float2 s2 = (st - 0.5) / z + 0.5 + pan;
    half4 c = levels(s2, sigma);
    half4 around = levels(s2, max(sigma, 0.004));
    float lift = dot(float3(c.rgb - around.rgb), float3(0.299, 0.587, 0.114));
    float star = smoothstep(0.04, 0.22, lift);
    float2 cell = floor(s2 * uTexSize / 5.0);
    float h = hash12(cell);
    float tw = 0.5 + 0.5 * sin(t * (1.2 + 3.5 * h) + h * 40.0);
    c.rgb *= half(1.0 + star * (tw * 0.7 - 0.35));
    return c;
}

// Globe: the texture is an equirectangular map (day above night when uNight is set) wrapped on a
// sphere lit from the upper right, spinning at uGlobe.w rad/s, with an atmosphere rim and halo.
half4 globe(float2 st, float sigma, float t) {
    float b = sigma / uPlaneH;
    float2 p = float2(st.x * 2.0 - 1.0, st.y * uPlaneH);
    float2 d = (p - float2(uGlobe.x, uGlobe.y * uPlaneH)) / uGlobe.z;
    float rd = length(d);
    float3 atm = uGlobe2.yzw;
    float3 light = normalize(float3(0.8, 0.25, 0.45));
    float3 col = float3(0.9, 0.95, 1.0) * stars(float2(st.x * 2.0 / uPlaneH, st.y), b, t, 0.93) * 0.8;
    float side = 0.5 + 0.5 * dot(normalize(d + 0.0001), normalize(float2(light.x, -light.y)));
    col += atm * exp(-max(rd - 1.0, 0.0) * 18.0) * 0.6 * (0.3 + 0.7 * side);
    if (rd < 1.02) {
        float3 n = float3(d.x, -d.y, sqrt(max(1.0 - rd * rd, 0.0)));
        float ct = cos(uGlobe2.x);
        float sn = sin(uGlobe2.x);
        float3 m = float3(ct * n.x - sn * n.y, sn * n.x + ct * n.y, n.z);
        float lon = atan(m.x, m.z) + t * uGlobe.w;
        float lat = asin(clamp(m.y, -1.0, 1.0));
        float2 uv = float2(fract(lon / (2.0 * PI) + 0.5), 0.5 - lat / PI);
        float sg = sigma + (1.0 - n.z) * 0.002;
        float vScale = uNight > 0.5 ? 0.5 : 1.0;
        float3 surf = float3(levels(float2(uv.x, uv.y * vScale), sg).rgb);
        float lam = dot(n, light);
        float lit = smoothstep(-0.08, 0.25, lam);
        surf *= (0.03 + 0.97 * lit) * (0.75 + 0.25 * n.z);
        if (uNight > 0.5) {
            float3 city = float3(levels(float2(uv.x, 0.5 + uv.y * 0.5), sg).rgb);
            surf += city * float3(1.0, 0.85, 0.6) * (1.0 - smoothstep(-0.12, 0.06, lam)) * 1.3;
        }
        surf += atm * pow(1.0 - n.z, 3.0) * (0.3 + 0.9 * lit);
        float e = 0.004 / uGlobe.z + b * 4.0;
        col = mix(col, surf, 1.0 - smoothstep(1.0 - e, 1.0 + e, rd));
    }
    return half4(half3(col), 1.0);
}

// Nebula: the soft, large-scale gas sways slowly while all fine detail (stars, thin filaments)
// stays where it is, so at rest the photo is unchanged. Stars twinkle.
half4 nebula(float2 st, float sigma, float t) {
    float2 s2 = (st - 0.5) / 1.02 + 0.5;
    float soft = max(sigma, 0.005);
    float2 o = 0.006 * float2(vnoise(s2 * 2.2 + float2(t * 0.025, 0.0)) - 0.5,
                              vnoise(s2 * 2.2 + float2(5.0, t * 0.02)) - 0.5);
    half4 gas = levels(s2 + o, soft);
    half4 still = levels(s2, sigma);
    half4 around = levels(s2, soft);
    float3 detail = float3(still.rgb - around.rgb);
    float2 cell = floor(s2 * uTexSize / 5.0);
    float h = hash12(cell);
    float tw = 0.5 * sin(t * (1.0 + 3.0 * h) + h * 40.0);
    return half4(half3(float3(gas.rgb) + detail + max(detail, float3(0.0)) * tw), 1.0);
}

half4 content(float2 st, float sigma) {
    if (uStyle > 4.5) { return uMotion > 0.5 ? nebula(st, sigma, uTime) : levels(st, sigma); }
    if (uStyle > 3.5) { return globe(st, sigma, uMotion > 0.5 ? uTime : 0.0); }
    if (uMotion < 0.5 || uStyle < 0.5) { return levels(st, sigma); }
    float t = uTime;
    if (uStyle > 2.5) { return cosmos(st, sigma, t); }
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

    // Palette: c0 space, c1 cool limb, c2 photosphere, c3 hot granule core, c4 prominence.
    private const val SCENE_SUN = """
float hash31(float3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise3(float3 p) {
    float3 i = floor(p);
    float3 f = fract(p);
    float3 u = f * f * (3.0 - 2.0 * f);
    float a = mix(hash31(i), hash31(i + float3(1.0, 0.0, 0.0)), u.x);
    float b2 = mix(hash31(i + float3(0.0, 1.0, 0.0)), hash31(i + float3(1.0, 1.0, 0.0)), u.x);
    float c = mix(hash31(i + float3(0.0, 0.0, 1.0)), hash31(i + float3(1.0, 0.0, 1.0)), u.x);
    float d = mix(hash31(i + float3(0.0, 1.0, 1.0)), hash31(i + float3(1.0, 1.0, 1.0)), u.x);
    return mix(mix(a, b2, u.y), mix(c, d, u.y), u.z);
}

float fbm3(float3 p, float b) {
    float sum = 0.0;
    float amp = 0.5;
    float f = 1.0;
    for (int i = 0; i < 4; i++) {
        float keep = clamp(1.0 - b * f * 12.0, 0.0, 1.0);
        sum += amp * mix(0.5, vnoise3(p * f), keep);
        f *= 2.0;
        amp *= 0.5;
    }
    return sum / 0.9375;
}

// Distances to the nearest and second-nearest animated cell centers: bright granules, dark lanes.
float2 worley3(float3 p, float t) {
    float3 i = floor(p);
    float3 f = fract(p);
    float f1 = 9.0;
    float f2 = 9.0;
    for (int z = -1; z <= 1; z++) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                float3 g = float3(float(x), float(y), float(z));
                float3 o = float3(hash31(i + g), hash31(i + g + 17.1), hash31(i + g + 31.7));
                o = 0.5 + 0.42 * sin(t * 0.25 + 6.283 * o);
                float3 rv = g + o - f;
                float dd = dot(rv, rv);
                if (dd < f1) { f2 = f1; f1 = dd; } else if (dd < f2) { f2 = dd; }
            }
        }
    }
    return sqrt(float2(f1, f2));
}

half4 scene(float2 st, float b, float t) {
    float2 p = float2(st.x * 2.0 - 1.0, st.y * uPlaneH);
    float R = 0.66;
    float2 d = (p - float2(0.45, uPlaneH * 0.52)) / R;
    float r = length(d);
    float e = 0.004 / R + b * 3.0;
    float3 col = uC0 + float3(0.9, 0.95, 1.0) * stars(float2(st.x * 2.0 / uPlaneH, st.y), b, t, 0.95) * 0.5;
    float h = max(r - 1.0, 0.0);
    col += uC2 * 0.45 * exp(-h * 7.0) + uC1 * 0.3 * exp(-h * 1.8);

    if (r > 0.97 && r < 1.7) {
        float2 dir = d / r;
        float ang = atan(dir.y, dir.x);
        // A fringe of small flames (spicules) all around the limb.
        float spic = smoothstep(0.55, 0.85, vnoise3(float3(dir * 45.0, h * 5.0 - t * 0.35))) * exp(-h * 45.0);
        // Plasma arches anchored on the limb, drifting slowly with the rotation.
        float arcs = 0.0;
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            float a0 = hash11(fi * 4.3 + 0.7) * 6.283 + t * 0.004;
            float da = atan(sin(ang - a0), cos(ang - a0));
            float span = 0.1 + 0.12 * hash11(fi * 2.9 + 5.0);
            float ht = (0.1 + 0.16 * hash11(fi * 1.7 + 9.0)) * (0.9 + 0.1 * sin(t * 0.11 + fi * 2.0));
            float ring = length(float2(da / span, h / ht)) - 1.0;
            float thick = 0.22 + 0.14 * vnoise3(float3(dir * 9.0, t * 0.04 + fi));
            float strand = smoothstep(0.25, 0.85, fbm3(float3(dir * 26.0, h * 14.0 - t * 0.2 + fi * 7.0), b));
            float feet = 0.55 + 0.45 * (1.0 - clamp(h / ht, 0.0, 1.0));
            arcs += exp(-ring * ring / (thick * thick)) * strand * feet;
        }
        // An eruption: a plume rises from one region, fades, and returns somewhere else.
        float cycle = t / 45.0;
        float ph = fract(cycle);
        float a1 = hash11(floor(cycle) * 3.7 + 1.0) * 6.283;
        float da1 = atan(sin(ang - a1), cos(ang - a1));
        float reach = 0.15 + 0.55 * ph;
        float cone = 1.0 - smoothstep(0.03 + h * 0.35, 0.06 + h * 0.5, abs(da1));
        float plume = cone * (1.0 - smoothstep(reach * 0.7, reach, h))
            * smoothstep(0.35, 0.75, fbm3(float3(dir * 16.0, h * 6.0 - t * 0.5), b))
            * smoothstep(0.0, 0.1, ph) * (1.0 - smoothstep(0.65, 1.0, ph));
        col += uC4 * (arcs * 1.3 + spic * 0.9 + plume * 1.6) * smoothstep(0.97, 1.0, r);
    }

    if (r < 1.0 + e) {
        float3 n = float3(d.x, -d.y, sqrt(max(1.0 - r * r, 0.0)));
        float spin = t * 0.012;
        float3 m = float3(n.x * cos(spin) + n.z * sin(spin), n.y, n.z * cos(spin) - n.x * sin(spin));
        float keep = clamp(1.0 - b * 140.0, 0.0, 1.0);
        float2 w = worley3(m * 42.0, t);
        float cells = smoothstep(0.02, 0.32, w.y - w.x) * (1.1 - 0.4 * w.x);
        float sup = fbm3(m * 5.0 + float3(t * 0.01), b);
        float spots = 0.0;
        float pen = 0.0;
        for (int i = 0; i < 5; i++) {
            float fi = float(i);
            float lat = (hash11(fi * 3.1 + 1.0) - 0.5) * 0.9;
            float lon = hash11(fi * 7.3 + 2.0) * 6.283;
            float3 c = float3(cos(lat) * sin(lon), sin(lat), cos(lat) * cos(lon));
            float sz = 0.025 + 0.035 * hash11(fi * 5.7 + 3.0);
            float dd = length(m - c);
            spots = max(spots, 1.0 - smoothstep(sz * 0.4, sz * 0.55, dd));
            pen = max(pen, 1.0 - smoothstep(sz * 0.8, sz * 1.1, dd));
        }
        float mu = n.z;
        float limb = 0.3 + 0.7 * pow(mu, 0.5);
        float heat = clamp(limb * (0.62 + 0.3 * mix(0.6, cells, keep)) + 0.18 * (sup - 0.5), 0.0, 1.0);
        float3 s = mix(uC1, uC2, smoothstep(0.1, 0.5, heat));
        s = mix(s, uC3, smoothstep(0.5, 0.95, heat));
        s *= 1.0 - 0.5 * pen - 0.4 * spots;
        s += uC3 * 0.08 * smoothstep(0.6, 0.8, sup) * (1.0 - mu) * (1.0 - mu);
        float flare = pow(max(0.0, sin(t * 0.17 + 1.0)), 24.0) * smoothstep(0.82, 0.95, vnoise3(m * 7.0 + 5.0));
        s += uC3 * flare * 0.7;
        col = mix(col, s * 1.2, 1.0 - smoothstep(1.0 - e, 1.0 + e, r));
    }
    col = 1.0 - exp(-col * 1.5);
    return half4(half3(col), 1.0);
}
"""

    private fun sceneBody(scene: Scene): String = when (scene) {
        Scene.SUN -> SCENE_SUN
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
    float darkK = I;
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

    if (fx >= 90) {
        // Fold effect off: the wallpaper as a flat picture.
        s = 0.0;
    } else if (fx == 0) {
        // Duo Frost, after Apple's model: the glass lifts away from a fixed picture, so blur and
        // shade grow toward the free edge until it falls into black at full fold.
        float prog = inner ? clamp((PI - a) / (0.5 * PI), 0.0, 1.0) : clamp(a / (0.5 * PI), 0.0, 1.0);
        float motion = smoothstep(0.0, 0.75, prog);
        q = mix(qFlat, qPersp, clamp(I, 0.0, 1.0));
        if (moving) {
            sigma = DUO_SIGMA * I * motion * pow(d, 1.5);
            float dg = clamp((d - 0.2) / 0.8, 0.0, 1.0);
            dark = 1.3 * motion * pow(dg, 1.35);
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
    } else if (fx == 12) {
        // Shattered Glass: the moving glass cracks from an impact as it lifts. Radial cracks and
        // partial rings cut it into shards that shift, tilt and catch light, then mend on settling.
        float prog = inner ? clamp((PI - a) / (0.5 * PI), 0.0, 1.0) : clamp(a / (0.5 * PI), 0.0, 1.0);
        float m = smoothstep(0.0, 0.8, prog);
        q = mix(qFlat, qPersp, 0.5);
        if (moving && m > 0.001) {
            float2 p = float2(u, yPlane) - float2(0.62, uPlaneH * 0.42);
            float r = length(p);
            float N = 13.0;
            float A = (atan(p.y, p.x) + PI) / (2.0 * PI) * N;
            A += 0.45 * (vnoise(float2(r * 4.0, A * 0.9)) - 0.5);
            float sector = mod(floor(A), N);
            float hs = hash11(sector + 1.7);
            float reach = 1.7 * m * (0.6 + 0.6 * hs);
            float fa = fract(A);
            float dRad = min(fa, 1.0 - fa) * (2.0 * PI / N) * r;
            float rg = log(r * 9.0 + 1.0) * 3.2 + hs * 0.9;
            float ring = floor(rg);
            float fr = fract(rg);
            float dRing = min(fr, 1.0 - fr) * (r * 9.0 + 1.0) / 28.8;
            float inside = step(r, reach);
            float ringOn = step(0.35, hash12(float2(sector, ring))) * step(r, reach * 0.75);
            float dist = min(mix(9.0, dRad, inside), mix(9.0, dRing, ringOn));
            float wid = 0.0025 + 0.004 * exp(-r * 5.0);
            float line = 1.0 - smoothstep(0.0, wid, dist);
            float halo = 1.0 - smoothstep(0.0, wid * 4.0, dist);
            float2 sid = float2(sector, ring);
            float2 dir = float2(hash12(sid + 3.1), hash12(sid + 7.9)) - 0.5;
            q += dir * 0.03 * I * m * inside * (0.4 + hash12(sid + 1.3));
            float crush = exp(-r * r / 0.0016);
            sigma = SIGMA_MAX * 0.25 * I * m * exp(-r * 7.0);
            dark = (hash12(sid + 5.5) - 0.5) * 0.35 * m * inside + (halo - line) * 0.55 * m + 0.15 * m * d;
            edge = (line * 7.0 + crush * 6.0) * m * min(I, 1.2);
            disp = halo * 0.012 * I * m;
        } else {
            dark = 0.1 * m;
        }
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
