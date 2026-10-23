# Manual QA Verification Checklist -- Pixel Fold Hardware

Step-by-step procedures to verify the Ambient Fold live wallpaper on physical hardware or Android Foldable Emulator.

---

## Prerequisites

1. Pixel Fold, Pixel 9 Pro Fold, or Android Studio 7.6" Foldable Emulator running Android 12+ (API 31+).
2. Ambient Fold APK installed.

---

## Test Cases

### TC-1: Native Live Wallpaper Activation
1. Launch **Ambient Fold** from the app launcher.
2. Tap **Set as Wallpaper**.
3. Confirm that the system Live Wallpaper Picker opens.
4. Select **Set on Home and Lock Screens**.
5. **Expected Result**: The wallpaper renders beneath the stock Pixel Launcher without interfering with app icons or search widgets.

### TC-2: Unfolded Clean Presentation (180°)
1. Open the device fully to 180° flat.
2. **Expected Result**: Wallpaper renders crystal clear with 0 blur and 1.0x baseline scale.

### TC-3: Tabletop Mode Articulation (90°)
1. Slowly fold the device to a 90° angle.
2. **Expected Result**: The wallpaper applies a gentle, subtle blur (~28% of max blur) and micro-zoom (1.01x scale) without frame drops.

### TC-4: Near-Closed Transition (<30°)
1. Fold the device toward closed posture.
2. **Expected Result**: Blur elevates rapidly according to the ease-in curve, reaching maximum blur before the display powers down.

### TC-5: Multi-Display Cover Screen Handoff
1. Close the device completely and look at the outer cover screen.
2. Open the phone to full screen again.
3. **Expected Result**: Display transition occurs without texture flickering, distortion, or black screen pauses.

### TC-6: Theme Carousel Switching
1. Open the **Ambient Fold** app.
2. Select a different theme (e.g. *Prismatic Crystal* or *Dunes Horizon*).
3. Return to the home screen.
4. **Expected Result**: Wallpaper updates to the selected theme immediately.

### TC-7: Interactive Simulator Testing
1. In the app, drag the **Fold Angle Simulator** slider from 180° down to 0°.
2. **Expected Result**: The preview card dynamically blurs and scales in real time matching the hardware hinge response.
