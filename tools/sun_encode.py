"""Re-encodes the NASA SDO Sun clips for the app's GitHub release.

Commons only serves these clips as huge originals or 1080p transcodes. This keeps full resolution
(2048 x 2048 for full-disk clips), drops audio, and crossfades the last second into the first so the
loop has no jump. Encoder: VP9 (libvpx-vp9) from the imageio-ffmpeg build.

Run from the repository root after downloading the originals into build-output/sun_src/
(see build-output/sun_src/meta.json):  python tools/sun_encode.py [id ...]
Outputs build-output/sun_out/<id>.webm, uploaded as assets of the `media-v1` release.
"""
import json
import os
import subprocess
import sys

import imageio_ffmpeg

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
# id: (start s, length s or None for all, output size, crf)
JOBS = {
    "sun_flare_304": (0, None, 2048, 32),
    "sun_corona_171": (0, None, 2048, 32),
    "sun_teal_131": (0, None, 2048, 32),
    "ring_prominence": (0, None, 1920, 30),
    "solar_shreds": (0, 45, 1920, 30),
}
FADE = 1.0


def duration(path):
    out = subprocess.run([FFMPEG, "-i", path], capture_output=True, text=True, creationflags=subprocess.CREATE_NO_WINDOW).stderr
    for line in out.splitlines():
        if "Duration:" in line:
            h, m, s = line.split("Duration:")[1].split(",")[0].strip().split(":")
            return int(h) * 3600 + int(m) * 60 + float(s)
    raise RuntimeError("no duration for " + path)


def encode(vid, src):
    start, length, size, crf = JOBS[vid]
    total = duration(src) - start
    if length:
        total = min(total, length)
    body = total - FADE
    scale = f"scale={size}:-2:flags=lanczos"
    graph = (
        f"[0:v]trim=start={start}:duration={total},setpts=PTS-STARTPTS,{scale},fps=30,format=yuv420p,split[a][b];"
        f"[a]trim=0:{FADE},setpts=PTS-STARTPTS,fps=30[head];"
        f"[b]trim={FADE},setpts=PTS-STARTPTS,fps=30[rest];"
        f"[rest][head]xfade=transition=fade:duration={FADE}:offset={body - FADE:.3f}[v]"
    )
    os.makedirs("build-output/sun_out", exist_ok=True)
    out = f"build-output/sun_out/{vid}.webm"
    cmd = [FFMPEG, "-y", "-loglevel", "error", "-i", src, "-filter_complex", graph, "-map", "[v]", "-an",
           "-c:v", "libvpx-vp9", "-crf", str(crf), "-b:v", "0", "-row-mt", "1", "-cpu-used", "3",
           "-tile-columns", "2", "-g", "120", out]
    subprocess.run(cmd, check=True, creationflags=subprocess.CREATE_NO_WINDOW)
    print(vid, f"{os.path.getsize(out) / 1e6:.1f} MB", flush=True)


if __name__ == "__main__":
    meta = json.load(open("build-output/sun_src/meta.json"))
    for vid in (sys.argv[1:] or JOBS):
        encode(vid, meta[vid]["file"])
