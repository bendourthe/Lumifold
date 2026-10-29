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


def sphere_thumb(day, atm, tilt_deg, w=420, h=478, rings=False, unlit=False):
    tex = np.asarray(day, np.float32) / 255.0
    th, tw = tex.shape[:2]
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    cx, cy, r = w * 0.5, h * 0.5, w * (0.2 if rings else 0.42)
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
    light = np.array([-0.75, 0.3, 0.6], np.float32)
    light /= np.linalg.norm(light)
    lam = nx * light[0] + ny * light[1] + nz * light[2]
    lit = np.clip((lam + 0.08) / 0.33, 0, 1)
    lit = lit * lit * (3 - 2 * lit)
    if unlit:
        lit = np.full_like(lit, 0.35)
        surf = np.clip(surf * 1.6, 0, 1)
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
    if rings:
        incl = np.radians(20.0)
        ny_, nz_ = np.cos(incl), np.sin(incl)
        pz = -(ny * ny_) / nz_
        rr = np.sqrt(dx * dx + ny * ny + pz * pz)
        front = ~inside | (pz > nz)
        alpha = np.where((rr > 1.24) & (rr < 1.53), 0.25, 0.0)
        alpha = np.where((rr >= 1.53) & (rr < 1.95), 0.9, alpha)
        alpha = np.where((rr >= 2.03) & (rr < 2.27) & (np.abs(rr - 2.214) > 0.006), 0.6, alpha)
        alpha = alpha * front
        ring_col = np.array([0.82, 0.75, 0.6], np.float32)
        img = img * (1 - alpha[..., None]) + ring_col * alpha[..., None]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def saturn_texture(w=2048, h=1024):
    """Banded Saturn map from the colors of Cassini's 2004 natural-color view (public domain).

    The southern hemisphere below the rings is sampled in a vertical strip (latitudes about -33 to
    -76 degrees), detrended for the lighting falloff, mirrored north and extended smoothly across
    the equator, which the rings hide in that photo.
    """
    src = np.asarray(Image.open("build-output/raw/saturn_rings.jpg").convert("RGB"), np.float32) / 255.0
    cx, cy, rad = 4392.0, 2093.0, 1580.0
    lats, cols = [], []
    for y in range(2960, 3630, 4):
        row = src[y, 4640:5130].mean(axis=0)
        lats.append(np.degrees(np.arcsin(np.clip((cy - y) / rad, -1, 1))))
        cols.append(row)
    lats = -np.array(lats)
    cols = np.array(cols)
    luma = cols @ np.array([0.299, 0.587, 0.114])
    trend = np.polyval(np.polyfit(lats, luma, 2), lats)
    chroma = cols / np.maximum(luma[:, None], 1e-3)
    bands = 0.82 + (luma - trend) * 1.6
    prof_lat = np.concatenate([[0.0, 20.0], lats, [90.0]])
    prof_col = np.concatenate([[chroma[0] * 0.9], [chroma[0] * 0.88], chroma * bands[:, None], [chroma[-1] * 0.55]])
    order = np.argsort(prof_lat)
    prof_lat, prof_col = prof_lat[order], prof_col[order]
    rows = np.abs(90.0 - (np.arange(h) + 0.5) * 180.0 / h)
    tex_rows = np.stack([np.interp(rows, prof_lat, prof_col[:, k]) for k in range(3)], -1)
    rng = np.random.default_rng(3)
    lat_noise = np.interp(np.arange(h), np.linspace(0, h, 180), rng.normal(0, 0.025, 180))
    tex_rows *= (1 + lat_noise)[:, None]
    xs = np.arange(w) / w * 2 * np.pi
    wobble = (np.sin(xs[None, :] * 3 + np.arange(h)[:, None] * 0.05) * 0.008)[..., None]
    tex = np.clip(tex_rows[:, None, :] * (1 + wobble), 0, 1)
    return Image.fromarray((tex * 255).astype(np.uint8))


if __name__ == "__main__":
    sat = saturn_texture()
    sat.save(RES + "wpg_saturn.jpg", quality=88, optimize=True)
    sphere_thumb(sat, (0.8, 0.72, 0.55), 0.0, rings=True).save(RES + "wpt_saturn.jpg", quality=88)
    day, night = texture("earth_day"), texture("earth_night")
    sphere_thumb(night, (0.35, 0.6, 1.0), 23.0, unlit=True).save(RES + "wpt_earth_night.jpg", quality=88)
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
