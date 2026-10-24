# Lumifold -- Progress Dashboard

**Branch:** `feature/duo-fold-effect-overhaul` (from `develop`)

---

## Scores (update after each sprint)

| Metric | Current | Target | Delta |
|--------|---------|--------|-------|
| Tasks Completed | 47 | 48 | -1 |
| Wallpaper Designs | 103 (38 generative, 65 public-domain or CC0 photos) | 84 (12 per category) | Met |
| Opening Effects | 12, plus an off switch | 12 | Met |
| Unit Tests | 38 | 38 | Met |
| Min SDK Target | 33 (Android 13) | 33 | Met |
| Target SDK | 35 (Android 15) | 35 | Met |

---

## Phase 1 -- Core Live Wallpaper Engine & Sensor Pipeline [DONE]

- [x] T001 [US1] Implement HingeSensorObserver with smoothing filter and ease-in curve calculation app/src/main/java/com/ambientfold/wallpaper/service/HingeSensorObserver.kt
- [x] T002 [US1] Implement RenderNode hardware blur and center zoom pipeline in FoldWallpaperService app/src/main/java/com/ambientfold/wallpaper/service/FoldWallpaperService.kt
- [x] T003 [US1] Add unit tests for sensor smoothing and ease-in math app/src/test/java/com/ambientfold/wallpaper/service/HingeMathTest.kt

## Phase 2 -- Theme Collection & Preference Management [DONE]

- [x] T004 [US2] Implement WallpaperRepository and persistent preferences app/src/main/java/com/ambientfold/wallpaper/model/WallpaperRepository.kt
- [x] T005 [P] [US2] Validate and bundle vector drawables for all six wallpaper themes app/src/main/res/drawable/
- [x] T006 [US2] Add unit tests for WallpaperRepository fallback behavior app/src/test/java/com/ambientfold/wallpaper/model/WallpaperRepositoryTest.kt

## Phase 3 -- Multi-Display Handoff & Battery Cutoff [DONE]

- [x] T007 [US3] Implement dual-display bitmap caching for cover and inner screens app/src/main/java/com/ambientfold/wallpaper/service/FoldWallpaperService.kt
- [x] T008 [US3] Enforce strict battery safeguards and sensor unregistration on visibility change app/src/main/java/com/ambientfold/wallpaper/service/FoldWallpaperService.kt
- [x] T009 [US3] Refine interactive preview activity with fold simulator slider and wallpaper intent launcher app/src/main/java/com/ambientfold/wallpaper/ui/MainActivity.kt
- [x] T010 [US3] Run lint and test suite for activity and service lifecycles app/src/main/java/com/ambientfold/wallpaper/ui/MainActivity.kt

## Phase 4 -- Architecture Refactor, Known-Gaps Reconciliation, and CI/CD [DONE]

- [x] T011 [US4] Perform architecture and static analysis lint sweep app/proguard-rules.pro
- [x] T012 [US4] Document known gaps and hardware display cutoff constraints docs/releases/v0/v0.1/development/v0.1.0-known-gaps.md
- [x] T013 [US4] Author living architecture handbook and update progress tracker docs/handbooks/architecture.md
- [x] T014 [US4] Audit git commit history for cleanliness and ASCII compliance docs/todos.md
- [x] T015 [US4] Create GitHub Actions CI workflow for automated testing and APK assembly .github/workflows/android-ci.yml
- [x] T016 [US4] Execute functional verification deep pass and record release evidence docs/releases/v0/v0.1/development/v0.1.0-last-phase-evidence.md
- [x] T017 [US4] Perform goal vs codebase compliance audit docs/releases/v0/v0.1/development/v0.1.0-last-phase-evidence.md
- [x] T018 [US4] Author manual QA verification checklist for Pixel Fold hardware docs/releases/v0/v0.1/development/manual-qa-checklist.md
- [x] T019 [US4] Run complete local test and check suite docs/todos.md
- [x] T020 [US4] Merge develop into main, tag v0.1.0 release, and publish to origin CHANGELOG.md

## Phase 5 -- Duo Effect Overhaul (v0.5.0) [IN PROGRESS]

- [x] T021 [US5] Implement the Duo model (shared image plane, cubic edge blur, brightness curves) in AGSL app/src/main/java/com/ambientfold/wallpaper/render/FoldShaders.kt
- [x] T022 [US5] Add the unit-tested optics specification app/src/main/java/com/ambientfold/wallpaper/render/FoldOptics.kt
- [x] T023 [US5] Keep the cover display awake while opening with a bounded screen wake lock app/src/main/java/com/ambientfold/wallpaper/service/CoverAwakeKeeper.kt
- [x] T024 [US5] Rewrite the wallpaper engine with per-frame hinge mapping, settle, and switch-angle diagnostics app/src/main/java/com/ambientfold/wallpaper/service/FoldWallpaperService.kt
- [x] T025 [US5] Add Fluted Glass, Aperture, Dawn, Window and Prism Edge effects app/src/main/java/com/ambientfold/wallpaper/model/OpeningEffect.kt
- [x] T026 [US5] Add 20 generative wallpapers and a photo blur pyramid app/src/main/java/com/ambientfold/wallpaper/render/BlurPyramid.kt
- [x] T027 [US5] Relabel the photo catalog to match image content and drop low-resolution photos app/src/main/java/com/ambientfold/wallpaper/model/WallpaperRepository.kt
- [x] T028 [US5] Rebuild the UI in Compose with a physically correct 3D book-fold preview app/src/main/java/com/ambientfold/wallpaper/ui/FoldPreviewView.kt
- [x] T029 [US5] Generate the new adaptive icon from the supplied artwork app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- [x] T030 [US5] Verify on an Android 16 foldable emulator: preview sweep, all effects, live engine, settle, cover path docs/releases/v0/v0.5/development/v0.5.0-known-gaps.md
- [x] T031 [US5] Update README, architecture handbook, changelog and known gaps docs/handbooks/architecture.md
- [x] T032 [US5] Verify cover keep-awake and display-switch diagnostics on a physical Pixel foldable (Pixel 11 Pro Fold: opening switch 90 degrees, closing switch 40 degrees) docs/releases/v0/v0.5/development/v0.5.0-known-gaps.md
- [x] T033 [US5] Rename the app, packages and repository to Lumifold README.md
- [x] T034 [US5] Move the preview angle readout out of the animation area app/src/main/java/com/lumifold/wallpaper/ui/LumifoldScreen.kt
- [x] T035 [US5] Add the wallpaper detail page with Dynamic or Static, back, apply and predictive back app/src/main/java/com/lumifold/wallpaper/ui/WallpaperDetailScreen.kt
- [x] T036 [US5] Add liquid, nature and pulse photo motion with landscape motion maps app/src/main/java/com/lumifold/wallpaper/render/FoldShaders.kt
- [x] T037 [US5] Speed up generative scene motion and add clouds, sand and swell app/src/main/java/com/lumifold/wallpaper/render/FoldShaders.kt
- [x] T038 [US5] Add six opening effects and an effect grid with rendered previews app/src/main/java/com/lumifold/wallpaper/model/OpeningEffect.kt
- [x] T039 [US5] Smooth the hinge with a critically damped spring app/src/main/java/com/lumifold/wallpaper/service/HingeTracker.kt
- [x] T040 [US5] Cache thumbnails as bitmaps to remove the per-frame shader redraw that caused an ANR app/src/main/java/com/lumifold/wallpaper/render/ShaderSnapshots.kt
- [x] T041 [US5] Replace unlicensed photos with 65 public-domain or CC0 photos and credit them docs/reference/photo-credits.md
- [x] T042 [US5] Grow every category to at least 12 designs and move drawn space scenes to Illustrated app/src/main/java/com/lumifold/wallpaper/model/WallpaperRepository.kt
- [x] T043 [US5] Make effect strength scale shading and perspective without saturating, with a live sample app/src/main/java/com/lumifold/wallpaper/render/FoldShaders.kt
- [x] T044 [US5] Fix cached shader images never re-rendering when their key changes app/src/main/java/com/lumifold/wallpaper/ui/LumifoldScreen.kt
- [x] T045 [US5] Add fold effect and live motion quick toggles in the app and as Quick Settings tiles app/src/main/java/com/lumifold/wallpaper/service/QuickTiles.kt
- [x] T046 [US5] Pause live motion in Battery Saver or below a chosen battery level app/src/main/java/com/lumifold/wallpaper/service/MotionPolicy.kt
- [x] T047 [US5] Hold the effect for 4 seconds at a still posture and ease it back gently app/src/main/java/com/lumifold/wallpaper/service/FoldWallpaperService.kt
- [x] T048 [US5] Add real video loops downloaded on demand, played through the fold effects (verified on the emulator; download needs a check on the phone, the emulator had no DNS) app/src/main/java/com/lumifold/wallpaper/render/VideoFrames.kt
- [x] T049 [US5] Add the Shattered Glass opening effect app/src/main/java/com/lumifold/wallpaper/render/FoldShaders.kt
- [x] T050 [US5] Show six opening effects with a Show all button app/src/main/java/com/lumifold/wallpaper/ui/LumifoldScreen.kt
- [x] T051 [US5] Add rotating planets from NASA maps, live Sun scenes and drifting nebulae app/src/main/java/com/lumifold/wallpaper/render/FoldShaders.kt
- [x] T052 [US5] Source public-domain or CC0 video loops: 15 Landscapes, 8 City, 9 Scenes docs/reference/photo-credits.md
- [ ] T053 [US5] Grow City past 8 clips (no public-domain neon, rain-street or light-trail footage on Commons yet) docs/reference/photo-credits.md
- [x] T054 [US5] Rebuild Shattered Glass with hairline cracks, polygonal ring breaks and per-shard refraction app/src/main/java/com/lumifold/wallpaper/render/FoldShaders.kt
- [x] T055 [US5] Label still wallpapers in the grid and detail page app/src/main/java/com/lumifold/wallpaper/ui/LumifoldScreen.kt
- [x] T056 [US5] Replace the generated suns with real NASA SDO footage, framed per display app/src/main/java/com/lumifold/wallpaper/model/WallpaperRepository.kt
- [x] T057 [US5] Frame planets per display (centered open, left limb on the cover) and add Saturn with rings and Earth at Night app/src/main/java/com/lumifold/wallpaper/render/FoldShaders.kt
- [x] T058 [US5] Split nebula photos into gas and star layers for parallax and twinkle instead of warping tools/nebula_layers.py
- [ ] T059 [US5] Check the Sun and planet cover framing on the phone's real cover display (verified in the in-app cover preview only)
- [x] T060 [US5] Stop picker previews from overwriting panel sizes and rebuild photos when the plane changes app/src/main/java/com/lumifold/wallpaper/service/FoldWallpaperService.kt
- [ ] T061 [US5] Confirm the composition fix on the phone with the new surface log line (adb logcat -s Lumifold)
- [x] T062 [US5] Hand off between displays from the effect midpoint and stretch each display's range to the measured switch angles app/src/main/java/com/lumifold/wallpaper/service/FoldWallpaperService.kt
- [x] T063 [US5] Point users to Android's Continue using apps on fold setting app/src/main/java/com/lumifold/wallpaper/ui/LumifoldScreen.kt
- [x] T064 [US5] Rebuild nebulae and selected photos in depth from estimated depth maps tools/depth_maps.py
- [x] T065 [US5] Add a ray-traced black hole and a spiral galaxy simulation app/src/main/java/com/lumifold/wallpaper/render/BlackHoleLens.kt
- [x] T066 [US5] Cap categories at about 16 and order Space and Landscapes by theme app/src/main/java/com/lumifold/wallpaper/model/WallpaperRepository.kt
- [ ] T067 [US5] Replace Illustrated with public-domain artworks in depth
- [ ] T068 [US5] Grow City to about 16 public-domain clips
- [ ] T069 [US5] Host full-resolution Sun clips as release assets and point the catalog at them tools/sun_encode.py
