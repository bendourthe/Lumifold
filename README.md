# DuoBlur (ambient-fold-wallpaper)

Live wallpaper for book-style Android foldables (Pixel Fold, Pixel 9 Pro Fold and later) that recreates the iPhone Duo frosted-glass opening effect and adds five other hinge-driven transitions. Every frame maps the real hinge angle to an AGSL shader, so the effect follows your hand instead of playing a canned animation.

## Features

- **Duo Frost**: the iPhone Duo model as reconstructed by [Empirical Health](https://www.empirical.health/blog/why-iphone-duo-fold-animation-works/). Both displays sample one shared image plane behind the phone, blur grows as `sigmaMax * sin(angle) * d^3` from the hinge to the free edge, and each panel has its own brightness curve.
- **Five more opening effects**: Fluted Glass, Aperture, Dawn, Window (pure parallax) and Prism Edge.
- **Cover display stays on while opening** (Android 16 and later): the wallpaper holds a short screen wake lock while the cover is showing, which keeps book-style Pixels in the CLOSED device state up to about 90 degrees. See [Cover Display](#cover-display).
- **32 designs in six categories**: 20 generative designs rendered by GPU shaders at the display's native resolution (Abstract, Gradients, Landscapes, Space, Textures, Minimal) plus 12 photographs.
- **Settle behavior**: a hinge held still at a half-open posture (tabletop, book, tent) eases back to a sharp wallpaper after 1.5 seconds.
- **3D preview**: the app shows a physically correct book foldable rendered with the same shader as the wallpaper. Scrub the angle, let it auto-play, or follow your real hinge.
- **Diagnostics**: the app reports the live hinge angle and the angles at which your device actually switched displays.

## Cover Display

Android decides which display is on, not the wallpaper. On Android 16, book-style foldables that use `BookStyleDeviceStatePolicy` switch to the inner display as soon as the hinge moves, unless an app holds a screen wake lock; then the device stays CLOSED (cover on) until about 90 degrees. DuoBlur's "Keep cover on while opening" option holds that lock only while the cover shows the wallpaper, re-arms it only on wallpaper interaction, and lets it expire one second before your screen timeout (10 seconds on the lock screen). On older Android versions the system switches immediately and no app can change that; the inner display still runs its half of the effect.

`FLAG_KEEP_SCREEN_ON` only applies to a foreground activity window, and `WindowAreaController` (rear display and dual screen) only works for a foreground activity on an already-open device, so neither can serve a home-screen wallpaper.

## Build

Requirements: JDK 17, Android SDK 35. Minimum Android version is 13 (API 33) for AGSL.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`. Install it, open DuoBlur, choose a design and effect, then tap **Set as wallpaper**.

## Project Layout

```
app/src/main/java/com/ambientfold/wallpaper/
  model/     WallpaperItem, OpeningEffect, WallpaperRepository (catalog and preferences)
  render/    FoldShaders (AGSL), FoldOptics (geometry spec), BlurPyramid, WallpaperShader
  service/   FoldWallpaperService, HingeTracker, CoverAwakeKeeper
  ui/        MainActivity, DuoBlurScreen (Compose), FoldPreviewView (3D preview), theme/
```

Architecture details: [docs/handbooks/architecture.md](docs/handbooks/architecture.md). Known limits of this release: [docs/releases/v0/v0.5/development/v0.5.0-known-gaps.md](docs/releases/v0/v0.5/development/v0.5.0-known-gaps.md).
