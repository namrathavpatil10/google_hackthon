# Sentin-Edge — Day-0 Setup Plan

## Context
You want to start building Sentin-Edge (the on-device deepfake-defense Android app described in [it-s-2-am-your-vectorized-dragon.md](Desktop/google_hackthon/it-s-2-am-your-vectorized-dragon.md)) but you're starting from zero: no Android Studio, no Qualcomm AI Hub account, and no Samsung Galaxy S25 Ultra in hand yet (it arrives before the demo). This plan covers **only the bootstrap** — everything that must exist before the first line of `CameraAnalyzer.kt` gets written. Pick this up tonight; once it's done you'll be unblocked for the actual build per the existing project plan.

---

## Do you need an emulator? — short answer

**Yes, but only for early dev. The S25 Ultra is mandatory for the demo.**

| Use the **emulator** for | Use a **physical Android phone** (your own) for | Use the **S25 Ultra** for |
|---|---|---|
| Compose UI iteration | CameraX pipeline + real frames | Snapdragon 8 Elite NPU (QNN delegate) |
| ML Kit face detection on a virtual scene | ML Kit on a real face | End-to-end latency benchmarks |
| TFLite CPU/GPU smoke tests | LiteRT GPU delegate sanity check | Final demo recording |

The emulator **cannot** access the Hexagon NPU — confirmed in the existing plan ([line 83](Desktop/google_hackthon/it-s-2-am-your-vectorized-dragon.md#L83)). So treat it as a UI scratchpad, not the target.

---

## What to install (Mac, ~2–3 hours total)

1. **Android Studio (latest stable, "Ladybug" or newer)** — download from `developer.android.com/studio`. Bundled JDK 17 is fine; no separate JDK install needed.
2. **Android SDK** (installed via Studio's SDK Manager):
   - Platform: **API 35** (Android 15) + API 34 fallback
   - Build tools: latest
   - Emulator + Platform-tools (gives you `adb`)
3. **Emulator image:** Pixel 8 / API 35, Google APIs ARM64 (since you're on Apple Silicon — much faster than x86 images)
4. **Kotlin plugin** — bundled with Studio.
5. **Git** (probably already installed via Xcode CLT — `xcode-select --install` if not).
6. **`adb`** on PATH — `export PATH=$PATH:~/Library/Android/sdk/platform-tools` in your `~/.zshrc`.

## Accounts to create (~30 min total)

1. **Qualcomm AI Hub** — `aihub.qualcomm.com`. Free, gates the deepfake ViT compile-for-Snapdragon flow.
2. **Hugging Face** — needed to download Gemma 3 Nano weights (must accept Google's license).
3. **Google Developer** account — only if you plan to publish; skip for hackathon.

## Hardware to round up

| Item | Status | Notes |
|---|---|---|
| Mac (dev machine) | ✅ have it | darwin per env |
| Your personal Android phone | needed | Any modern Android (API 30+), USB-C cable, **enable Developer Options + USB debugging** |
| Samsung Galaxy S25 Ultra | arriving later | Mandatory for NPU + demo. Until it arrives, code defensively so the QNN delegate is swappable. |
| Second phone OR laptop | for demo | Plays the deepfake clip that S25 Ultra's camera points at |

## Sample assets to collect (in parallel, can hand to Vaishnavi per [section 5](Desktop/google_hackthon/it-s-2-am-your-vectorized-dragon.md#L91))

- 5–10 deepfake clips: FaceForensics++ samples, DFDC public samples, or curated YouTube clips.
- 5 real-face control clips for the green-trust-score demo.

---

## Order of operations (tonight → tomorrow)

1. Install Android Studio + SDK + Pixel 8 API 35 emulator. Boot it, confirm Hello World runs.
2. Sign up: Qualcomm AI Hub, Hugging Face. Browse AI Hub for `ViT` / `deepfake` / `face-authenticity` models filtered to Snapdragon 8 Elite — shortlist 2–3 candidates with their `.tflite` artifacts and on-device latency numbers.
3. Enable USB debugging on your personal Android, plug it in, run `adb devices` to confirm.
4. Create the empty Compose project (`com.sentinedge.app`, min SDK 30, target SDK 35) with these Gradle deps queued up but not yet wired:
   - `androidx.camera:camera-camera2 / camera-lifecycle / camera-view` (CameraX)
   - `com.google.mlkit:face-detection`
   - `com.google.ai.edge.litert:litert` + `litert-gpu` (CPU/GPU first; QNN delegate added once on S25 Ultra)
   - `com.google.ai.edge.litert-lm` (or MediaPipe `tasks-genai`) for Gemma
5. Stop. The build plan in [it-s-2-am-your-vectorized-dragon.md section 2](Desktop/google_hackthon/it-s-2-am-your-vectorized-dragon.md#L40) takes over from here.

---

## Critical files to be created (Phase 1, after this setup)

These already live in the parent plan — listing only so you know setup is "done" when you're ready to create them:

- `app/src/main/java/com/sentinedge/app/camera/CameraAnalyzer.kt`
- `app/src/main/java/com/sentinedge/app/ml/DeepfakeDetector.kt`
- `app/src/main/java/com/sentinedge/app/ml/BlinkTracker.kt`
- `app/src/main/java/com/sentinedge/app/llm/Explainer.kt`
- `app/src/main/java/com/sentinedge/app/ui/TrustOverlay.kt`
- `app/src/main/assets/` — model files

---

## Verification — you're "set up" when all of these pass

- [ ] `adb devices` shows your personal phone connected.
- [ ] Pixel 8 API 35 emulator boots and shows Compose preview.
- [ ] Empty `SentinEdgeApp` project builds and runs on **both** emulator and your phone, showing a placeholder Compose screen.
- [ ] You're logged into Qualcomm AI Hub and have a shortlist of ≥2 candidate deepfake models (with download links + reported latency on Snapdragon 8 Elite).
- [ ] You can download Gemma 3 Nano INT4 (`.task` file) from Hugging Face — license accepted.
- [ ] At least 3 deepfake sample clips downloaded and playable on the second phone/laptop you'll use for the demo.

When all six boxes tick, you're done with bootstrap. Move to the build plan in [it-s-2-am-your-vectorized-dragon.md](Desktop/google_hackthon/it-s-2-am-your-vectorized-dragon.md).
