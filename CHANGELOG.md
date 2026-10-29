# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- First-run setup prompt that opens the "display over other apps" and accessibility settings needed for the effect over apps, with a hint for Android's restricted-settings block on apps installed from a file.

### Changed
- Effect strength: 100% now draws what 150% drew in 0.5.0, and higher settings go stronger.
- Full swipe on Home uses the same sweep as the effect over apps, with blur: the cover is covered from its right edge until fully covered at 90 degrees, and the inner screen lights fully covered and clears from right to left by flat. Covered areas dim lightly and blur strongly, so the effect reads on bright and dark wallpapers alike.
- After a close from the inner screen, the cover comes up in the dimmed state Android's switch brings it up in and reveals the wallpaper on its own over 1.2 seconds, instead of waiting for the hinge.

### Fixed
- A fast fold no longer shows the next screen at the pose from before the panel switch for a second or two: the wallpaper keeps following the hinge through the switch.
- During rapid folds, the overlay no longer darkens the Home screen as if an app were open.

## [0.5.0] - 2026-10-28

This entry covers every change since 0.1.0; the 0.2.0 and 0.3.0 tags were cut without changelog entries.

### Added
- Full swipe, on by default and toggled on the home page: when a screen lights up at the panel switch, its outer third is dimmed, the middle third fades to lit, and a blur fades across the picture; opening slides this off toward the outer edge and closing plays it back, on both screens, with the picture kept in place.
- Effect over apps: an optional accessibility service draws a soft dimming sweep over the open app on both screens and keeps the cover on until about 90 degrees while opening, as on Home.
- Hold and finish: a hinge held still keeps the effect for 2 seconds, then finishes it over 1.2 seconds; folding back the other way by 10 degrees starts a new fold, and a slow panel switch restarts the hold when the new panel shows.
- Per-panel effect ranges: a close hands over to the cover near 45 degrees, with the inner screen reaching full effect and the cover starting from it.
- Mirror effect option (off by default): both halves of the inner screen move with the Duo Frost fold.
- Shattered Glass opening effect (13 in total).
- 234 designs in eleven categories, including 63 real video loops for landscapes, cities, space and scenes, hosted on the `media-v1` release and downloaded on demand, plus new Paris, Tokyo, Empire State, Giza and Sphere stills.
- Signed release builds from a git-ignored `keystore.properties`.
- Six more opening effects: Ripple, Mosaic, Crystal, Color Bloom, Zoom Burst, Silk Wave (12 in total), shown as a grid with rendered previews.
- Living motion for photos: liquid-glass flow for abstract photos, light pulses for the circuit texture.
- Wallpaper detail page with live and opening previews, Dynamic or Static choice, back and apply buttons, and predictive back.
- Critically damped spring for hinge smoothing.
- Duo Frost effect modeled on the iPhone Duo reconstruction: shared image plane with inverse perspective, `sin(angle) * d^3` edge blur, separate brightness curves per panel.
- Five more opening effects: Fluted Glass, Aperture, Dawn, Window, Prism Edge, plus a strength setting.
- Cover display keep-awake while opening (Android 16 BookStyleDeviceStatePolicy) through a bounded screen wake lock, built into the fold effect.
- 20 generative AGSL wallpapers rendered at native resolution across six categories, with band-limited blur.
- Gaussian blur pyramid for photo wallpapers.
- Display-switch angle diagnostics recorded by the engine and shown in the app.
- New app icon from the supplied artwork, with a themed monochrome layer.

### Changed
- On Home the wallpaper draws the fold effect live; the overlay runs only over apps, which removes a late pop to blur and a jump when a frozen video frame gave way to the live clip.
- Home page toggles are Fold effect, Live effect, Full swipe and Mirror effect (Live motion renamed to Live effect); Pause on Battery Saver, effect strength, permissions and diagnostics moved to Advanced settings. Mirror effect is unavailable while Full swipe is on.
- Turning the live effect off shows each clip's still poster instead of a paused frame.
- Toolchain: Android Gradle Plugin 9.5 with built-in Kotlin, Kotlin 2.4.21, Gradle 9.8.2, compile and target SDK 37, Compose BOM 2026.09.00.
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
- Screen capture: the per-fold window screenshots, the wallpaper copy behind Home icons and the MediaProjection service. The accessibility service no longer takes screenshots or reads window content, and both foreground-service permissions are gone.
- The low-battery pause and the separate "Keep cover on while opening" switch.
- 54 unused resources (about 20 MB).
- White gradient overlays that imitated frost, the touch ripple, and the pixel-warping motion that produced glare-like artifacts.
- Five 1024 px photos and two mislabeled or duplicate photos.
- Legacy launcher PNGs, XML layouts, and the unused AppCompat, Material Components, ConstraintLayout and WindowManager dependencies.

### Effect Over Apps

- Activation: open Lumifold, tap the gear for Advanced settings, then turn on "Allow display over other apps" and "Lumifold accessibility" (each opens the Android settings page where you select Lumifold).
- Validation: open an app on the cover and unfold the phone; the cover stays on until about 90 degrees and a dimming sweep follows the hinge on both screens.
- Rollback: turn either setting off in the same place, or turn off Fold effect on the home page; nothing else is written to the device.
- Authority: the service takes no screenshots, reads no window content (accessibility capabilities 0), and receives no touch coordinates from other apps; it only dims the screen while the hinge moves and holds a screen wake lock up to your screen timeout.
- Docs: [Effect Over Apps](README.md#effect-over-apps) and [docs/handbooks/architecture.md](docs/handbooks/architecture.md#7-effect-over-apps).

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
