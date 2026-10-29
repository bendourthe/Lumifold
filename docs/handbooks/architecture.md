# Architecture Handbook -- Lumifold

Runtime architecture of the live wallpaper, the fold optics model, and the cover display policy.

---

## 1. Pipeline

```
Sensor.TYPE_HINGE_ANGLE  ->  HingeTracker (raw degrees)
        |
        v
FoldWallpaperService.FoldEngine
  - classifies the surface: inner (aspect <= 1.4) or cover, using natural (rotation 0) size
  - follows the angle with a critically damped spring (omega 22 rad/s, no overshoot)
  - FoldMotion: maps the hinge to each panel's effect angle, then holds and finishes a parked fold (section 6)
  - CoverAwakeKeeper: screen wake lock while the cover shows the wallpaper
  - Choreographer frames: every vsync while the hinge moves, 30 fps for ambient motion, none when idle
        |
        v
WallpaperShader.bind(surface, angle, time, effect, intensity)
        |
        v
AGSL: content(st, sigma) + fold effect stage  ->  lockHardwareCanvas().drawRect
```

`FoldPreviewView` in the app draws the same shader onto a 3D book model, so the preview and the wallpaper produce the same pixels. Over apps, a separate path draws the effect (section 7).

## 2. Fold Optics

Units are panel widths: the cover width, or half the inner width. The hinge runs along x = 0. The inner right half (natural orientation) is stationary; the cover and the inner left half sit on the moving leaf. The hinge angle is 0 closed and pi flat.

- **Shared image plane**: every moving-panel pixel at distance `u` from the hinge sits at `P = (u cos a, y, u sin a)`. Its color comes from the point where the ray from the eye through `P` meets the plane z = 0. The wallpaper uses an eye 4 panel widths in front of the display: over the cover's center for the cover, over the crease for the inner display. The preview passes its own camera. Content near the hinge never moves.
- **Coverage**: rays that land outside the picture return black, softened by the local blur. This produces the dark wedges at the free edge that the real device shows.
- **Duo motion**: `motion = smoothstep(0, 0.75, progress)`, where progress runs from 0 at the display's resting angle (closed for the cover, flat for the inner display) to 1 at 90 degrees, as in Apple's demo.
- **Duo blur**: `sigma = 0.09 * motion * d^1.5` panel widths, `d` the distance from the hinge.
- **Duo shade**: brightness `1 - 2 * motion * ((d - 0.2) / 0.8)^1.35`, clamped, so the free edge falls into black at full motion; the stationary half dims by at most 10 percent.
- **Hinge zoom**: content scales by `1 + 0.03 sin(a)` about the plane center.

`FoldOptics.kt` is the unit-tested specification of these formulas; `FoldShaders.kt` implements them in AGSL.

## 3. Content

- **Photos**: decoded once at the shared-plane resolution and turned into a 9-level Gaussian pyramid (`BlurPyramid`). Level k carries roughly 2^k pixels of blur; the shader blends the two levels around the requested sigma.
- **Video loops**: 63 clips are hosted as assets of the `media-v1` GitHub release and downloaded on demand into app storage (`VideoCache`, keyed by URL hash). A short bundled preview clip plays until the full loop is cached. `VideoFrames` decodes into an `ImageReader` and hands each frame to the shader as `uVideo`; playback pauses whenever the wallpaper is hidden, and with the live effect off the shader shows the clip's still poster.
- **Generative designs**: nine AGSL scenes (ribbons, aurora, mesh gradient, dunes, bokeh, contours, nebula, horizon, iris) with five-color palettes. Each scene receives the blur radius and band-limits its own detail: noise octaves below the radius fade to their mean, edges and lines widen while keeping their energy. A frosted frame costs the same as a sharp one. Motion cycles are tuned to roughly 10 to 30 seconds.

### Photo Motion

Each photo has a `PhotoMotion` style:

- **None**: a still photograph.
- **Liquid** (abstract photos): the whole image is advected along a slowly rotating flow field with a two-phase flow map, plus a gentle wobble and a faint sheen.
- **Pulse** (circuit texture): light travels along bright details.

Displacement-based nature motion for landscape photos was removed: shader warps on a still photo read as unnatural. Landscapes move to real video footage.

The two-phase flow map samples two copies that slide along the flow half a cycle apart and cross-fades between them, so the motion never visibly resets; a noise phase offset keeps regions from pulsing in sync.

### Thumbnails

App thumbnails are rendered once into cached bitmaps on a background thread (`ShaderSnapshots`, GPU through `HardwareRenderer`, software fallback). Drawing them live would re-run every shader whenever the window repaints, which happens on every frame of the 3D preview.

## 4. Effects

| Effect | Moving panel | Stationary half |
|--------|--------------|-----------------|
| Duo Frost | full perspective, edge blur, shade into black, coverage wedges | slight dimming; with "Mirror effect" on, mirrors the moving panel about the crease |
| Shattered Glass | radial and concentric cracks from an impact, each shard refracting by its own offset; mends as the hinge settles | slight darkening |
| Fluted Glass | 16 refracting ribs, moderate blur, rib highlights | slight darkening |
| Aperture | iris around the cover center or the crease | iris continues across |
| Dawn | darkness spreads from the free edge, returns from the crease with a warm front | same front |
| Window | full perspective, no blur | unchanged |
| Prism Edge | color dispersion and light blur toward the free edge | slight darkening |
| Ripple | rings from the hinge whose phase follows the angle | rings continue across |
| Mosaic | tiles up to 0.11 panel widths, larger toward the free edge | small tiles |
| Crystal | Voronoi facets with shifted views and lit edges | weaker facets |
| Color Bloom | color drains from the free edge, returns from the crease | same front |
| Zoom Burst | 5-tap radial blur toward the screen center | lighter radial blur |
| Silk Wave | a sinusoidal fold that travels with the angle | faint wave |

All effects are functions of the hinge angle only, never of time, so they track the hinge exactly.

### Full Swipe

With Full swipe on (the default), both screens replace the selected effect with one in-place profile, measured from each panel's outer edge (the inner screen's left, the cover's right). At the panel switch the outer third is dimmed, the middle third fades from dimmed to lit, and the blur fades from the middle third's outer edge to the far edge. Opening slides the profile off toward the outer edge (cover: closed to 90 degrees; inner: 90 degrees to flat); closing plays it back. The picture never moves. "Mirror effect" has nothing to change in this mode, so the app disables its toggle.

## 5. Cover Display Policy

Android 16 `BookStyleDeviceStatePolicy` keeps the CLOSED device state up to about 90 degrees while the power manager reports `SCREEN_TIMEOUT_KEEP_DISPLAY_ON`, which any screen wake lock triggers. `CoverAwakeKeeper` holds a `SCREEN_DIM_WAKE_LOCK` only when all of these hold: the fold effect is on, the cover is showing, the screen is interactive and unlocked (or showing the wallpaper), it is not the picker preview, and the hinge is below 120 degrees. On Home the wallpaper engine holds it; over apps, which hide the wallpaper, `FoldAccessibilityService` applies the same rule, re-armed by a 1x1 overlay that receives `ACTION_OUTSIDE` for every touch without coordinates. The lock times out one second before the user's screen timeout (capped at 10 seconds on the lock screen). Closing always switches to the cover below about 45 degrees.

The engine records the hinge angle at each display switch it observes while the cover hold is off (`pref_measured_open_switch`, `pref_measured_close_switch`); the app shows both values as diagnostics. With the hold built into the fold effect, these values update rarely.

## 6. Hold and Finish

`FoldMotion` turns hinge samples into the effect angle each panel draws, shared by the wallpaper and the overlay over apps:

- **Per-panel ranges**: an opening hands over near 90 degrees, so the cover draws closed to 90 and the inner screen 90 to flat. A close hands over near `CLOSE_SWITCH` (45 degrees), so its mapping stretches each panel's span to the range it is lit: the inner screen reaches full effect at 45 and the cover starts from it. A close that starts on the cover (below 90 degrees) keeps the opening mapping, so reversing never jumps.
- **Direction**: each run of hinge travel keeps its direction until the hinge moves back by `REVERSE_DEG` (10 degrees, two sensor steps) from the run's furthest angle, which starts a new fold. Reaching fully closed or flat also starts one.
- **Hold**: a hinge held still keeps the effect for `HOLD_MS` (2 s), then finishes it over `FINISH_MS` (1.2 s): the cover plays back to clean, the inner screen forward to open. A finished fold stays finished until a new fold starts, so continuing the same gesture does not bring the effect back. When Android takes a second or two to switch panels, the hold restarts as the new panel shows.

## 7. Effect Over Apps

`FoldAccessibilityService` follows the hinge with its own `FoldMotion` and, while a fold is active and no wallpaper is showing, `FoldGlass` adds a full-screen accessibility overlay that dims the app in a soft sweep: the cover darkens from its right edge until fully dim at 90 degrees; the inner screen starts dim and clears from its right edge (from the hinge without Full swipe) by flat; closing plays it back. Effect strength scales the dimming. The decision to skip the overlay is made once per fold from `FoldWallpaperService.isWallpaperShowing`, because the wallpaper hides briefly during each panel switch.

The service takes no screenshots and reads no window content (accessibility capabilities 0). Two earlier designs were retired: per-fold window screenshots failed while apps such as Settings re-laid themselves out for the new panel, and Android's cross-window blur covers only uniform rectangles, whose hard edges cut across a gradient.
