# Ambient Fold Wallpaper -- Progress Dashboard

**Branch:** `develop`

---

## Scores (update after each sprint)

| Metric | Current | Target | Delta |
|--------|---------|--------|-------|
| Tasks Completed | 6 | 12 | +6 |
| Supported Wallpaper Themes | 6 | 6 | 0 |
| Min SDK Target | 31 (Android 12) | 31 | Met |
| Target SDK | 35 (Android 15) | 35 | Met |

---

## Sprint / Phase 1 -- Project Bootstrap and Core Engine [DONE]

- [x] T001 Initialize Gradle build structure with Android 12+ compatibility
- [x] T002 Implement HingeSensorObserver with exponential moving-average filter
- [x] T003 Implement FoldWallpaperService with RenderNode and RenderEffect hardware blur
- [x] T004 Add initial vector theme collection (Landscapes, Geometric, Minimal)
- [x] T005 Implement interactive customizer MainActivity with 0-180 deg fold slider
- [x] T006 Configure repository governance baseline (README, CHANGELOG, DEVLOG, branch model)

## Sprint / Phase 2 -- v0.1.0 Stabilization and Multi-Display Optimization

- [ ] T007 Add Jetpack WindowManager fold posture and display change listener
- [ ] T008 Implement dual-display bitmap cache for instantaneous cover/inner screen handoff
- [ ] T009 Add unit tests for HingeSensorObserver smoothing and math calculations
- [ ] T010 Validate battery performance with sensor unregistration when display is off
- [ ] T011 Perform local release build validation (assembleRelease / lint)
- [ ] T012 Finalize v0.1.0 release artifacts and documentation
