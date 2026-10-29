# Fold Handoff Timing

## Result

The normal-speed Duo behavior is not achieved. The user confirmed that Home's wallpaper remains visible after the composition repair, but reports left-only inner-screen distortion and an incoming panel that becomes visible too late to show the effect. The shared-shading repair removes the mismatched full-screen blur fallback; it does not redesign Duo Frost or control panel power.

## Evidence

- Device: Pixel 11 Pro Fold, Android 17. Installed APK SHA256: B1B87BD8B40AF7ECACDBB824E4421AF0649CB44B9DC6C50C097DAE68E488DF9B.
- The preserved normal-speed Home cycle occurred on October 27, 2026, around 10:29:50-10:29:55 PDT. The streaming Perfetto capture covers 10:29:16-10:30:56 PDT. Parsing with full sorting reported no error statistics.
- Analysis follows the actual Binder call chain from system_server's PhotonicModulator through SurfaceFlinger to /vendor/bin/hw/android.hardware.composer.drm-hwc3-service. These are measurements from this phone, not estimates from a public Android version.

| Direction | Outgoing panel OFF, hardware composer | Incoming panel ON, hardware composer | Sum of sequential driver calls |
|---|---:|---:|---:|
| Opening | Cover: 221.54 ms | Inner: 156.41 ms | 377.95 ms |
| Closing | Inner: 130.09 ms | Cover: 230.32 ms | 360.41 ms |

The opening OFF call includes a 217 ms refresh_ctrl_commit_FI_flushed interval beneath frame insertion. The public-source search did not establish a matching implementation or an ordinary app setting that shortens this interval on the installed firmware. The phone's peak render-rate setting is 60 Hz and its minimum setting is unset; neither was changed. A refresh-rate comparison remains untested.

Android's initial-content waits were 410 ms opening and 339 ms closing. They overlap the driver operations and must not be added to them. They also do not measure physical illumination, so they do not fully quantify or disprove the user's reported one-to-two-second black interval.

## App Interaction

At 10:29:50.600 the app logged 90 degrees; Android requested the cover-to-inner transition at 10:29:50.604. Android reported the open posture at 10:29:50.802 and released the inner screen's initial content at 10:29:51.051. Home's cover-awake behavior therefore starts the sequential handoff late in the gesture. Changing the shader cannot render on the panel while it is powered off.

Lumifold's main thread spent 253.92 ms in MSG_DIE while removing the outgoing overlay. Its RenderThread simultaneously waited 234.45 ms for buffer release. This overlaps the outgoing hardware power operation; it is not a separate quarter-second to add to the power-call total. The launcher also stalled for roughly 214-227 ms during the same handoff. On closing, Lumifold's teardown took only 20.79 ms while the hardware power calls still took about 360 ms. Removing overlay teardown alone is therefore not established as a solution to the handoff delay.

The current Duo Frost shader deliberately distorts the left inner panel and leaves the right largely stationary. Its horizontal pinch also remains distinct from the target perspective mapping tracked in T097. Earlier captures of two adjacent Settings windows were successfully assembled into a single 2076x2152 image; the left-only result is not evidence of an independently animated right-window capture.

## Boundaries

- Earlier Home handoff could leave more time for the inner effect, at the cost of a shorter cover effect. It would not establish a closing-cover animation or simultaneous panel illumination.
- Seamless overlap needs a demonstrated display-policy mechanism. The retired connected-display polling loop and device-state overrides are not candidates to repeat. Stationary dual-panel illumination was previously demonstrated, so this report does not claim that the hardware cannot power both panels.
- The current app/ADB investigation has not demonstrated a reliable way to keep both panels visible through movement. Root access, bootloader changes, firmware modification, and new display overrides were not attempted.
- The GPU check passed eight assertions for matching fallback shading and preserving right-pane detail; 71 unit tests passed. Those checks do not constitute physical acceptance of the overall effect.

## Cover Hold Follow-up

The retained PowerManager history confirms that Lumifold acquired its cover screen wake lock at 10:29:44.719 and released it at 10:29:50.886, after the opening handoff had started. The installed preferences still enable both the cover hold and fold effect. The clean system-wallpaper comparison had initial-content waits of 118-390 ms despite the user's report of no visible delay; its logs are not a matching Perfetto comparison of vendor calls. Therefore the measured driver intervals alone cannot explain the perceived difference between the clean baseline and Lumifold.

The [Android 16 QPR2 posture estimator](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/services/foldables/devicestateprovider/src/com/android/server/policy/BookStyleClosedStatePredicate.java) treats an active screen wake lock as likely tent/wedge posture when its feature flag is enabled. Its state calculator can retain the cover decision until 90 degrees. This supports testing with Lumifold's existing cover-hold option disabled before movement starts; simply releasing a lock at a lower mid-gesture angle is not an established fix. Grip and orientation can also influence the policy, and this public source is not proof of an exact firmware match.

A read-only SurfaceFlinger dump reports async_off:0 on both physical displays, while vendor.display.async_off.supported is unset. Google's [published HwcDisplay implementation at 7249d706](https://android.googlesource.com/platform/hardware/google/display/common/+/7249d706bd8a2fc0649e15a8f6c950dc17bbb0bf/hwc/hwc3_device/HwcDisplay.cpp) initializes asynchronous power-off from that vendor support property, defaulting to false. Its SetAsync debug action changes a different flag used for composition; it must not be mistaken for an asynchronous power-off control. A direct composer-service dump returned FAILED_TRANSACTION. No driver property, compositor action, or power setter was changed, and this evidence does not establish that asynchronous power-off is supported on this phone.

## Cover Hold Comparison

The isolated v6 comparison ran on October 27, 2026, 11:42:22-11:44:51 PDT, with the installed build unchanged, the effect enabled, Accessibility unchanged, and only "Keep cover on while opening" turned off. Before capture, Lumifold held no screen wake lock and mScreenTimeoutPolicy was 0, yet the closed phone, lying flat, still reported isLikelyTentOrWedgeMode = true with closedState OUTER and no sticky flags. The user then performed three Home cycles in hand, upright.

| Opening | Hinge leaves closed (5 degrees) | Android requests inner | Inner initial content released | Hinge at release |
|---|---|---|---|---|
| v6 cycle 1 | 11:42:46.439 | +7 ms | +478 ms | 75-90 degrees |
| v6 cycle 2 | 11:43:04.564 | +8 ms | +304 ms | 35 degrees |
| v6 cycle 3 | 11:43:11.352 | +7 ms | +452 ms | 105-125 degrees |
| v5, hold on | 90 degrees at 10:29:50.600 | +4 ms after 90 degrees | +451 ms after 90 degrees | past 90 degrees |

With the hold off, Android requested the inner panel within 8 ms of the hinge leaving closed in all three cycles, rather than waiting for 90 degrees. The tent/wedge flag did not prevent this. The vendor power calls did not change: opening cover OFF took 230.06 and 223.36 ms, and inner ON took 157.48 and 157.61 ms, in cycles 2 and 3. Cycle 1's opening rows fell outside the retrieved query output. Closing is unaffected by this option: Android requested the cover at 40-55 degrees, inner OFF took 124.67-135.37 ms, and cover ON took 230.00-230.71 ms, matching v5.

The user reported that the inner screen seemed to light a little sooner but found it hard to judge, that the cover now turns off immediately on opening, and that closing was unchanged, with the cover lighting only when fully folded.

Interpretation: the cover hold is what moves Android's opening handoff to 90 degrees. Without it, the dark interval of about 385 ms of sequential driver work still exists, but it starts at the beginning of the gesture, and the inner panel shows content between 35 and 125 degrees depending on opening speed. The cost is that the cover half of the effect is no longer visible. The current renderer clamps the inner panel to the 90-180 degree half of the shader range (FoldMotion.degrees and remapForSwitch), so an early handoff alone shows only the second half of the effect. These are software timestamps with a small, uncertain subjective improvement; no fix is declared and no application code changed.

Afterward, the cover-hold option was restored on through the app UI and verified in preferences, the diagnostic log property returned to empty, MediaProjection was null, Accessibility was unchanged, the device-state override was empty, and no trace, logger, or recording process remained. The logcat file ends at 11:43:15.960 because buffered output was lost when the logger stopped; the Perfetto trace covers the final closing.

## Panel Policy Rules

The public Android 16 QPR2 [BookStyleStateTransitions](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/services/foldables/devicestateprovider/src/com/android/server/policy/BookStyleStateTransitions.java) table, read with BookStyleClosedStatePredicate, explains every v5 and v6 observation. The predicate buckets the hinge into 0, below 45, 45-90, and 90-180 degrees.

- Opening: the choice is made while fully closed. If the phone looks like tent or wedge at 0 degrees, the cover stays until the hinge reaches 90 degrees; otherwise the inner panel is chosen as soon as the hinge leaves 0. There is no intermediate angle an app can select.
- Tent or wedge is true when the screen is landscape, the orientation sensor reads seascape, the right half lies mostly flat, or, under the forceFoldablesTentModeWithScreenWakelock flag, the default display's screen timeout policy is KEEP_DISPLAY_ON (any screen wake lock). The v6 preflight reported true only because the phone lay flat; held upright, the input was false.
- Closing: from the inner panel, every 45-90 degree row prefers the inner panel unless the opening-only sticky flag is set, so Android always returns to the cover when the hinge drops below 45 degrees. No app input moves this threshold. With about 360 ms of sequential driver work, the cover lights close to fully folded at normal speed.

Consequences for the app: Lumifold's cover hold is a SCREEN_DIM wake lock taken only by the wallpaper engine while the wallpaper is visible (CoverAwakeKeeper.shouldHold requires visible). With an application in front, no lock exists, so opening switches at about 5 degrees and the cover goes dark immediately, which matches the user's report. Holding the lock while an application is on the cover would make application opening match Home (implemented as T107; see Application Cover Hold). Earlier inner illumination while keeping the cover effect, and an earlier cover on closing, are not available through this policy; both would require simultaneous panel output (T103).

## Application Cover Hold

FoldAccessibilityService now applies CoverAwakeKeeper.shouldHold to any unlocked screen, with its own SCREEN_DIM lock, so an application in front no longer removes the hold. A transparent 1x1 accessibility overlay with FLAG_WATCH_OUTSIDE_TOUCH receives one coordinate-free ACTION_OUTSIDE per touch and re-arms the lock for one second less than the screen timeout, matching the wallpaper's touch re-arming on Home.

The first physical run failed: the lock expired normally at 14:01:00, the screen slept, and after the user woke and unlocked the phone nothing re-took it before opening. A controlled sleep, wake, and unlock showed SCREEN_OFF and SCREEN_ON but no USER_PRESENT broadcast. The service therefore re-evaluates the hold on each subscribed window-state event; this only acquires a hold released while asleep or locked and never extends a running one.

With that build (APK SHA256 8D8C9174A7384437738688F071C62DD84267B19CD6A045CC9E9E98ECA692A2CC, 71 unit tests passing), the user unlocked with Android Settings in front and opened immediately. The hinge passed 5 degrees at 14:56:02.160 and Android requested the inner panel at 14:56:02.892, between the 80 and 100 degree samples; the hold logged on-to-off at that handoff. The user confirmed the cover now behaves as on Home. The inner-panel delay after 90 degrees and the 45-degree closing threshold are unchanged, as the policy rules above predict.

## Handoff Masking Trial

A fade-through-black was tried: the cover dimmed from 60 to 90 degrees, the inner screen dimmed toward the 45-degree closing switch, and a newly lit panel rose from black over 350 ms on the wallpaper, the app overlay, and the Home preview. At normal speed the user saw no difference, which matches the timing: 60 to 90 degrees passes in roughly 0.15 s and the inner panel lights near flat. The fade was removed everywhere, and a GPU render confirmed the default inner effect is again byte-identical to the original.

The user also observed that closing returns to the cover near 30 degrees while opening switches at 90. This follows the policy rules above: the hold-until-90 rule is set only while opening from closed, closing always returns to the cover below 45 degrees, and the roughly 360 ms of sequential driver work carries the hinge to about 30 degrees before the cover lights. No app input changes the closing threshold.

## Capture Quality

The initial v3 ring buffer retained only its final 23 seconds and missed the folds. The v4 streaming capture ran from 10:18:31 to 10:21:31 PDT; the user's subsequent cycle occurred around 10:25, outside that interval. Neither is evidence for the rendering cause. The v5 streaming capture preserved the tested cycle and supplies all timing measurements above.

All trace processes have stopped, the diagnostic log property returned to empty, MediaProjection is inactive, and the device-state override is empty. The installed build and display settings were left unchanged after analysis.

Local evidence resides in C:/Users/BEDOURTHE/AppData/Local/Temp/lumifold-duo-investigation-20261026: cover-timing-v5.pftrace, cover-timing-final-v5.log, cover-timing-recovered-v5.log, cover-timing-v5-analysis1.txt through cover-timing-v5-analysis4.txt, shared-shading-native-v2.log, and, for the cover-hold comparison, coverhold-v6.pftrace, coverhold-v6.log, and coverhold-v6-preflight-posture.txt. Perfetto Trace Processor v58.2 was used locally; no trace was uploaded.
