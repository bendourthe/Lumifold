import numpy as np
from PIL import Image, ImageFilter
def load(n):
    im = Image.open(rf"app\src\main\res\drawable\wp_{n}.jpg").convert("RGB")
    w = 320; h = int(im.height * w / im.width)
    a = np.asarray(im.resize((w, h), Image.LANCZOS)).astype(np.float32) / 255.0
    return im, a
def ss(e0, e1, x): t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)
def smooth_map(lum, r=4):
    im = Image.fromarray((lum * 255).astype(np.uint8))
    m = np.asarray(im.filter(ImageFilter.BoxBlur(r))).astype(np.float32) / 255
    m2 = np.asarray(Image.fromarray((lum * lum * 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(r))).astype(np.float32) / 255
    return np.sqrt(np.clip(m2 - m * m, 0, 1))
def blur(x, r=3):
    return np.asarray(Image.fromarray((np.clip(x, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))).astype(np.float32) / 255
rules = {}
def misty_pines(a, y, lum, sat, std):
    R = ss(0.50, 0.70, lum) * (1 - ss(0.62, 0.72, y))
    B = (1 - ss(0.30, 0.50, lum)) * ss(0.50, 0.62, y)
    return R, 0 * R, B
def fog_castle(a, y, lum, sat, std):
    R = ss(0.55, 0.72, lum) * (std < 0.06) * (1 - ss(0.58, 0.64, y))
    G = ss(0.66, 0.70, y)
    return R, G, 0 * R
def valley_river(a, y, lum, sat, std):
    R = ss(0.70, 0.82, lum) * (std < 0.035) * (1 - ss(0.40, 0.50, y))
    green = (lum < 0.42) & (std > 0.03)
    B = green * ss(0.18, 0.26, y) * (1 - ss(0.68, 0.74, y))
    G = ss(0.70, 0.76, y) * (1 - green)
    return R, G, B
def golden_shore(a, y, lum, sat, std):
    R = 1 - ss(0.38, 0.44, y)
    G = ss(0.42, 0.46, y) * (1 - ss(0.76, 0.86, y))
    return R, G, 0 * R
def desert_road(a, y, lum, sat, std):
    skyish = (a[..., 2] > a[..., 0] - 0.02) & ((a[..., 2] > a[..., 0] + 0.05) | (lum > 0.72))
    R = skyish * (std < 0.07) * (1 - ss(0.45, 0.55, y))
    grass = (lum > 0.55) * (a[..., 0] - a[..., 2] < 0.25) * ss(0.58, 0.66, y)
    xs = np.linspace(0, 1, a.shape[1])[None, :]
    B = grass * ss(0.55, 0.62, xs)
    return R, 0 * R, B
def milky_way(a, y, lum, sat, std):
    R = (1 - ss(0.42, 0.55, y)) * (lum < 0.75)
    return R, 0 * R, 0 * R
fns = [misty_pines, fog_castle, valley_river, golden_shore, desert_road, milky_way]
prev = []
for fn in fns:
    n = fn.__name__
    im, a = load(n)
    h, w, _ = a.shape
    y = np.linspace(0, 1, h)[:, None] * np.ones((1, w))
    lum = a @ np.array([0.299, 0.587, 0.114], dtype=np.float32)
    sat = a.max(-1) - a.min(-1)
    std = smooth_map(lum)
    R, G, B = fn(a, y, lum, sat, std)
    R, G, B = [blur(np.broadcast_to(c, lum.shape).astype(np.float32)) for c in (R, G, B)]
    mask = np.stack([R, G, B], -1)
    Image.fromarray((mask * 255).astype(np.uint8)).save(rf"app\src\main\res\drawable-nodpi\motion_{n}.png", optimize=True)
    ov = a * 0.45 + mask * 0.55
    prev.append(Image.fromarray((np.clip(ov, 0, 1) * 255).astype(np.uint8)))
W = sum(p.width for p in prev) + 10 * (len(prev) + 1); H = max(p.height for p in prev) + 20
sh = Image.new("RGB", (W, H), (20, 20, 24)); x = 10
for p in prev: sh.paste(p, (x, 10)); x += p.width + 10
import os
os.makedirs("build-output", exist_ok=True)
sh.save(os.path.join("build-output", "motion_maps_preview.png")); print("ok")

