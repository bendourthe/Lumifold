"""Build the live-loop assets for every video wallpaper from its real footage.

For each video in WallpaperRepository.kt this writes:
  app/src/main/res/raw/wpl_<id>.mp4          small loop for grid thumbnails and previews
  app/src/main/res/drawable-nodpi/wpv_<id>.jpg poster, the loop's first frame
  build-output/live/full/<cache file name>     full-resolution loop for the phone's video cache

All three come from the same slowed, seamlessly looping cut, so a thumbnail, the preview and
the wallpaper show the same footage. Slowing blends neighboring frames instead of repeating
them, and the loop crossfades its end into its start so playback never jumps.

Usage: python tools/live_loops.py [id ...] [--push]
  --push copies the full loops into the app's video cache on the connected phone.
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

import imageio_ffmpeg

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT / "app/src/main/java/com/lumifold/wallpaper/model/WallpaperRepository.kt"
RAW = ROOT / "app/src/main/res/raw"
POSTERS = ROOT / "app/src/main/res/drawable-nodpi"
WORK = ROOT / "build-output/live"
SOURCES = WORK / "sources"
FULL = WORK / "full"
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
ADB = Path(os.environ["USERPROFILE"]) / "android-dev/sdk/platform-tools/adb.exe"
SERIAL = "66301FDDJ003SU"
PACKAGE = "com.lumifold.wallpaper"
UA = "Lumifold/1.0 (personal live wallpaper; benjamin.dourthe@gmail.com)"

# How much slower than shot, and how each clip loops. "seamless" clips already end where they start.
SLOW = {"default": 2.0, "SCENES": 1.5}
SEAMLESS = {"black_hole"}
# Seconds to skip at the start: title cards, captions and signs that are not part of the scene.
START = {"deep_sea_jellyfish": 16.0, "petra_monastery": 30.0, "canary_spring": 10.0,
         "crab_nebula_live": 15.0, "earth_from_iss": 7.0}
MAX_SOURCE_SECONDS = 14.0
FADE_SECONDS = 2.5


def run(args, **kw):
    return subprocess.run(args, capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW, **kw)


def java_hash(s: str) -> str:
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return format(h, "x")


def cache_name(item_id: str, url: str) -> str:
    suffix = "" if "1080p.vp9.webm" in url else "-" + java_hash(url)
    ext = "mp4" if url.split("?")[0].lower().endswith(".mp4") else "webm"
    return f"{item_id}{suffix}.{ext}"


def catalog():
    text = REPO.read_text(encoding="utf-8")
    sun = re.search(r'SUN_MEDIA = "([^"]+)"', text).group(1)
    items = []
    for m in re.finditer(r'video\(\s*"([a-z0-9_]+)",\s*"[^"]*",\s*WallpaperCategory\.([A-Z]+),\s*"([^"]+)"', text):
        url = m.group(3).replace("$SUN_MEDIA", sun)
        items.append({"id": m.group(1), "category": m.group(2), "url": url})
    return items


def duration(path: Path) -> float:
    out = run([FFMPEG, "-i", str(path)]).stderr.decode("utf-8", "replace")
    m = re.search(r"Duration: (\d+):(\d+):(\d+\.\d+)", out)
    if not m:
        raise RuntimeError(f"no duration for {path}")
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))


def pull_from_phone(name: str, dst: Path) -> bool:
    with open(dst, "wb") as f:
        p = subprocess.run(
            [str(ADB), "-s", SERIAL, "exec-out", "run-as", PACKAGE, "cat", f"files/videos/{name}"],
            stdout=f, stderr=subprocess.PIPE, creationflags=subprocess.CREATE_NO_WINDOW,
        )
    ok = p.returncode == 0 and dst.stat().st_size > 100_000
    if not ok:
        dst.unlink(missing_ok=True)
    return ok


def download(url: str, dst: Path) -> bool:
    for attempt in range(6):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r, open(dst, "wb") as f:
                while True:
                    chunk = r.read(1 << 20)
                    if not chunk:
                        break
                    f.write(chunk)
            return True
        except Exception as exc:
            print(f"  download retry {attempt + 1}: {exc}", flush=True)
            dst.unlink(missing_ok=True)
            time.sleep(20 * (attempt + 1))
    return False


def source_for(item) -> Path | None:
    SOURCES.mkdir(parents=True, exist_ok=True)
    ext = "mp4" if item["url"].split("?")[0].lower().endswith(".mp4") else "webm"
    dst = SOURCES / f"{item['id']}.{ext}"
    if dst.exists() and dst.stat().st_size > 100_000:
        return dst
    if pull_from_phone(cache_name(item["id"], item["url"]), dst):
        print("  pulled from phone", flush=True)
        return dst
    print("  downloading", flush=True)
    return dst if download(item["url"], dst) else None


def loop_filter(dur: float, slow: float, seamless: bool, scale: str) -> tuple[str, float]:
    """Returns the filter graph and the output length in seconds."""
    if seamless:
        body = f"[0:v]setpts=PTS-STARTPTS,fps=30,{scale}[s]"
        out_len = dur * slow
    else:
        d = min(dur, MAX_SOURCE_SECONDS)
        f = min(FADE_SECONDS, d / 4)
        body = (
            f"[0:v]trim=0:{d:.3f},setpts=PTS-STARTPTS,fps=30,{scale},split[a][b];"
            f"[a]trim={f:.3f}:{d:.3f},setpts=PTS-STARTPTS,fps=30,settb=1/30[main];"
            f"[b]trim=0:{f:.3f},setpts=PTS-STARTPTS,fps=30,settb=1/30[head];"
            f"[main][head]xfade=transition=fade:duration={f:.3f}:offset={d - 2 * f:.3f}[s]"
        )
        out_len = (d - f) * slow
    graph = f"{body};[s]setpts={slow}*PTS,minterpolate=fps=30:mi_mode=blend,format=yuv420p[v]"
    return graph, out_len


def encode(src: Path, dst: Path, item, long_edge: int, crf: int, preset: str):
    start = START.get(item["id"], 0.0)
    dur = duration(src) - start
    slow = SLOW.get(item["category"], SLOW["default"])
    scale = f"scale='if(gt(iw,ih),min({long_edge},iw),-2)':'if(gt(iw,ih),-2,min({long_edge},ih))':flags=lanczos"
    graph, out_len = loop_filter(dur, slow, item["id"] in SEAMLESS, scale)
    dst.parent.mkdir(parents=True, exist_ok=True)
    args = [
        FFMPEG, "-y", "-v", "error", "-ss", f"{start:.3f}", "-i", str(src), "-filter_complex", graph, "-map", "[v]", "-an",
        "-c:v", "libx264", "-preset", preset, "-crf", str(crf), "-profile:v", "high",
        "-movflags", "+faststart", "-t", f"{out_len:.3f}", "-f", "mp4", str(dst),
    ]
    p = run(args)
    if p.returncode != 0:
        raise RuntimeError(p.stderr.decode("utf-8", "replace")[-800:])
    return out_len


def poster(full: Path, dst: Path):
    p = run([
        FFMPEG, "-y", "-v", "error", "-i", str(full), "-frames:v", "1",
        "-vf", "scale='if(gt(iw,ih),min(1920,iw),-2)':'if(gt(iw,ih),-2,min(1920,ih))'", "-q:v", "3", str(dst),
    ])
    if p.returncode != 0:
        raise RuntimeError(p.stderr.decode("utf-8", "replace")[-400:])


def push(full: Path, name: str):
    tmp = f"/data/local/tmp/{name}"
    for args in (
        [str(ADB), "-s", SERIAL, "push", str(full), tmp],
        [str(ADB), "-s", SERIAL, "shell", "chmod", "644", tmp],
        [str(ADB), "-s", SERIAL, "shell", f"run-as {PACKAGE} cp {tmp} files/videos/{name}"],
        [str(ADB), "-s", SERIAL, "shell", "rm", tmp],
    ):
        p = run(args)
        if p.returncode != 0:
            raise RuntimeError(p.stderr.decode("utf-8", "replace"))


def main():
    wanted = {a for a in sys.argv[1:] if not a.startswith("--")}
    do_push = "--push" in sys.argv
    reuse = "--reuse" in sys.argv
    results = []
    for item in catalog():
        if wanted and item["id"] not in wanted:
            continue
        print(item["id"], flush=True)
        name = cache_name(item["id"], item["url"])
        full = FULL / name
        t0 = time.time()
        if reuse and full.exists() and (RAW / f"wpl_{item['id']}.mp4").exists():
            out_len = duration(full)
        else:
            src = source_for(item)
            if src is None:
                print("  SKIP: no source", flush=True)
                results.append({"id": item["id"], "ok": False})
                continue
            try:
                out_len = encode(src, full, item, 3840, 21, "veryfast")
                encode(src, RAW / f"wpl_{item['id']}.mp4", item, 720, 27, "slow")
                poster(full, POSTERS / f"wpv_{item['id']}.jpg")
            except RuntimeError as exc:
                print(f"  SKIP: {str(exc).strip()[:160]}", flush=True)
                results.append({"id": item["id"], "ok": False})
                continue
        if do_push:
            try:
                push(full, name)
            except RuntimeError as exc:
                print(f"  not copied to the phone: {str(exc).strip()[:120]}", flush=True)
        size = (RAW / f"wpl_{item['id']}.mp4").stat().st_size
        print(f"  ok {out_len:.1f}s loop, preview {size // 1024} KB, {time.time() - t0:.0f}s", flush=True)
        results.append({"id": item["id"], "ok": True, "seconds": round(out_len, 1), "preview_kb": size // 1024})
    (WORK / "report.json").write_text(json.dumps(results, indent=2))
    print("done", sum(r["ok"] for r in results), "of", len(results), flush=True)


if __name__ == "__main__":
    main()
