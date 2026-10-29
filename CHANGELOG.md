# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Six more opening effects: Ripple, Mosaic, Crystal, Color Bloom, Zoom Burst, Silk Wave (12 in total), shown as a grid with rendered previews.
- Living motion for photos: liquid-glass flow for abstract photos, light pulses for the circuit texture.
- Wallpaper detail page with live and opening previews, Dynamic or Static choice, back and apply buttons, and predictive back.
- Critically damped spring for hinge smoothing.
- Duo Frost effect modeled on the iPhone Duo reconstruction: shared image plane with inverse perspective, `sin(angle) * d^3` edge blur, separate brightness curves per panel.
- Five more opening effects: Fluted Glass, Aperture, Dawn, Window, Prism Edge, plus a strength setting.
- Cover display keep-awake while opening (Android 16 BookStyleDeviceStatePolicy) through a bounded screen wake lock, with an in-app switch.
- Settle behavior that returns a half-open posture to a sharp wallpaper after 1.5 seconds.
- 20 generative AGSL wallpapers rendered at native resolution across six categories, with band-limited blur.
- Gaussian blur pyramid for photo wallpapers.
- Display-switch angle diagnostics recorded by the engine and shown in the app.
- New app icon from the supplied artwork, with a themed monochrome layer.

### Changed
- Duo Frost follows Apple's model: half the blur, shade that falls into black at the free edge, black wedges where rays miss the picture, and the inner display's eye over the crease.
- Drawn landscapes (Aurora Veil, Sahara, Moonlit Dunes, Still Sea) moved to a new Illustrated category; added Pale Fog to Minimal.
- Removed the displacement-based nature motion and its motion maps; landscape photos are still images until video footage replaces them.
- Renamed the app and repository to Lumifold; application ID and packages are now `com.lumifold.wallpaper`.
- Generative scenes move visibly faster (10 to 30 second cycles); ribbons flow along their length, dunes gain blowing sand and clouds, the sea gains rolling swell.
- App thumbnails are rendered once into cached bitmaps instead of re-running shaders on every frame.
- The preview angle readout moved out of the animation area.
- App UI rebuilt in Jetpack Compose with a 3D book-fold preview that renders the real shader, effect cards, category chips without emoji, and a two-pane layout on wide screens.
- Minimum SDK raised from 31 to 33 (AGSL).
- Photo catalog renamed to match the actual image content.

### Removed
- White gradient overlays that imitated frost, the touch ripple, and the pixel-warping motion that produced glare-like artifacts.
- Five 1024 px photos and two mislabeled or duplicate photos.
- Legacy launcher PNGs, XML layouts, and the unused AppCompat, Material Components, ConstraintLayout and WindowManager dependencies.

## [0.1.0] - 2026-10-23

### Added
- Native `WallpaperService` engine (`FoldWallpaperService`) providing dynamic live wallpaper capabilities.
- Real-time physical hinge angle detection via `Sensor.TYPE_HINGE_ANGLE` (0° to 180°).
- GPU-accelerated blur pipeline using Android 12+ `RenderEffect.createBlurEffect`.
- Low-pass smoothing filter in `HingeSensorObserver` to prevent physical mechanical jitter.
- Multi-theme vector artwork catalog:
  - Landscapes: Cyber Aurora, Dunes Horizon.
  - Geometric: Prismatic Crystal, Material Matrix.
  - Minimal: OLED Ambient Rings, Clean Polar.
- Interactive customizer and preview activity (`MainActivity`) with a 0° to 180° fold simulator slider and native Live Wallpaper Picker intent dispatch.
- Dual-display in-memory bitmap cache for seamless cover-to-inner screen handoff.
- Strict battery safeguards unregistering sensors and clearing handlers on screen power-down.
- Unit test suite (`HingeMathTest`, `WallpaperRepositoryTest`, `FoldLifecycleTest`).
- GitHub Actions automated CI workflow (`.github/workflows/android-ci.yml`).
- Project governance documentation, architecture handbook, and manual QA checklist.
