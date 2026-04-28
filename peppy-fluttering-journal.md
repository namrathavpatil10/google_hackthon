# Plan: MediaProjection + Floating Overlay for SentinEdge

## Context
The hackathon pitch describes SentinEdge flagging deepfakes **during live video calls on WhatsApp/Google Meet** with a red overlay appearing on top. Currently the app only analyzes its own camera feed or uploaded videos — it does not work on top of other apps. This plan adds that capability via Android's MediaProjection API + a floating WindowManager overlay, making the pitch scenario real and demoable.

---

## New Files to Create

### 1. `source/ScreenCaptureSource.kt`
New `FrameSource` implementation (matches the existing interface). Uses:
- `MediaProjection` → `VirtualDisplay` → `ImageReader`
- Reads pixel data from `ImageReader.acquireLatestImage()` at ~10 fps
- Converts `Image` planes → `Bitmap` (ARGB_8888)
- Emits via `MutableSharedFlow` with DROP_OLDEST (same pattern as `LiveCameraSource`)

```
MediaProjection
    └── VirtualDisplay (screen size, density)
            └── ImageReader.newInstance(width, height, RGBA_8888, 2)
                    └── onImageAvailableListener → emit Bitmap
```

### 2. `service/DeepfakeOverlayService.kt`
Foreground service that owns the full detection pipeline for screen capture mode:
- Receives `MediaProjection` via Intent extras (passed from MainActivity)
- Creates `ScreenCaptureSource`, `DeepfakeDetector`, `BlinkTracker`
- Runs coroutine scope: frames → combine signals → update overlay
- Shows/updates floating `WindowManager` overlay view
- Notification: "SentinEdge protecting your call" with Stop action

Detection result → update overlay:
- Score ≥ 0.75 → small green pill badge (top-right corner), "✓ 87% Real"
- Score 0.50–0.75 → amber pill, "⚠ Suspicious"
- Score < 0.50 → full-width red banner, "⚠ DEEPFAKE DETECTED · Irregular blink pattern"

### 3. `ui/FloatingOverlay.kt`
Standard Android `View` (not Compose — WindowManager is simpler with Views):
- `FrameLayout` root added to `WindowManager` with `TYPE_APPLICATION_OVERLAY`
- Two states:
  - **Collapsed**: 120×40dp pill in top-right corner (score + color)
  - **Expanded**: full-width 60dp banner at top (deepfake alert)
- Touch: drag to reposition (collapsed), tap X to dismiss (expanded)
- `update(trustScore, verdict, blinkRate)` method called from service

---

## Files to Modify

### 4. `AndroidManifest.xml`
Add permissions:
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
```

Add service:
```xml
<service
    android:name=".service.DeepfakeOverlayService"
    android:exported="false"
    android:foregroundServiceType="mediaProjection" />
```

### 5. `MainActivity.kt`
Add:
- `mediaProjectionLauncher` using `ActivityResultContracts.StartActivityForResult`
- `onLiveCallProtection()` callback:
  1. Check `Settings.canDrawOverlays()` — if false, open Settings intent
  2. Launch `MediaProjectionManager.createScreenCaptureIntent()`
  3. On result: start `DeepfakeOverlayService` with intent + `MediaProjection` token
- Stop service when app goes back to Idle

### 6. `HomeScreen.kt`
Add third mode card below existing two:
```
[📹 Analyze Video]      ← purple
[📷 Live Camera]        ← blue
[🛡 Protect Live Calls] ← red/orange gradient  ← NEW
```
"Monitors WhatsApp, Meet, Zoom in real time. Runs invisibly in the background."

---

## Implementation Order

1. `AndroidManifest.xml` — add permissions + service (2 min)
2. `source/ScreenCaptureSource.kt` — new FrameSource for screen (20 min)
3. `ui/FloatingOverlay.kt` — the floating window view (20 min)
4. `service/DeepfakeOverlayService.kt` — the foreground service (30 min)
5. `MainActivity.kt` — wire MediaProjection permission flow (15 min)
6. `HomeScreen.kt` — add third card (5 min)

---

## Key Technical Details

**MediaProjection permission flow (Android 10+):**
```
MediaProjectionManager.createScreenCaptureIntent()
    → user dialog: "SentinEdge will capture your screen"
    → resultCode + data Intent
    → mediaProjectionManager.getMediaProjection(resultCode, data)
    → pass to service via Intent extras
```

**WindowManager overlay (Android 8+):**
```kotlin
windowManager.addView(overlayView, WindowManager.LayoutParams(
    width, height,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    PixelFormat.TRANSLUCENT
))
```

**Foreground service notification (required Android 9+):**
- Channel: "SentinEdge Protection"
- Ongoing: true
- Action: "Stop" → sends stop broadcast to service

---

## Emulator Testing Note
MediaProjection works on the emulator (API 36). The overlay will appear on top of any app. To test:
1. Grant "Draw over other apps" permission in Settings
2. Tap "Protect Live Calls" → accept screen capture dialog
3. Open YouTube / any video app
4. SentinEdge overlay badge appears in top-right corner
5. Push the deepfake video to the emulator → overlay should turn red

---

## Verification
- [ ] "Protect Live Calls" button visible on HomeScreen
- [ ] Tapping it requests draw-over-apps then screen capture permissions
- [ ] After both granted, floating overlay badge appears in top-right corner
- [ ] Overlay stays on top when switching to another app
- [ ] Score updates ~every 100ms while screen capture is running
- [ ] Red "DEEPFAKE DETECTED" banner appears when score < 0.5
- [ ] "Stop" action in notification stops service and removes overlay
- [ ] App works normally (video upload, live camera) independently of service
