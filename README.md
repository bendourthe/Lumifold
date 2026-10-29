# Lumifold

Live wallpaper for book-style Android foldables (Pixel Fold, Pixel 9 Pro Fold and later). It recreates the iPhone Duo frosted-glass opening effect, adds twelve more hinge-driven transitions, and brings every wallpaper to life with slow, natural motion. Each frame maps the real hinge angle to an AGSL shader, so the effect follows your hand instead of playing a canned animation.

## What's New in 0.5.1

- **Guided setup**: on first launch, while "display over other apps" or accessibility is off, the app opens a prompt with a button for each, plus a hint for Android's restricted-settings block on apps installed from a file.
- **Stronger default**: 100% now draws what 150% drew in 0.5.0, and 150% goes further.
- **Full swipe on any wallpaper**: Home uses the same right-to-left sweep as the effect over apps, with light dimming and strong blur, so bright and abstract wallpapers show it as clearly as dark ones.
- **Cover reveal after closing**: the cover comes up covered, matching the dimmed state Android's screen switch shows, then reveals the wallpaper by itself over 1.2 seconds, even after a fast close.
- **Fixes**: a fast fold no longer shows the new screen frozen at its pre-switch pose, and rapid folds no longer dim the Home screen.

## Highlights of 0.5.0

- **Full swipe** (on by default): a soft front crosses each screen from right to left, covering the cover with dimming and blur by 90 degrees and clearing the inner screen by flat; closing plays it back, and the picture never moves.
- **Effect over apps**: with an app open, a soft dimming sweep follows the fold on both screens, drawn live without screenshots, and the cover stays on until about 90 degrees like it does on Home.
- **Each screen matches its own range**: the inner screen's effect spans 90 degrees to flat when opening, and a close hands over to the cover near 45 degrees with the full effect on both sides of the switch.
- **Hold and finish**: a hinge held still keeps the effect for 2 seconds, then finishes it over 1.2 seconds; folding back the other way starts a new fold.
- **234 designs**, including 63 real video loops downloaded on demand, and a signed release build.

## Features

- **Duo Frost**: the iPhone Duo model, following Apple's own demo as reverse-engineered by [juejin](https://juejin.cn/post/7688737629098999843) and the [Empirical Health](https://www.empirical.health/blog/why-iphone-duo-fold-animation-works/) reconstruction. Both displays sample one fixed picture behind the glass; as a panel lifts, blur and shade grow toward its free edge until it falls into black, and rays that miss the picture show black wedges.
- **Thirteen opening effects**: Duo Frost, Shattered Glass, Fluted Glass, Crystal, Prism Edge, Aperture, Dawn, Window, Ripple, Mosaic, Color Bloom, Zoom Burst and Silk Wave. A critically damped spring smooths the hinge so the effect glides through the sensor's steps.
- **Live effect**: generative designs animate natively, abstract photos flow like slow liquid glass, and landscapes, cities and space scenes play real video loops. Turn the live effect off to show each design's still poster.
- **234 designs in eleven categories**: GPU-rendered generative scenes at the display's native resolution, photographs, and 63 video loops hosted on the project's `media-v1` release.
- **Home page toggles**: Fold effect, Live effect, Full swipe and Mirror effect (both halves move; off by default, and unavailable while Full swipe is on). Advanced settings hold effect strength, Pause on Battery Saver, the permissions for the effect over apps, and diagnostics.
- **Cover display stays on while opening** (Android 16 and later). See [Cover Display](#cover-display).
- **Diagnostics**: the app reports the live hinge angle and the angles at which your device switched displays.

## Cover Display

Android decides which display is on, not the wallpaper. On Android 16 and later, book-style foldables that use `BookStyleDeviceStatePolicy` switch to the inner display as soon as the hinge moves, unless a screen wake lock is held; then the device stays CLOSED (cover on) until about 90 degrees. Lumifold holds that lock as part of the fold effect: the wallpaper holds it on Home, and the accessibility service holds it over apps. The lock is re-armed by touches and expires one second before your screen timeout. When closing, Android hands over to the cover below about 45 degrees, and no app can change either angle. On older Android versions the system switches immediately; the inner display still runs its half of the effect.

`FLAG_KEEP_SCREEN_ON` only applies to a foreground activity window, and `WindowAreaController` (rear display and dual screen) only works for a foreground activity on an already-open device, so neither can serve a home-screen wallpaper.

## Effect Over Apps

On Home, the wallpaper draws the effect itself. Over an app, the optional Lumifold accessibility service and the "display over other apps" permission draw a dimming sweep above the app while the hinge moves. The app offers to set both up on first launch, and Advanced settings keep the switches. Lumifold takes no screenshots and does not read window content. Android can blur another app only in uniform rectangles, whose hard edges would cut across the sweep, so apps are dimmed rather than blurred.

## Build

Requirements: JDK 17 and the Android SDK (compile and target SDK 37). The minimum Android version is 13 (API 33) for AGSL.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`. Install it, open Lumifold, pick an opening effect, tap a wallpaper, then tap **Apply**.

A signed release build reads `keystore.properties` at the repository root (git-ignored, holding `storeFile`, `storePassword`, `keyAlias` and `keyPassword`); without it, `assembleRelease` produces an unsigned APK.

## Project Layout

```
app/src/main/java/com/lumifold/wallpaper/
  model/     WallpaperItem, OpeningEffect, WallpaperRepository (catalog and preferences)
  render/    FoldShaders (AGSL), FoldOptics (geometry spec), BlurPyramid, WallpaperShader, VideoFrames, ShaderSnapshots
  service/   FoldWallpaperService, FoldMotion (hold, finish and per-panel ranges), HingeTracker, CoverAwakeKeeper,
             FoldAccessibilityService and FoldGlass (effect over apps)
  ui/        MainActivity, LumifoldScreen, WallpaperDetailScreen, FoldPreviewView (3D preview), theme/
```

Architecture details: [docs/handbooks/architecture.md](docs/handbooks/architecture.md). Known limits of this release: [docs/releases/v0/v0.5/development/v0.5.1-known-gaps.md](docs/releases/v0/v0.5/development/v0.5.1-known-gaps.md). Photo sources and licenses: [docs/reference/photo-credits.md](docs/reference/photo-credits.md).
