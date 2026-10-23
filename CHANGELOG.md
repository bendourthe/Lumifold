# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
- Project governance documentation and per-version release trees.
