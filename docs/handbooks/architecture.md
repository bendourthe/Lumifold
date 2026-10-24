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
  - follows the angle with a critically damped spring (omega 22 rad/s, no overshoot) and applies settle
  - CoverAwakeKeeper: screen wake lock while the cover shows the wallpaper
  - Choreographer frames: every vsync while the hinge moves, 30 fps for ambient motion, none when idle
        |
        v
WallpaperShader.bind(surface, angle, time, effect, intensity)
        |
        v
AGSL: content(st, sigma) + fold effect stage  ->  lockHardwareCanvas().drawRect
```

`FoldPreviewView` in the app draws the same shader onto a 3D book model, so the preview and the wallpaper produce the same pixels.

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
| Duo Frost | full perspective, edge blur, shade into black, coverage wedges | slight dimming |
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

## 5. Cover Display Policy

Android 16 `BookStyleDeviceStatePolicy` keeps the CLOSED device state up to about 90 degrees while the power manager reports `SCREEN_TIMEOUT_KEEP_DISPLAY_ON`, which any screen wake lock triggers. `CoverAwakeKeeper` holds a `SCREEN_DIM_WAKE_LOCK` only when all of these hold: the option is on, the wallpaper is visible on the cover, the screen is interactive, it is not the picker preview, and the hinge is below 120 degrees. The lock times out one second before the user's screen timeout (capped at 10 seconds on the lock screen) and is re-armed only by taps, touches and page scrolls on the wallpaper.

The engine records the hinge angle at each display switch it observes while visible (`pref_measured_open_switch`, `pref_measured_close_switch`); the app shows both values as diagnostics.

## 6. Settle

The fold effect is a transition. When the hinge moves less than 3 degrees for 1.5 seconds, the angle fed to the shader eases toward the current display's resting angle (0 for the cover, 180 for the inner display) with a 0.35 s time constant, and returns with a 0.12 s time constant once the hinge moves again.
