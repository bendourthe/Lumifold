"""Builds globe textures and grid thumbnails from the downloaded surface maps.

Inputs: build-output/globes/<name>.jpg (2:1 equirectangular maps from Wikimedia Commons).
Outputs: app/src/main/res/drawable-nodpi/wpg_<id>.jpg (2048 x 1024, or 2048 x 2048 with the
night map stacked under the day map) and wpt_<id>.jpg (a lit sphere thumbnail for the grid).

Run from the repository root:  python tools/globe_assets.py
"""
import numpy as np
from PIL import Image

RES = "app/src/main/res/drawable-nodpi/"
SRC = "build-output/globes/{}.jpg"

# id: (day map, night map or None, atmosphere rgb, tilt degrees)
GLOBES = {
    "earth": ("earth_day", "earth_night", (0.35, 0.6, 1.0), 23.0),
    "moon": ("moon", None, (0.0, 0.0, 0.0), 6.0),
    "mars": ("mars", None, (0.9, 0.55, 0.35), 25.0),
    "jupiter": ("jupiter", None, (0.8, 0.7, 0.55), 3.0),
    "mercury": ("mercury", None, (0.0, 0.0, 0.0), 2.0),
}


def texture(name, size=(2048, 1024)):
    return Image.open(SRC.format(name)).convert("RGB").resize(size, Image.LANCZOS)


def sphere_thumb(day, atm, tilt_deg, w=420, h=478):
    tex = np.asarray(day, np.float32) / 255.0
    th, tw = tex.shape[:2]
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    cx, cy, r = w * 0.5, h * 0.5, w * 0.42
    dx, dy = (xs - cx) / r, (ys - cy) / r
    rr = dx * dx + dy * dy
    inside = rr < 1.0
    nz = np.sqrt(np.clip(1.0 - rr, 0, 1))
    nx, ny = dx, -dy
    t = np.radians(tilt_deg)
    mx, my = np.cos(t) * nx - np.sin(t) * ny, np.sin(t) * nx + np.cos(t) * ny
    lon = np.arctan2(mx, nz) + 0.6
    lat = np.arcsin(np.clip(my, -1, 1))
    u = ((lon / (2 * np.pi) + 0.5) % 1.0) * (tw - 1)
    v = (0.5 - lat / np.pi) * (th - 1)
    surf = tex[v.astype(int).clip(0, th - 1), u.astype(int).clip(0, tw - 1)]
    light = np.array([0.8, 0.25, 0.45], np.float32)
    light /= np.linalg.norm(light)
    lam = nx * light[0] + ny * light[1] + nz * light[2]
    lit = np.clip((lam + 0.08) / 0.33, 0, 1)
    lit = lit * lit * (3 - 2 * lit)
    surf = surf * (0.04 + 0.96 * lit)[..., None] * (0.75 + 0.25 * nz)[..., None]
    rim = (1 - nz) ** 3
    surf += np.array(atm, np.float32) * (rim * (0.3 + 0.9 * lit))[..., None]
    rng = np.random.default_rng(7)
    img = np.zeros((h, w, 3), np.float32)
    stars = rng.random((h, w)) > 0.9975
    img[stars] = rng.uniform(0.4, 1.0, (stars.sum(), 1))
    rd = np.sqrt(rr)
    halo = np.exp(-(np.maximum(rd, 1) - 1) * 18) * 0.6 * (~inside)
    img += np.array(atm, np.float32) * halo[..., None]
    edge = np.clip((1.0 - rd) * r, 0, 1)[..., None]
    img = img * (1 - edge) + surf * edge
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


if __name__ == "__main__":
    for gid, (day, night, atm, tilt) in GLOBES.items():
        d = texture(day)
        if night:
            stacked = Image.new("RGB", (2048, 2048))
            stacked.paste(d, (0, 0))
            stacked.paste(texture(night), (0, 1024))
            stacked.save(RES + f"wpg_{gid}.jpg", quality=85, optimize=True)
        else:
            d.save(RES + f"wpg_{gid}.jpg", quality=85, optimize=True)
        sphere_thumb(d, atm, tilt).save(RES + f"wpt_{gid}.jpg", quality=88)
        print(gid)
