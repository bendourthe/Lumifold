# Ambient Fold Wallpaper

Dynamic Android live wallpaper engineered for Google Pixel foldables. It links physical hinge articulation to hardware-accelerated blur transitions (`RenderEffect`), creating smooth depth transitions as the phone opens and closes without replacing the stock Pixel Launcher.

---

## Features

- **Hinge Sensor Integration**: Reads mechanical posture in real-time via `Sensor.TYPE_HINGE_ANGLE` (0° folded to 180° flat).
- **GPU-Accelerated Depth Blur**: Android 12+ `RenderEffect.createBlurEffect` executed inside native `RenderNode` pipelines at 120 Hz with zero CPU raster overhead.
- **Stock Launcher Compatible**: Implements the standard Android `WallpaperService` contract. App icons, widgets, and Pixel Launcher gestures remain untouched.
- **Multi-Theme Collection**:
  - *Landscapes*: Cyber Aurora, Dunes Horizon.
  - *Abstract Geometric*: Prismatic Crystal (Dark), Material Matrix (Light).
  - *Minimal*: OLED Ambient Rings (Dark), Clean Polar (Light).
- **Zero Idle Battery Drain**: Sensor listeners and surface render loops unregister automatically when the screen turns off or when apps cover the home screen.
- **Interactive Simulator**: Built-in test slider allows previewing the fold blur curves without needing physical hardware.

---

## Technical Architecture

```
Hardware Hinge Movement (0° to 180°)
        │
        ▼
Sensor.TYPE_HINGE_ANGLE (Low-pass filtered in HingeSensorObserver)
        │
        ▼
Normalized Progress: p = clamp(angle / 180.0, 0.0, 1.0)
        │
        ▼
Blur Radius: r = (1.0 - p) * MaxBlurRadius
        │
        ▼
Hardware Canvas / RenderNode.setRenderEffect(RenderEffect.createBlurEffect(r, r))
        │
        ▼
SurfaceHolder Frame Presentation
```

---

## Project Structure

```
ambient-fold-wallpaper/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ambientfold/wallpaper/
│       │   ├── model/
│       │   │   ├── WallpaperItem.kt
│       │   │   └── WallpaperRepository.kt
│       │   ├── service/
│       │   │   ├── FoldWallpaperService.kt
│       │   │   └── HingeSensorObserver.kt
│       │   └── ui/
│       │       ├── MainActivity.kt
│       │       └── WallpaperAdapter.kt
│       └── res/
│           ├── drawable/
│           ├── layout/
│           ├── values/
│           └── xml/wallpaper.xml
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Requirements

- Android Studio Koala / Ladybug or newer.
- Android SDK 35 (Android 15+).
- Minimum Android version: Android 12 (API level 31) for `RenderEffect` hardware acceleration.
- Device: Google Pixel Fold, Pixel 9 Pro Fold, or future Pixel foldable hardware. Also runs in foldable Android emulators with configurable hinge angle sensors.

---

## Setup and Installation

1. Open Android Studio.
2. Select **File > Open** and point to `ambient-fold-wallpaper`.
3. Let Gradle sync project dependencies.
4. Connect your Pixel Fold or launch an Android Foldable Emulator (e.g. 7.6" Fold-in with outer display).
5. Run the app (`Shift + F10`).
6. From the app interface, select your preferred theme and tap **Set as Wallpaper** to activate it via Android's native Live Wallpaper Picker.

---

## GitHub Repository Setup

To publish this codebase to your GitHub account:

### 1. Suggested Repository Metadata
- **Repository Name**: `ambient-fold-wallpaper`
- **Description**: `Dynamic Android live wallpaper for Google Pixel foldables with hardware-accelerated hinge angle blur transitions.`
- **Visibility**: Public or Private
- **Initialize with README**: No (already included)
- **Add .gitignore**: None (already included)

### 2. Git Initialization Commands
Run the following commands in PowerShell from the project root:

```powershell
cd C:\Users\BEDOURTHE\.gemini\antigravity-ide\scratch\ambient-fold-wallpaper
git init
git add .
git commit -m "feat: initial commit for ambient fold dynamic wallpaper app"
git branch -M main
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/ambient-fold-wallpaper.git
git push -u origin main
```
