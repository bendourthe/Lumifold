"""Splits nebula photos into a starless gas layer and a star layer for the NEBULA motion style.

Stars are compact bright components of a white top-hat (image minus its grey opening); elongated
or large components (gas filaments) stay in the gas layer. Their pixels are replaced by the opened
image, feathered, and the difference becomes the additive star layer. Gas + stars reproduces the
original photo.

Outputs, per id: wp_<id>.jpg (gas, replacing the photo), wpn_<id>.jpg (stars on black) and
wpt_<id>.jpg (a thumbnail of the original). Keeps the original in build-output/nebula_originals.

Run from the repository root:  python tools/nebula_layers.py
"""
import os
import shutil

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

RES = "app/src/main/res/drawable-nodpi/"
IDS = ["pillars_webb", "pillars_hubble", "cosmic_cliffs", "tarantula", "orion_nebula", "helix",
       "ring_nebula", "crab_nebula", "cygnus_loop"]


def split(img):
    luma = img @ np.array([0.299, 0.587, 0.114], np.float32)
    yy, xx = np.mgrid[-6:7, -6:7]
    disk = xx * xx + yy * yy <= 36
    opened = np.stack([ndi.grey_opening(img[..., k], footprint=disk) for k in range(3)], -1)
    opened = ndi.gaussian_filter(opened, sigma=(2.5, 2.5, 0))
    tophat = luma - (opened @ np.array([0.299, 0.587, 0.114], np.float32))
    cand = tophat > 0.05
    labels, n = ndi.label(cand)
    if n:
        idx = np.arange(1, n + 1)
        area = ndi.sum(np.ones_like(luma), labels, idx)
        slices = ndi.find_objects(labels)
        keep = np.zeros(n + 1, bool)
        for i, sl in enumerate(slices):
            h = sl[0].stop - sl[0].start
            w = sl[1].stop - sl[1].start
            compact = max(h, w) <= 3.0 * max(1, min(h, w)) and area[i] <= 400
            keep[i + 1] = compact
        mask = keep[labels]
    else:
        mask = cand
    mask = ndi.binary_dilation(mask, iterations=3)
    soft = np.clip(ndi.gaussian_filter(mask.astype(np.float32), 1.5) * 1.5, 0, 1)[..., None]
    gas = img * (1 - soft) + np.minimum(img, opened) * soft
    stars = np.clip(img - gas, 0, 1)
    return gas, stars


if __name__ == "__main__":
    os.makedirs("build-output/nebula_originals", exist_ok=True)
    for pid in IDS:
        keep = f"build-output/nebula_originals/wp_{pid}.jpg"
        if not os.path.exists(keep):
            shutil.copy(RES + f"wp_{pid}.jpg", keep)
        orig = Image.open(keep).convert("RGB")
        img = np.asarray(orig, np.float32) / 255.0
        gas, stars = split(img)
        Image.fromarray((gas * 255).round().astype(np.uint8)).save(RES + f"wp_{pid}.jpg", quality=82, optimize=True)
        Image.fromarray((stars * 255).round().astype(np.uint8)).save(RES + f"wpn_{pid}.jpg", quality=90, optimize=True)
        thumb = orig.copy()
        thumb.thumbnail((640, 640))
        thumb.save(RES + f"wpt_{pid}.jpg", quality=86)
        print(pid, f"stars {float((stars.max(-1) > 0.05).mean() * 100):.1f}% of pixels")
