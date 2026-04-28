# Sentin-Edge — Research & Build Plan

## Context
Sentin-Edge is a real-time, fully on-device deepfake-defense app for the Samsung Galaxy S25 Ultra. It flags deepfakes during live video calls using a Vision Transformer running on the Snapdragon 8 Elite NPU, overlays a Trust Score on the camera feed, and produces a plain-language forensic explanation via an on-device LiteRT-LM. This plan breaks the project into the research areas to study and the concrete pieces to build for a hackathon submission.

---

## 1. What to Research

### A. Deepfake detection (the ML core)
- **Vision Transformer (ViT) fundamentals** — patch embeddings, self-attention, CLS token. Read the original ViT paper (Dosovitskiy et al.).
- **Deepfake-specific ViT models** — search for "ViT deepfake detection", "EfficientViT deepfake", "Face X-ray", "F3-Net".
- **Datasets** (for understanding what signals the model uses, not for retraining): FaceForensics++, Celeb-DF, DFDC, DeepfakeTIMIT.
- **Artifact signals the model exploits**: GAN fingerprints, warping boundaries, unnatural blink rate (humans blink 15–20/min), asymmetric facial-muscle motion, color-temperature mismatches around the face edge.

### B. Qualcomm AI Hub + Snapdragon NPU
- **Qualcomm AI Hub catalog** (aihub.qualcomm.com) — filter for image-classification / face models precompiled for Snapdragon 8 Elite. Look for a ViT or face-authenticity model with a `.tflite` / `.dlc` / QNN context-binary artifact.
- **Runtime options**: QNN SDK (native, fastest), LiteRT (TFLite) with NNAPI / Qualcomm delegate, ONNX Runtime with QNN EP.
- **Snapdragon 8 Elite Hexagon NPU** — INT8/INT16 quantization, ~45 TOPS, how to profile a model on-device.
- **AI Hub compile/profile flow** — submitting a model, getting per-device latency, downloading the compiled asset.

### C. On-device LLM (the explainer)
- **LiteRT-LM** — Google's on-device LLM runtime (successor to MediaPipe LLM Inference). Supported models: Gemma 3 Nano, Gemma 2 2B, Phi-2.
- Prompt pattern: feed detector outputs (blink-rate, muscle-asymmetry score, artifact heatmap summary) → get a 2–3 sentence user-facing explanation.
- Memory budget on S25 Ultra (12–16 GB RAM) — a 2B-param INT4 model fits comfortably.

### D. Android camera + real-time pipeline
- **CameraX** `ImageAnalysis` use case at 15–30 fps, `YUV_420_888` → RGB conversion.
- **ML Kit Face Detection** for face crop + landmarks (eyes for blink, mouth for lip-sync heuristics).
- **Jetpack Compose overlay** — `Canvas` over `PreviewView`, animated trust-score ring, red "Deepfake Detected" banner.
- **Foreground service + media projection** — required to analyze another app's video call (WhatsApp, Zoom). This is the hard part; study `MediaProjection` + `VirtualDisplay`.

### E. Performance & UX
- Target: end-to-end latency under 100 ms per analyzed frame; analyze every 3rd–5th frame, not every frame.
- Temporal smoothing — don't flip the verdict every frame; use a rolling window (e.g. last 30 frames) with hysteresis.
- Battery/thermal — profile with Android Studio Energy Profiler.

---

## 2. What to Build (hackathon scope)

Cut aggressively. A hackathon demo needs one convincing path, not a product.

### Minimum demo path
1. **Android app (Kotlin + Jetpack Compose)** with CameraX front-camera preview.
2. **Face crop** via ML Kit → feed to ViT.
3. **Pre-trained deepfake ViT from Qualcomm AI Hub**, running on NPU via LiteRT + QNN delegate. Output: real/fake probability.
4. **Trust Score overlay** — big ring (green 0.8+, amber 0.5–0.8, red <0.5) + label.
5. **Blink-rate heuristic** as a second signal (ML Kit eye-open probability over 10 s).
6. **LiteRT-LM (Gemma 3 Nano, INT4)** that takes the two signals and emits a one-paragraph explanation when score < 0.5.
7. **Demo video** — play a known deepfake clip on a second phone, point Sentin-Edge at it, show the red flag + explanation.

### Stretch (only if core works ≥2 days before deadline)
- `MediaProjection` integration so it works over a real WhatsApp video call.
- Audio deepfake detection (separate model, separate pipeline).
- Per-session forensic report export (PDF).

### Frame source abstraction (dev-on-emulator vs demo-on-S25)
The S25 Ultra arrives later, so the analyzer must be testable on the emulator with no real camera. Wrap frame input behind a `FrameSource` interface with two implementations:

- `LiveCameraSource` — CameraX `ImageAnalysis` → YUV→RGB → `Analyzer.analyze(bitmap)`. Used on physical devices and for the final demo.
- `DebugFileSource` — reads frames from an `.mp4` in `assets/` (via `MediaMetadataRetriever` for stills or `MediaCodec` for true playback) → same `Analyzer.analyze(bitmap)`. Used in the emulator and for headless model validation. Toggle via a debug build-config flag, no UI switcher needed.

Both paths feed the **same** detector, blink tracker, and overlay — so swapping sources changes nothing downstream. This unblocks development on day 1 with one deepfake clip and zero hardware.

### Critical files/modules to create (once out of plan mode)
- `app/src/main/java/.../source/FrameSource.kt` — sealed interface: `LiveCameraSource` + `DebugFileSource`.
- `app/src/main/java/.../camera/CameraAnalyzer.kt` — CameraX ImageAnalysis.Analyzer, frame throttling (used by `LiveCameraSource`).
- `app/src/main/java/.../ml/DeepfakeDetector.kt` — LiteRT interpreter wrapper, QNN delegate setup.
- `app/src/main/java/.../ml/BlinkTracker.kt` — ML Kit-based blink-rate signal.
- `app/src/main/java/.../llm/Explainer.kt` — LiteRT-LM wrapper + prompt template.
- `app/src/main/java/.../ui/TrustOverlay.kt` — Compose overlay.
- `app/src/main/assets/` — `.tflite` model + Gemma `.task` file + 1–2 sample deepfake `.mp4`s for `DebugFileSource`.

---

## 3. Hackathon Prep Checklist (what to divide with the team)

| Area | Owner role | Deliverable |
|---|---|---|
| ML model selection + AI Hub compile | ML lead | Working `.tflite` for Snapdragon 8 Elite, benchmarked latency |
| Android camera pipeline | Android dev | CameraX + face crop feeding frames to a stub detector |
| LLM explainer | ML / Android | Gemma 3 Nano packaged, prompt template, 3 example outputs |
| UI overlay + Trust Score | Design / Android | Compose overlay with smooth animation |
| Demo script + video | PM / all | 90-second pitch, 2-minute live demo, backup recorded video |
| Slides / one-pager | PM | Problem, solution, architecture diagram, on-device privacy story |
| Test deepfakes | Anyone | Curated set of 5–10 clips (public deepfake datasets) for demo |

---

## 4. Verification / Demo Plan
- Run the app on a physical S25 Ultra (emulator won't hit the NPU).
- Benchmark: inference latency per frame, frames-per-second the pipeline sustains, peak memory, battery drain over 5 min.
- Golden path: point camera at a real face → green. Point at a screen playing a known deepfake → red within 3 s with explanation.
- Edge cases: low light, multiple faces, no face, phone rotated.
- Record a backup demo video in case the live demo fails on stage (hackathon rule #1).

---

## 5. Suggested reply to Vaishnavi
> Hey! A few things you can start on:
> 1. Collect 5–10 public deepfake sample clips (FaceForensics++ / DFDC samples on YouTube) we can demo with.
> 2. Draft the 90-second pitch + a one-pager (problem, solution, on-device privacy angle, architecture diagram).
> 3. Browse Qualcomm AI Hub and shortlist any pre-trained deepfake / face-authenticity ViT models compiled for Snapdragon 8 Elite — send me the links.
> Ping me once you pick one and I'll take the next chunk.
