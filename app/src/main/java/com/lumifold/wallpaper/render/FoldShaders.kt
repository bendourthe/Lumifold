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

    fun videoSource(): String = COMMON + NOISE + VIDEO_CONTENT + EFFECT_MAIN

    // A video frame has no blur pyramid: frost comes from a 16-tap spiral with a per-pixel
    // rotation, which the effect stage's grain hides.
    private const val VIDEO_CONTENT = """
uniform shader uVideo;
uniform float2 uVidSize;
uniform float uFocusX;
uniform float4 uFrame;

half4 vtap(float2 st) {
    float planeAsp = 2.0 / uPlaneH;
    float vidAsp = uVidSize.x / uVidSize.y;
    if (uFrame.w > 0.5) {
        bool iv = uInner > 0.5;
        float hgt = iv ? uFrame.x : uFrame.y;
        float2 ctr = float2(iv ? 0.0 : uFrame.z, uPlaneH * 0.5);
        float2 fuv = (float2(st.x * 2.0 - 1.0, st.y * uPlaneH) - ctr) / (hgt * float2(vidAsp, 1.0)) + 0.5;
        if (fuv.x < 0.0 || fuv.y < 0.0 || fuv.x > 1.0 || fuv.y > 1.0) { return half4(0.0, 0.0, 0.0, 1.0); }
        float2 edge = smoothstep(0.0, 0.08, min(fuv, 1.0 - fuv));
        half3 fc = max(uVideo.eval(fuv * uVidSize).rgb - 0.025, 0.0) / 0.975;
        return half4(fc * half(edge.x * edge.y), 1.0);
    }
    float2 uv = st;
    if (vidAsp > planeAsp) {
        float k = planeAsp / vidAsp;
        uv.x = (st.x - 0.5) * k + 0.5 + (uFocusX - 0.5) * (1.0 - k);
    } else {
        uv.y = (st.y - 0.5) * vidAsp / planeAsp + 0.5;
    }
    return uVideo.eval(clamp(uv, float2(0.0), float2(1.0)) * uVidSize);
}

half4 content(float2 st, float sigma) {
    if (sigma < 0.0015) { return vtap(st); }
    float2 rad = float2(sigma * 0.5, sigma / uPlaneH) * 1.8;
    float rot = hash12(st * 917.0) * 6.283;
    half4 acc = half4(0.0);
    for (int i = 0; i < 16; i++) {
        float fi = float(i);
        float r = sqrt((fi + 0.5) / 16.0);
        float a = fi * 2.39996 + rot;
        acc += vtap(st + rad * r * float2(cos(a), sin(a)));
    }
    return acc / 16.0;
}
"""

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
uniform shader uStars;
uniform shader uDepth;
uniform float2 uTexSize;
uniform float uMotion;
uniform float uStyle;
uniform float4 uGlobe;
uniform float4 uGlobe2;
uniform float4 uGlobe3;
uniform float3 uLight;

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

// Globe: the texture is an equirectangular map (day above night with uGlobe3.z) wrapped on a sphere
// lit along uLight. Open, the sphere is centered on the inner display; folded, the cover shows its
// left limb. The spin axis rolls by uGlobe2.x and leans uGlobe3.x toward the viewer; uGlobe3.y
// adds Saturn-like rings (radii in planet radii) with shadows both ways.
float4 ringAt(float rr) {
    if (rr < 1.24 || rr > 2.27) { return float4(0.0); }
    float fine = 0.82 + 0.36 * vnoise(float2(rr * 420.0, 0.5));
    float a = 0.0;
    float3 c = float3(0.8, 0.74, 0.62);
    if (rr < 1.53) { a = 0.22; c = float3(0.55, 0.5, 0.45); }
    else if (rr < 1.95) { a = 0.92; c = float3(0.86, 0.79, 0.64); }
    else if (rr < 2.03) { a = 0.06; }
    else if (abs(rr - 2.214) > 0.006) { a = 0.62; c = float3(0.76, 0.71, 0.6); }
    return float4(c, clamp(a * fine, 0.0, 1.0));
}

half4 globe(float2 st, float sigma, float t) {
    float b = sigma / uPlaneH;
    bool innerView = uInner > 0.5;
    float R = innerView ? uGlobe.z : uGlobe.y;
    float2 c = float2(innerView ? 0.0 : uGlobe.x, uPlaneH * 0.5);
    float2 p = float2(st.x * 2.0 - 1.0, st.y * uPlaneH);
    float2 d = (p - c) / R;
    float rd = length(d);
    float3 atm = uGlobe2.yzw;
    float3 light = normalize(uLight);
    float3 col = float3(0.9, 0.95, 1.0) * stars(float2(st.x * 2.0 / uPlaneH, st.y), b, t, 0.93) * 0.8;
    float side = 0.5 + 0.5 * dot(normalize(d + 0.0001), normalize(float2(light.x, -light.y)));
    col += atm * exp(-max(rd - 1.0, 0.0) * 18.0) * 0.6 * (0.3 + 0.7 * side);

    float roll = uGlobe2.x;
    float incl = uGlobe3.x;
    float3 axis = normalize(float3(-sin(roll) * cos(incl), cos(roll) * cos(incl), sin(incl)));
    float3 e2 = normalize(float3(0.0, 0.0, 1.0) - axis * axis.z);
    float3 e1 = cross(axis, e2);
    float3 pos = float3(d.x, -d.y, 0.0);
    float zs = sqrt(max(1.0 - rd * rd, 0.0));
    float e = 0.004 / R + b * 4.0;
    float cover = 1.0 - smoothstep(1.0 - e, 1.0 + e, rd);

    if (rd < 1.02) {
        float3 n = float3(d.x, -d.y, zs);
        float lon = atan(dot(n, e1), dot(n, e2)) + t * uGlobe.w;
        float lat = asin(clamp(dot(n, axis), -1.0, 1.0));
        float2 uv = float2(fract(lon / (2.0 * PI) + 0.5), 0.5 - lat / PI);
        float sg = sigma + (1.0 - n.z) * 0.002;
        bool night = uGlobe3.z > 0.5;
        float3 surf = float3(levels(float2(uv.x, uv.y * (night ? 0.5 : 1.0)), sg).rgb);
        float lam = dot(n, light);
        float lit = smoothstep(-0.08, 0.25, lam);
        if (uGlobe3.y > 0.5) {
            float s = -dot(n, axis) / dot(light, axis);
            if (s > 0.0) {
                float4 shadow = ringAt(length(n + light * s));
                lit *= 1.0 - shadow.a * 0.85;
            }
        }
        surf *= (0.03 + 0.97 * lit) * (0.75 + 0.25 * n.z);
        if (night) {
            float3 city = float3(levels(float2(uv.x, 0.5 + uv.y * 0.5), sg).rgb);
            surf += city * float3(1.0, 0.85, 0.6) * (1.0 - smoothstep(-0.12, 0.06, lam)) * 1.4;
        }
        surf += atm * pow(1.0 - n.z, 3.0) * (0.3 + 0.9 * lit);
        col = mix(col, surf, cover);
    }

    if (uGlobe3.y > 0.5 && abs(axis.z) > 0.01) {
        float z = -(pos.x * axis.x + pos.y * axis.y) / axis.z;
        float3 rp = float3(pos.xy, z);
        float4 ring = ringAt(length(rp));
        if (ring.a > 0.0 && (rd >= 1.0 || z > zs)) {
            float tc = dot(-rp, light);
            float3 nearest = rp + light * tc;
            float shade = (tc > 0.0 && dot(nearest, nearest) < 1.0) ? 0.06 : 0.35 + 0.65 * abs(dot(light, axis));
            col = mix(col, ring.rgb * shade, ring.a * clamp(1.0 - b * 30.0, 0.0, 1.0));
        }
    }
    return half4(half3(col), 1.0);
}

// Depth parallax: the image is rebuilt in depth from uDepth (white is near) and seen by a camera
// that sways and slowly pushes in, so near parts move more than far ones. A few fixed-point steps
// keep foreground edges in place instead of smearing them. At rest (amt = 0) it is the image.
float2 camSway(float t, float amt) {
    return amt * 0.012 * float2(sin(t * 0.23), 0.6 * sin(t * 0.17 + 1.1));
}

float camPush(float t, float amt) {
    return amt * 0.035 * (0.5 - 0.5 * cos(t * 0.11));
}

float2 parallaxUv(float2 st, float t, float amt) {
    float2 base = (st - 0.5) / 1.05 + 0.5;
    float2 cam = camSway(t, amt);
    float push = camPush(t, amt);
    float2 uv = base;
    for (int i = 0; i < 3; i++) {
        float d = float(uDepth.eval(uv * uTexSize).r);
        uv = (base - 0.5) / (1.0 + push * d) + 0.5 - cam * (d - 0.4);
    }
    return uv;
}

half4 depthView(float2 st, float sigma, float t, float amt) {
    return levels(parallaxUv(st, t, amt), sigma);
}

// Nebula: the photo is split offline into starless gas (the pyramid) and stars (uStars), and the
// gas has a depth map. The gas moves in depth with the camera; the stars sit at a middle depth
// and twinkle. At rest, gas plus stars is the photo.
half4 nebula(float2 st, float sigma, float t, float amt) {
    half4 gas = levels(parallaxUv(st, t, amt), sigma);
    float2 base = (st - 0.5) / 1.05 + 0.5;
    float sd = 0.7;
    float2 sst = (base - 0.5) / (1.0 + camPush(t, amt) * sd) + 0.5 - camSway(t, amt) * (sd - 0.4);
    float3 star = float3(uStars.eval(sst * uTexSize).rgb);
    float h = hash12(floor(sst * uTexSize / 4.0));
    float tw = 1.0 + 0.5 * amt * sin(t * (0.9 + 2.6 * h) + h * 40.0);
    float fade = 1.0 - smoothstep(0.002, 0.02, sigma);
    return half4(half3(float3(gas.rgb) + star * tw * fade), 1.0);
}

half4 content(float2 st, float sigma) {
    if (uStyle > 5.5) { return depthView(st, sigma, uMotion > 0.5 ? uTime : 0.0, uMotion); }
    if (uStyle > 4.5) { return nebula(st, sigma, uMotion > 0.5 ? uTime : 0.0, uMotion); }
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

    // Black hole: light paths come from BlackHoleLens (Schwarzschild, units of the horizon radius).
    // For each pixel the ray plane is known, so the thin disk is hit where that plane crosses it:
    // the first crossing is the disk in front, the next ones are the lensed images over and under
    // the shadow and the photon ring. Escaped rays sample a star sphere in their bent direction.
    // The disk turns faster inside, is Doppler-brightened on the approaching side, and its texture
    // is advected on two staggered phases so it never winds up.
    // Open, the hole is centered; folded, its left side and the disk sweep across the cover.
    // Palette: c0 sky tint, c1 hot inner disk, c2 middle, c3 outer, c4 star tint.
    private const val SCENE_BLACK_HOLE = """
uniform shader uLens;
uniform shader uLensEnd;

// 16-bit value split over red and green; constants stay small because GPUs may evaluate in half.
float lensDecode(half4 c) { return float(c.r) * 0.99610895 + float(c.g) * 0.00389105; }

float lensU(float bb, float ph) {
    float2 px = float2(clamp(ph / 9.42477796, 0.0, 1.0) * 767.0 + 0.5, clamp(bb / 16.0, 0.0, 1.0) * 511.0 + 0.5);
    return lensDecode(uLens.eval(px));
}

float lensEnd(float bb) {
    return lensDecode(uLensEnd.eval(float2(clamp(bb / 16.0, 0.0, 1.0) * 511.0 + 0.5, 0.5))) * 9.42477796;
}

float diskNoise(float r, float th, float t, float br, float seed) {
    float om = 0.5 * pow(3.0 / r, 1.5);
    float per = 30.0;
    float ph0 = fract(t / per);
    float ph1 = fract(t / per + 0.5);
    float w = abs(1.0 - 2.0 * ph0);
    float a0 = th - om * ph0 * per;
    float a1 = th - om * ph1 * per;
    float n0 = fbm4(float2(r * 3.2 + seed, 0.0) + 1.6 * float2(cos(a0), sin(a0)), br);
    float n1 = fbm4(float2(r * 3.2 + seed + 41.0, 0.0) + 1.6 * float2(cos(a1), sin(a1)), br);
    return mix(n0, n1, w);
}

half4 scene(float2 st, float bl, float t) {
    bool iv = uInner > 0.5;
    float2 p = float2(st.x * 2.0 - 1.0, st.y * uPlaneH);
    float scale = iv ? 0.082 : 0.12;
    float2 c = float2(iv ? 0.0 : 1.12, uPlaneH * 0.5);
    float2 q = (p - c) / scale;
    q.y = -q.y;
    float bb = length(q);
    float3 e = float3(q / max(bb, 0.0001), 0.0);
    float3 Z = float3(0.0, 0.0, 1.0);
    float elev = 0.13;
    float roll = -0.1;
    float3 n = normalize(float3(-sin(roll) * cos(elev), cos(roll) * cos(elev), sin(elev)));
    float3 d1 = normalize(Z - n * n.z);
    float3 d2 = cross(n, d1);
    float br = bl * uPlaneH / scale * 0.08;

    float phiEnd = bb < 15.9 ? lensEnd(bb) : PI + 2.0 / bb;
    float3 col = float3(0.0);
    if (phiEnd > 0.01) {
        float tilt = bb * 0.07 - (phiEnd - PI);
        float3 dir = -cos(tilt) * Z + sin(tilt) * e;
        float lon = atan(dir.x, -dir.z);
        float lat = asin(clamp(dir.y, -1.0, 1.0));
        float2 sp = float2(lon, lat) * 0.55;
        float s = stars(sp, bl * 0.5, t, 0.86) + 0.6 * stars(sp * 1.7 + 3.0, bl * 0.5, t, 0.9);
        float band = exp(-pow((lat - 0.25 * lon) * 3.0, 2.0)) * fbm4(float2(lon, lat) * 3.0, bl);
        col = uC4 * s * 1.3 + uC0 * (0.6 + 0.8 * band * band);
    }

    float phi0 = atan(-n.z, dot(n, e));
    if (phi0 < 0.0) { phi0 += PI; }
    for (int i = 0; i < 3; i++) {
        float k = float(2 - i);
        float ph = phi0 + k * PI;
        bool alive = bb < 15.9 && (phiEnd < 0.01 || ph < phiEnd);
        float u = alive ? lensU(bb, ph) : 0.0;
        if (u > 0.0834 && u < 0.3334) {
            float r = 1.0 / u;
            float3 P = r * (cos(ph) * Z + sin(ph) * e);
            float th = atan(dot(P, d2), dot(P, d1));
            float3 vdir = normalize(cross(n, P));
            float v = sqrt(0.5 / (r - 1.0));
            float3 kdir = sin(ph) * Z - cos(ph) * e;
            float g = sqrt(1.0 - 1.5 / r) / (1.0 - 0.75 * v * dot(vdir, kdir));
            float I = pow(3.0 / r, 1.8) * smoothstep(3.0, 3.25, r) * (1.0 - smoothstep(8.0, 12.0, r));
            float nz = diskNoise(r, th, t, br, k * 13.0);
            float rings = 0.75 + 0.5 * vnoise(float2(r * 5.0, k));
            float lum = I * (0.55 + 1.0 * nz * rings) * pow(g, 2.5) * 1.5;
            float3 tint = mix(uC3, uC2, smoothstep(0.1, 0.6, lum));
            tint = mix(tint, uC1, smoothstep(0.7, 1.6, lum));
            float a = clamp(lum * 0.9, 0.0, 0.9);
            col = col * (1.0 - a) + tint * lum * 1.6;
        }
    }
    col += uC2 * 0.12 * exp(-pow((bb - 2.6) * 2.2, 2.0));
    col = col / (1.0 + col * 0.35);
    return half4(half3(col), 1.0);
}
"""

    // Spiral galaxy, inclined: a two-armed density wave turns slowly as a whole while the stars,
    // dust and star-forming knots inside it orbit faster near the core (on two staggered phases,
    // so the texture never winds up). Dust lanes trail the inner edge of the arms.
    // Open, the galaxy is centered; folded, its left side sweeps across the cover.
    // Palette: c0 space, c1 warm core, c2 young blue stars, c3 pink star-forming knots, c4 dust.
    private const val SCENE_GALAXY = """
float galaxyField(float r, float ap, float br, float seed) {
    float2 pos = r * float2(cos(ap), sin(ap));
    return fbm4(pos * 7.0 + seed, br);
}

// Resolved stars in the disk, denser where [dens] is high; they orbit with the texture.
float diskStars(float r, float ap, float br, float dens, float seed) {
    float2 pos = r * float2(cos(ap), sin(ap)) * 9.0 + seed;
    float2 cell = floor(pos * 28.0);
    float h = hash21(cell);
    if (h > dens) { return 0.0; }
    float2 sp = cell + 0.5 + 0.6 * float2(hash21(cell + 2.3) - 0.5, hash21(cell + 5.1) - 0.5);
    float d = length(pos * 28.0 - sp);
    float s = 0.18 + 0.25 * hash21(cell + 9.7);
    float e2 = s * s + br * br * 600.0;
    return exp(-d * d / e2) * (s * s) / e2;
}

half4 scene(float2 st, float bl, float t) {
    bool iv = uInner > 0.5;
    float2 p = float2(st.x * 2.0 - 1.0, st.y * uPlaneH);
    float R = iv ? 1.25 : 1.6;
    float2 c = float2(iv ? 0.0 : 1.1, uPlaneH * 0.5);
    float2 q = (p - c) / R;
    float cr = cos(0.45);
    float sr = sin(0.45);
    q = float2(cr * q.x + sr * q.y, -sr * q.x + cr * q.y);
    q.y /= 0.6;
    float r = length(q);
    float a = atan(q.y, q.x);
    float br = bl * uPlaneH / R;

    float pattern = 0.025 * t;
    float lr = log(max(r, 0.012));
    float armPh = 2.0 * (a - pattern - lr * 3.4);
    float arm = pow(0.5 + 0.5 * cos(armPh), 3.0);
    float spur = pow(0.5 + 0.5 * cos(2.0 * armPh + 1.3), 6.0) * 0.35;
    float armAll = clamp(arm + spur, 0.0, 1.0);
    float lane = pow(0.5 + 0.5 * cos(armPh + 0.6), 14.0);

    float per = 40.0;
    float ph0 = fract(t / per);
    float ph1 = fract(t / per + 0.5);
    float w = abs(1.0 - 2.0 * ph0);
    float om = 0.06 / (r + 0.08);
    float a0 = a - om * ph0 * per;
    float a1 = a - om * ph1 * per;
    float tex = mix(galaxyField(r, a0, br, 0.0), galaxyField(r, a1, br, 19.0), w);
    float fine = mix(galaxyField(r * 3.0, a0, br * 3.0, 7.0), galaxyField(r * 3.0, a1, br * 3.0, 31.0), w);

    float disk = exp(-r / 0.3) * smoothstep(1.2, 0.55, r);
    float bulge = exp(-r * r / 0.006) * 2.0 + exp(-r / 0.085) * 0.8;
    float3 col = uC0 + float3(0.9, 0.95, 1.0) * stars(st * float2(2.0 / uPlaneH, 1.0), bl, t, 0.95) * 0.55;
    col += uC1 * disk * (0.25 + 0.35 * tex);
    float armLight = armAll * (0.5 + 0.9 * tex) * disk * 2.2;
    col += uC2 * armLight * (0.55 + 0.7 * fine);

    float dens = clamp((0.12 + armAll * 1.6) * disk, 0.0, 0.85);
    float sOld = mix(diskStars(r, a0, br, dens, 0.0), diskStars(r, a1, br, dens, 11.0), w);
    col += mix(uC2, float3(1.0), 0.5) * sOld * (0.5 + armAll) * 1.4 * smoothstep(0.06, 0.2, r);

    float knotDens = clamp(arm * disk * 0.9 - 0.08, 0.0, 0.5);
    float knots = mix(diskStars(r * 0.7, a0, br, knotDens, 3.0), diskStars(r * 0.7, a1, br, knotDens, 17.0), w);
    col += uC3 * knots * 3.5 * smoothstep(0.12, 0.3, r);

    float dust = lane * smoothstep(0.3, 0.7, tex + 0.15 * fine) * smoothstep(0.05, 0.22, r) * smoothstep(1.1, 0.4, r);
    col = mix(col, col * uC4 * 0.5, clamp(dust * 1.6, 0.0, 0.9));
    col += uC1 * bulge;
    col = col / (1.0 + col * 0.3);
    return half4(half3(col), 1.0);
}
"""

    private fun sceneBody(scene: Scene): String = when (scene) {
        Scene.RIBBONS -> SCENE_RIBBONS
        Scene.MESH -> SCENE_MESH
        Scene.BOKEH -> SCENE_BOKEH
        Scene.CONTOURS -> SCENE_CONTOURS
        Scene.IRIS -> SCENE_IRIS
        Scene.BLACK_HOLE -> SCENE_BLACK_HOLE
        Scene.GALAXY -> SCENE_GALAXY
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
        // Shattered Glass: hairline cracks radiate from an impact and wander; concentric breaks
        // only close to it. Each shard refracts the picture by its own small offset, so the cracks
        // read as breaks in the image rather than drawn lines. They mend as the hinge settles.
        float prog = inner ? clamp((PI - a) / (0.5 * PI), 0.0, 1.0) : clamp(a / (0.5 * PI), 0.0, 1.0);
        float m = smoothstep(0.0, 0.8, prog);
        q = mix(qFlat, qPersp, 0.5);
        if (moving && m > 0.001) {
            float px = 1.0 / unit;
            float2 p = float2(u, yPlane) - float2(0.62, uPlaneH * 0.42);
            float r = length(p);
            float N = 17.0;
            float ang = atan(p.y, p.x);
            float2 around = float2(cos(ang), sin(ang));
            float A = (ang + PI) / (2.0 * PI) * N;
            A += 0.5 * (vnoise(around * 1.3 + float2(r * 3.0, 7.0)) - 0.5)
                + 0.06 * (vnoise(around * 4.0 + float2(r * 45.0, 3.0)) - 0.5);
            float sector = mod(floor(A), N);
            float hs = hash11(sector + 1.7);
            float reach = 1.8 * m * (0.5 + 0.7 * hs);
            float fa = fract(A);
            float dRad = min(fa, 1.0 - fa) * (2.0 * PI / N) * r;
            // Concentric breaks are straight chords between neighboring radial cracks, each wedge
            // breaking at its own distances, which makes the web irregular and polygonal.
            float ca = (floor(A) + 0.5) / N * 2.0 * PI - PI;
            float proj = dot(p, float2(cos(ca), sin(ca)));
            float k = floor(log(max(proj, 0.001) / 0.03) / log(1.6));
            float r0 = 0.03 * pow(1.6, k) * (1.0 + 0.35 * (hash12(float2(sector, k)) - 0.5));
            float r1 = 0.03 * pow(1.6, k + 1.0) * (1.0 + 0.35 * (hash12(float2(sector, k + 1.0)) - 0.5));
            float ring = proj < r0 ? k - 1.0 : (proj < r1 ? k : k + 1.0);
            float dRing = min(abs(proj - r0), abs(proj - r1));
            float inside = step(r, reach);
            float chord = abs(proj - r0) < abs(proj - r1) ? k : k + 1.0;
            float ringOn = step(0.4, hash12(float2(sector, chord + 0.5))) * step(r, min(reach, 0.45));
            float dist = min(mix(9.0, dRad, inside), mix(9.0, dRing, ringOn));
            float tip = mix(0.3, 1.0, 1.0 - smoothstep(reach * 0.6, reach, r));
            float line = (1.0 - smoothstep(0.3 * px, 1.3 * px, dist)) * tip;
            float shade = (1.0 - smoothstep(px, 4.0 * px, dist)) * tip;
            float2 sid = float2(sector, ring * step(r, 0.45));
            float2 dir = float2(hash12(sid + 3.1), hash12(sid + 7.9)) - 0.5;
            q += dir * 0.012 * I * m * inside;
            float crush = 1.0 - smoothstep(0.0, 0.05, r);
            sigma = SIGMA_MAX * 0.18 * I * m * crush;
            dark = (hash12(sid + 5.5) - 0.5) * 0.12 * m * inside + shade * 0.18 * m + 0.12 * m * d;
            edge = line * 3.0 * m;
            disp = line * 0.004 * I * m;
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
