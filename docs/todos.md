# Ambient Fold Wallpaper -- Progress Dashboard

**Branch:** `develop`

---

## Scores (update after each sprint)

| Metric | Current | Target | Delta |
|--------|---------|--------|-------|
| Tasks Completed | 10 | 20 | +10 |
| Supported Wallpaper Themes | 6 | 6 | 0 |
| Min SDK Target | 31 (Android 12) | 31 | Met |
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

## Phase 4 -- Architecture Refactor, Known-Gaps Reconciliation, and CI/CD

- [ ] T011 [US4] Perform architecture and static analysis lint sweep app/proguard-rules.pro
- [ ] T012 [US4] Document known gaps and hardware display cutoff constraints docs/releases/v0/v0.1/development/v0.1.0-known-gaps.md
- [ ] T013 [US4] Author living architecture handbook and update progress tracker docs/handbooks/architecture.md
- [ ] T014 [US4] Audit git commit history for cleanliness and ASCII compliance docs/todos.md
- [ ] T015 [US4] Create GitHub Actions CI workflow for automated testing and APK assembly .github/workflows/android-ci.yml
- [ ] T016 [US4] Execute functional verification deep pass and record release evidence docs/releases/v0/v0.1/development/v0.1.0-last-phase-evidence.md
- [ ] T017 [US4] Perform goal vs codebase compliance audit docs/releases/v0/v0.1/development/v0.1.0-last-phase-evidence.md
- [ ] T018 [US4] Author manual QA verification checklist for Pixel Fold hardware docs/releases/v0/v0.1/development/manual-qa-checklist.md
- [ ] T019 [US4] Run complete local test and check suite docs/todos.md
- [ ] T020 [US4] Merge develop into main, tag v0.1.0 release, and publish to origin CHANGELOG.md
