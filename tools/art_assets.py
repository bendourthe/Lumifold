"""Builds the Illustrated category's artwork assets from build-output/art_search/picks.json.

Each pick is a public-domain or CC0 scan on Wikimedia Commons. The script downloads it, crops it
to the pick's crop_rect (already the shape of the shared image plane, inside the clean art area),
resizes it to 2100 px wide as wp_<id>.jpg and estimates its depth map wpd_<id>.jpg.

Run from the repository root:  python tools/art_assets.py
"""
import io
import json
import os
import sys
import urllib.request

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from depth_maps import depth_image  # noqa: E402

Image.MAX_IMAGE_PIXELS = None
RES = "app/src/main/res/drawable-nodpi/"
UA = {"User-Agent": "LumifoldAssetFetch/1.0 (benjamin.dourthe@gmail.com)"}
LICENSES = ("public domain", "cc0")

if __name__ == "__main__":
    picks = json.load(open("build-output/art_search/picks.json", encoding="utf-8"))
    os.makedirs("build-output/art_src", exist_ok=True)
    for p in picks:
        if not p["license"].lower().startswith(LICENSES):
            raise SystemExit(f"{p['id']}: license {p['license']!r} is not public domain or CC0")
        cache = f"build-output/art_src/{p['id']}.jpg"
        if not os.path.exists(cache):
            url = p.get("download_url") or p["original_url"]
            data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120).read()
            open(cache, "wb").write(data)
        img = Image.open(cache).convert("RGB")
        x0, y0, x1, y1 = p["crop_rect"]
        w, h = img.size
        crop = img.crop((round(x0 * w), round(y0 * h), round(x1 * w), round(y1 * h)))
        out_w = 2100
        out_h = round(out_w * crop.height / crop.width)
        crop = crop.resize((out_w, out_h), Image.LANCZOS)
        crop.save(RES + f"wp_{p['id']}.jpg", quality=88, optimize=True)
        depth_image(crop).save(RES + f"wpd_{p['id']}.jpg", quality=90)
        print(p["id"], crop.size, flush=True)
