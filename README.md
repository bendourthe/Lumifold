# Lumifold

Live wallpaper for book-style Android foldables (Pixel Fold, Pixel 9 Pro Fold and later). It recreates the iPhone Duo frosted-glass opening effect, adds eleven more hinge-driven transitions, and brings every wallpaper to life with slow, natural motion. Each frame maps the real hinge angle to an AGSL shader, so the effect follows your hand instead of playing a canned animation.

## Features

- **Duo Frost**: the iPhone Duo model as reconstructed by [Empirical Health](https://www.empirical.health/blog/why-iphone-duo-fold-animation-works/). Both displays sample one shared image plane behind the phone, blur grows as `sigmaMax * sin(angle) * d^3` from the hinge to the free edge, and each panel has its own brightness curve.
- **Twelve opening effects**: Duo Frost, Fluted Glass, Aperture, Dawn, Window, Prism Edge, Ripple, Mosaic, Crystal, Color Bloom, Zoom Burst and Silk Wave. A critically damped spring smooths the hinge so the effect glides through the sensor's 1 degree steps.
- **Living motion**: abstract designs flow like slow liquid glass; landscape photos use motion maps so sky, clouds and mist drift, water flows and ripples, and trees and grass sway in the wind, while rocks and buildings stay still. Every wallpaper can be set to Dynamic or Static.
- **Cover display stays on while opening** (Android 16 and later): the wallpaper holds a short screen wake lock while the cover is showing, which keeps book-style Pixels in the CLOSED device state up to about 90 degrees. See [Cover Display](#cover-display).
- **32 designs in six categories**: 20 generative designs rendered by GPU shaders at the display's native resolution plus 12 photographs.
- **Wallpaper detail page**: tap a design to see it full size (live view or opening animation), choose Dynamic or Static, then apply. Back returns to the collection, including the system back gesture with a predictive animation.
- **Settle behavior**: a hinge held still at a half-open posture (tabletop, book, tent) eases back to a sharp wallpaper after 1.5 seconds.
- **Diagnostics**: the app reports the live hinge angle and the angles at which your device actually switched displays.

## Cover Display

Android decides which display is on, not the wallpaper. On Android 16, book-style foldables that use `BookStyleDeviceStatePolicy` switch to the inner display as soon as the hinge moves, unless an app holds a screen wake lock; then the device stays CLOSED (cover on) until about 90 degrees. Lumifold's "Keep cover on while opening" option holds that lock only while the cover shows the wallpaper, re-arms it only on wallpaper interaction, and lets it expire one second before your screen timeout (10 seconds on the lock screen). On older Android versions the system switches immediately and no app can change that; the inner display still runs its half of the effect.

`FLAG_KEEP_SCREEN_ON` only applies to a foreground activity window, and `WindowAreaController` (rear display and dual screen) only works for a foreground activity on an already-open device, so neither can serve a home-screen wallpaper.

## Build

Requirements: JDK 17, Android SDK 35. Minimum Android version is 13 (API 33) for AGSL.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`. Install it, open Lumifold, pick an opening effect, tap a wallpaper, then tap **Apply**.

Motion maps for landscape photos are generated with `python tools/make_motion_maps.py` (Pillow and NumPy).

## Project Layout

```
app/src/main/java/com/lumifold/wallpaper/
  model/     WallpaperItem, OpeningEffect, WallpaperRepository (catalog and preferences)
  render/    FoldShaders (AGSL), FoldOptics (geometry spec), BlurPyramid, WallpaperShader, ShaderSnapshots
  service/   FoldWallpaperService, HingeTracker (sensor and spring), CoverAwakeKeeper
  ui/        MainActivity, LumifoldScreen, WallpaperDetailScreen, FoldPreviewView (3D preview), theme/
tools/       make_motion_maps.py
```

Architecture details: [docs/handbooks/architecture.md](docs/handbooks/architecture.md). Known limits of this release: [docs/releases/v0/v0.5/development/v0.5.0-known-gaps.md](docs/releases/v0/v0.5/development/v0.5.0-known-gaps.md).
