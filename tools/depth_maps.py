"""Estimates depth maps for the DEPTH and NEBULA motion styles.

Uses Depth Anything V2 Small (Apache-2.0) through onnxruntime. Download the model once to
build-output/models/depth_anything_v2_small.onnx from
https://huggingface.co/onnx-community/depth-anything-v2-small (onnx/model.onnx).

Writes wpd_<id>.jpg: grayscale, white = near, about 1024 px on the long side, smoothed so the
parallax in the shader stretches gently instead of tearing at edges.

Run from the repository root:  python tools/depth_maps.py <id> [<id> ...]
The source for <id> is app/src/main/res/drawable-nodpi/wp_<id>.jpg.
"""
import sys

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageFilter

RES = "app/src/main/res/drawable-nodpi/"
MODEL = "build-output/models/depth_anything_v2_small.onnx"
_session = None


def estimate(img: Image.Image) -> np.ndarray:
    global _session
    if _session is None:
        _session = ort.InferenceSession(MODEL, providers=["CPUExecutionProvider"])
    w, h = img.size
    scale = 518 / max(w, h)
    sw, sh = max(14, round(w * scale / 14) * 14), max(14, round(h * scale / 14) * 14)
    x = np.asarray(img.convert("RGB").resize((sw, sh), Image.BICUBIC), np.float32) / 255.0
    x = (x - np.array([0.485, 0.456, 0.406], np.float32)) / np.array([0.229, 0.224, 0.225], np.float32)
    x = x.transpose(2, 0, 1)[None]
    name = _session.get_inputs()[0].name
    d = _session.run(None, {name: x})[0].squeeze()
    lo, hi = np.percentile(d, 2), np.percentile(d, 98)
    return np.clip((d - lo) / max(hi - lo, 1e-6), 0, 1)


def depth_image(img: Image.Image, long_side=1024, blur=3.0) -> Image.Image:
    d = estimate(img)
    out = Image.fromarray((d * 255).astype(np.uint8))
    w, h = img.size
    s = long_side / max(w, h)
    out = out.resize((max(1, round(w * s)), max(1, round(h * s))), Image.BICUBIC)
    return out.filter(ImageFilter.GaussianBlur(blur))


if __name__ == "__main__":
    for pid in sys.argv[1:]:
        src = Image.open(RES + f"wp_{pid}.jpg")
        depth_image(src).save(RES + f"wpd_{pid}.jpg", quality=90)
        print(pid)
