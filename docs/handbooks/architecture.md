# Architecture Handbook -- Ambient Fold Wallpaper

Technical manual detailing the runtime architecture, sensor processing pipeline, and graphics subsystems of Ambient Fold.

---

## 1. System Overview

```
Physical Hinge Articulation (0° to 180°)
        │
        ▼
Sensor.TYPE_HINGE_ANGLE (Type 36)
        │
        ▼
HingeSensorObserver
  ├── Exponential Moving Average filter (alpha = 0.35f)
  └── Ease-In Power Curve: blurProgress = (1.0 - angle/180)^1.8
        │
        ▼
FoldWallpaperService (Engine)
  ├── Dual-Display Bitmap Cache (Cover vs Inner aspect ratio boundary 1.4)
  ├── Hardware RenderNode recording
  ├── Depth Zoom Transform: 1.0f + blurProgress * 0.04f (center-anchored)
  └── RenderEffect.createBlurEffect(blurProgress * maxBlur, blurProgress * maxBlur)
        │
        ▼
Hardware Canvas Presentation via SurfaceHolder
```

---

## 2. Sensor Processing & Ease-In Mathematics

Mechanical hinge movements introduce micro-vibrations and discrete step readings. `HingeSensorObserver` filters raw sensor events using an exponential moving average:

$$S_t = (1 - \alpha) S_{t-1} + \alpha R_t \quad (\alpha = 0.35)$$

The normalized angle is then transformed into an ease-in progress curve:

$$p = \left(1.0 - \frac{\theta}{180.0}\right)^{1.8}$$

- **180° (Unfolded)**: $p = 0.0$ (0% blur, 1.0x scale)
- **90° (Tabletop)**: $p = (0.5)^{1.8} \approx 0.287$ (~28% blur, 1.011x scale)
- **0° (Closed)**: $p = 1.0$ (100% blur, 1.04x scale)

---

## 3. Dual-Display Cache & Memory Hygiene

Foldable devices switch display configurations dynamically. Rather than re-allocating or re-rasterizing vector drawables on every fold event, `FoldWallpaperService` maintains two cached slots:

1. `coverBitmap`: Activated when surface aspect ratio (height / width) > 1.4.
2. `innerBitmap`: Activated when surface aspect ratio <= 1.4.

Old bitmaps are recycled explicitly when the user switches themes, keeping total wallpaper process memory strictly bounded under 35 MB.

---

## 4. Power & Battery Cutoff

To prevent background battery consumption:
- `onVisibilityChanged(false)` immediately halts `SensorManager` listeners and cancels pending UI handler posts.
- `onVisibilityChanged(true)` re-attaches listeners only when the wallpaper is actively displayed on screen.
