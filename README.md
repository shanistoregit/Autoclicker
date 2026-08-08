# Shani AutoClicker

Image-based auto clicker for Android. Record a sequence of taps (each tap
saves a small screenshot around that point), then replay: the app
continuously searches the screen for each saved image and taps it when
found. No fixed timing — it waits until the image appears, retries N times,
and restarts the whole sequence from step 1 if a step can't be matched.

## How it works

- **Recording**: 3-second countdown, then a transparent overlay catches
  your taps. For each tap, a small region of the current screen is cropped
  as the "template" image for that step.
- **Replay**: For each step in order, the app screen-captures repeatedly
  (via MediaProjection) and uses OpenCV template matching to find the
  step's image on screen. When found (above your confidence threshold),
  it taps the matched location using Android's Accessibility gesture API.
  If a step isn't found after your configured retry count, the whole
  sequence restarts from step 1.
- **Save/Load**: Sequences save as a single `.autoclick` file (JSON with
  base64-embedded images) via internal storage or Android's file picker
  (Save As / Open).

## Required permissions (granted in-app, not at install time)

1. **Accessibility Service** — lets the app perform taps system-wide.
   Settings → Accessibility → Shani AutoClicker → enable.
2. **Screen Capture (MediaProjection)** — re-requested each time capture
   starts; Android does not allow this to be granted permanently.
3. **Display over other apps** — needed for the recording overlay that
   catches your taps.

## Building the APK

### Via GitHub Actions (recommended, matches your existing workflow)
1. Push this repo to GitHub.
2. The workflow in `.github/workflows/build.yml` runs automatically on
   push to `main`/`master`, or manually via the Actions tab
   ("Run workflow").
3. Download the built APK from the workflow run's **Artifacts** section
   (`app-debug-apk`).

### Locally (if you ever have Android Studio)
```
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

## Important things to know before you rely on this

- **This is a real, functioning skeleton, not a finished polished app.**
  The recording, matching, save/load, and replay-with-restart logic are
  all implemented and wired together. What's *not* included yet: a
  step-review/edit screen (to manually re-crop a captured region), a
  drag-to-reorder step list, and pause/resume mid-replay. I can add any
  of these next.
- **Accuracy depends on template size and confidence.** A 160x160px
  auto-crop (default) works well for buttons/icons but may need tuning
  (`autoTemplateSize` in `RecordingSession.kt`) for very small or very
  large UI elements. You can lower/raise the confidence threshold in the
  app UI to trade off false positives vs. missed matches.
- **This will very likely be flagged by anti-cheat in online games.**
  Screen-capture + synthetic-gesture automation is exactly the pattern
  game anti-cheat systems (including Free Fire's) watch for. That's a
  fact about how this class of app works, not a judgment on your use
  case — worth knowing before shipping this against a live multiplayer
  game.
- **OpenCV dependency**: using the `org.quickbirdstudios:opencv` prebuilt
  AAR to avoid needing the NDK/CMake toolchain in CI. If GitHub Actions
  ever fails to resolve it, swap to `org.opencv:opencv:4.x` from Maven
  Central as an alternative.

## Project structure

```
app/src/main/java/com/shanistore/autoclicker/
  MainActivity.kt              UI + permission flows
  ClickAccessibilityService.kt Performs taps (gesture dispatch)
  ScreenCaptureService.kt      MediaProjection foreground service
  RecordingOverlay.kt          Transparent tap-catching overlay
  RecordingSession.kt          Countdown + capture-on-tap logic
  ClickStep.kt / ClickSequence.kt   Data model + .autoclick save/load
  TemplateMatcher.kt           OpenCV template matching
  AutomationEngine.kt          Replay loop: match -> tap -> retry -> restart
```
