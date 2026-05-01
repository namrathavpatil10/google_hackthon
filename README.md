# SentinEdge

# Application Description

It’s 2 AM. Your phone rings a video call from someone in a bank uniform, calm and authoritative. They say your account has been compromised and need your OTP to freeze it. The face looks real. The voice sounds real. You almost type the code.
But Sentin-Edge has already flagged it. Three seconds into the call, a red overlay appears: “Deepfake Detected” Irregular facial muscle movement and unnatural eye blinking pattern identified.” You hang up. Your money is safe.
This is the crisis Sentin-Edge was built for.
Sentin-Edge is a real-time deepfake defense app for the Samsung Galaxy S25 Ultra. Using a pre-trained Vision Transformer from Qualcomm AI Hub, it performs forensic analysis through the Snapdragon 8 Elite’s NPU detecting sub-pixel inconsistencies, unnatural blinking, and generative artifacts invisible to the human eye. Results appear instantly as a Trust Score overlay on any live camera feed.
When a deepfake is flagged, the on-device LiteRT-LM generates a plain-language Forensic Explanation making the threat understandable to everyday users, not just experts. Everything runs fully on-device. No cloud. No data leaving your phone. No privacy tradeoff.
Sentin-Edge transforms your smartphone from a passive screen into an active, privacy-preserving firewall protecting you at the exact moment you need it most.

# Names and email of all Eligible Individuals on the team:

Fardeen Khan: fard33nrk@gmail.com

Namratha V Patil: nvpatil@usc.edu

Vaishnavi Srinath: vaishnavipdx.@gmail.com


---

## Screenshots

### Model Download (first launch)
<img width="738" height="1600" alt="landing_page" src="https://github.com/user-attachments/assets/b6eb6be5-018c-4daa-a316-c1ff8ad820b6" />

> *App downloads the Gemma 4 2B LiteRT-LM model (~2 GB) on first launch with a progress bar. After that it runs fully offline.*

### Video Analysis
<img width="738" height="1600" alt="WhatsApp Image 2026-05-01 at 12 34 59 PM" src="https://github.com/user-attachments/assets/8e5ec01d-444d-4cca-98b1-4a7df31d857a" />

### Image Analysis
<img width="738" height="1600" alt="WhatsApp Image 2026-05-01 at 12 26 27 PM (1)" src="https://github.com/user-attachments/assets/43ed524b-37bb-437c-9946-67bfa013a1aa" />

> *Upload any image or video clip — SentinEdge runs the dual-model ensemble and forensic pre-scan, then shows a trust score, watermark flags, and a Gemma-generated explanation.*

---
# Project Description 

Sentin-Edge is a cutting-edge security application designed for the Samsung Galaxy S25 Ultra. It transforms the smartphone from a passive viewing device into an active, privacy-preserving firewall against AI-generated fraud. By leveraging the Snapdragon 8 Elite’s NPU, Sentin-Edge detects deepfakes in real-time during live video calls, media playback, or camera feeds—keeping your data and identity safe without ever sending a single frame to the cloud.

Core Features & Functionality
Sentin-Edge provides the below distinct layers of protection tailored for everyday mobile use:
•	Media Upload Analysis: Users can upload existing images or videos from their gallery for a comprehensive forensic deep-dive report.
•	Plain-Language Explanations: Powered by Gemma 4 2B (LiteRT-LM), the app doesn't just give a score; it explains why a video is suspicious (e.g., "Unnatural blinking patterns detected") in simple terms.


How the Technology Works

Sentin-Edge employs a multi-stage Forensic Pipeline to ensure maximum accuracy and zero latency:
1. Forensic Pre-Scan
Before the AI even looks at the pixels, the SafetyVerificationEngine scans for digital fingerprints. It looks for watermarks initially and if there exists any watermarks it directly classifies as suscpicious.
2. Real-Time ML Analysis (The Brain)
Using the LiteRT (formerly TFLite) runtime optimized for the Qualcomm Hexagon NPU:
•	Vision Transformers (ViT): An ensemble of pre-trained models (dima806) analyzes facial textures for sub-pixel inconsistencies and generative artifacts.
•	Biometric Tracking: ML Kit and HRNet track "liveness" signals, such as blink rates per minute and mouth movement synchronicity.
•	Temporal Engine: A 20-frame rolling window monitors for "glitches" or landmark jitter that occur when a deepfake mask fails to align with the underlying face.
3. The Signal Combiner
All signals (visual score, biometric data, and metadata) are fused into a final Trust Score:
•	Green (≥ 0.8): Real/Trustworthy.
•	Orange (0.5 – 0.7): Suspicious/Inconclusive.
•	Red (< 0.5): Deepfake Detected.


Technical Stack & Architecture

Built for the next generation of Android hardware, Sentin-Edge utilizes a high-performance stack:
•	Hardware Acceleration: Primary execution on the Snapdragon 8 Elite NPU, with intelligent fallback to Adreno GPU or XNNPACK CPU.
•	Frameworks: Kotlin and Jetpack Compose for a modern, fluid UI; CameraX for high-speed frame capture.
•	On-Device LLM: Uses LiteRT-LM to run Gemma 4 2B locally, ensuring that the "Forensic Explanation" feature remains 100% private.
•	Privacy First: No cloud processing. All analysis happens locally on the device, ensuring biometric data never leaves the user's phone.

Real-time deepfake detection on Android. Upload a photo or video, scan a live camera feed, or run a background shield over your video calls — SentinEdge tells you if the face is AI-generated, entirely on-device.


## What it does

Most deepfake detectors run in the cloud. SentinEdge runs **entirely on-device** on the Samsung Galaxy S25 Ultra — your video never leaves your phone.

Three modes:

| Mode | How to use |
|---|---|
| **Media Upload** | Pick an image or video from your gallery — get a full forensic report |
| **Live Camera** | Point at a screen or person — score updates every frame in real time |
| **Background Protection** | Invisible overlay monitors WhatsApp / Zoom / Meet while you talk |

---

## How it works

### Full detection pipeline

```
┌──────────────────────────────────────────────────────────────┐
│                   FORENSIC PRE-SCAN (before NPU)             │
│   SafetyVerificationEngine                                    │
│   ├─ EXIF metadata check (camera make/model absent?)         │
│   ├─ AI software signatures (Midjourney, DALL-E, Firefly…)   │
│   ├─ C2PA / JUMBF content authenticity markers               │
│   ├─ IPTC DigitalSourceType AI tags                          │
│   └─ Adobe Generative / SynthID byte-level scan              │
│                                                               │
│   Result: watermarkFound + metadataSuspicious flags           │
│   → If watermark found: trust score immediately → 0.1         │
└─────────────────────────────┬────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                       Frame Source                            │
│   Image/Video ──┐                                            │
│   CameraX ──────┼──► FrameSource ──► Bitmap frame            │
│   MediaProjection ─┘                                         │
└─────────────────────────────┬────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                  ML Kit Face Detection                        │
│         Crop face region · track eye state · landmarks        │
└──────────┬──────────────────────────────────────┬────────────┘
           │                                      │
           ▼                                      ▼
┌──────────────────────┐              ┌─────────────────────────┐
│   LiteRT Vision      │              │   BlinkTracker          │
│   (Qualcomm NPU)     │              │   (ML Kit eye-open)     │
│                      │              │   blink rate / min       │
│  Static image:       │              └──────────┬──────────────┘
│   V1 only (dima806)  │                         │
│                      │              ┌─────────────────────────┐
│  Live video:         │              │   HRNet Face Landmarks  │
│   V1 (40%) + V2(60%) │              │   mouth movement score  │
│   ensemble           │              └──────────┬──────────────┘
└──────────┬───────────┘                         │
           │  visual_score                        │
           └──────────────┬──────────────────────┘
                          ▼
          ┌───────────────────────────────────┐
          │         Signal Combiner            │
          │  visual_score + blink + mouth      │
          └──────────────┬────────────────────┘
                         │
                         ▼
          ┌───────────────────────────────────┐
          │        TemporalEngine              │
          │  20-frame rolling window           │
          │  variance > 0.03  → glitch flag    │
          │  landmark jitter > 0.12 → flag     │
          │  glitch detected → score –0.4      │
          └──────────────┬────────────────────┘
                         │
          ┌──────────────┴──────────────────────┐
          ▼                                     ▼
   score ≥ 0.5                           score < 0.5
   ✅ REAL                               🚨 DEEPFAKE
   Green ring                            Red banner
          │
          ▼
   Gemma 4 2B (LiteRT-LM)
   Plain-language explanation
   "Blink rate 3/min detected —
    well below human average…"
```

### Score breakdown

```
V1 ViT — dima806 (static)       V1 40% + V2 60% (live)
         │                               │
         └──────────┬────────────────────┘
                    │ visual_score
                    │
              Blink signal  ──── abnormal = –score
              Mouth movement ─── unnatural = –score
              Temporal jitter ── glitchy  = –0.4
                    │
             EXIF / watermark
             pre-scan flags
                    │
                    ▼
         Combined trust score
   0.0 ─────────────────────── 1.0
   🔴 Fake         🟠       🟢 Real
   < 0.5          0.5–0.7    ≥ 0.8
```

### Accelerator fallback

```
On boot, tries in order:
  1. NPU  (Qualcomm Hexagon — Snapdragon 8 Elite)
  2. GPU  (Adreno)
  3. CPU  (XNNPACK fallback)

LiteRT-LM (Gemma 4 2B):
  1. GPU  (Adreno)
  2. CPU
```

---

## Models used

### HuggingFace

| Model | Repo | Role |
|---|---|---|
| `deepfake_detector.tflite` (V1) | [dima806/deepfake_vs_real_image_detection](https://huggingface.co/dima806/deepfake_vs_real_image_detection) | Primary ViT — real vs. AI-generated face. Used alone for static images. |
| `deepfake_detector_v2.tflite` (V2) | Second fine-tuned ViT checkpoint | Ensemble partner in live mode — weighted 60% |
| `gemma-4-E2B-it_qualcomm_sm8750.litertlm` | [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) | On-device LLM — generates the plain-language explanation |

### On-device / Qualcomm

| Model | File | Role |
|---|---|---|
| HRNet face landmarks | `hrnet_face.tflite` | Facial keypoints — feeds mouth movement + asymmetry signal |
| Gemma 4 2B SM8750 | `gemma-4-E2B-it_qualcomm_sm8750.litertlm` | Pre-compiled for Snapdragon 8 Elite NPU via Qualcomm AI Hub |

---

## LiteRT integration

Uses the **LiteRT compiled model API** — not the old TFLite package.

```
com.google.ai.edge.litert:litert:2.1.4           core runtime (CompiledModel)
com.google.ai.edge.litert:litert-gpu:1.4.2       GPU delegate (Adreno)
com.google.ai.edge.litertlm:litertlm-android     LiteRT-LM for Gemma 4 2B
```

`DeepfakeDetector.kt` uses `CompiledModel.create()` and `Accelerator.NPU / GPU / CPU` — the new LiteRT API, not `Interpreter`.

---

## Tech stack

| Layer | What we used |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Camera | CameraX |
| ML runtime | LiteRT — `com.google.ai.edge.litert` |
| LLM runtime | LiteRT-LM — `com.google.ai.edge.litertlm` |
| Face tracking | ML Kit Face Detection |
| Screen capture | MediaProjection + WindowManager floating overlay |
| Forensic scan | EXIF / C2PA / IPTC / SynthID byte-level scan |
| Temporal analysis | Custom TemporalEngine (variance + jitter, 20-frame window) |
| Architecture | MVVM, Kotlin coroutines + Flow |
| Target device | Samsung Galaxy S25 Ultra (Android 15, API 35) |
| Min SDK | API 30 |

---

## Setting and Running the app

1. Clone the repo
2. Open in Android Studio (Ladybug or newer)
3. Build and run on a physical Android device (API 30+)
4. On first launch the app downloads `gemma-4-E2B-it_qualcomm_sm8750.litertlm` (~2 GB) automatically — wait for the progress bar to complete before scanning
5. Place `deepfake_detector.tflite` and `deepfake_detector_v2.tflite` in `app/src/main/assets/` (not in repo due to size)
6. For background protection mode: grant "Draw over other apps" permission when prompted

Demo tip: play a known deepfake clip on a second screen and point the live camera at it.

---

## Project structure

```
app/src/main/java/com/sentinedge/app/
├── ml/
│   ├── DeepfakeDetector.kt         — LiteRT CompiledModel, NPU/GPU/CPU fallback, dual-model ensemble
│   ├── BlinkTracker.kt             — ML Kit blink rate signal
│   ├── TemporalEngine.kt           — 20-frame variance + jitter analysis
│   ├── SafetyVerificationEngine.kt — EXIF / C2PA / IPTC / watermark forensic pre-scan
│   └── ModelDownloader.kt          — background download of Gemma 4 2B on first launch
├── source/
│   ├── FrameSource.kt              — sealed interface for all frame inputs
│   ├── LiveCameraSource.kt         — CameraX ImageAnalysis pipeline
│   └── DebugFileSource.kt          — video file pipeline for testing without hardware
├── service/
│   └── LiveProtectionService.kt    — foreground service: MediaProjection + floating overlay
├── ui/
│   ├── HomeScreen.kt               — three-mode launcher
│   ├── VideoAnalysisScreen.kt      — upload + analyse flow
│   ├── CameraScreen.kt             — live camera view
│   ├── ResultScreen.kt             — trust score + watermark flags + Gemma explanation
│   ├── TrustOverlay.kt             — animated score ring overlay
│   ├── DetectionBar.kt             — frame-by-frame detection bar
│   ├── ScanLineOverlay.kt          — scanning animation
│   ├── VerdictColor.kt             — green/amber/red color logic
│   └── MainScreen.kt               — top-level nav host
├── MainActivity.kt
└── MainViewModel.kt
```

## References Used:

1. AI Reasoning Engine (LLM)

Google AI Edge: Gemma on LiteRT: https://ai.google.dev/edge/litert-lm/overview

Hugging Face (Gemma-2b-it-TFLite): [google/gemma-2b-it-tflite](https://ai.google.dev/edge/litert-lm/overview)

2. Primary Deepfake Detectors (Vision)
Hugging Face (dima806 Ensemble): [dima806/deepfake_vs_real_image_detection](https://ai.google.dev/edge/litert-lm/overview)

3. Facial Landmark & Jitter Tracking
Research Paper (HRNet): Deep High-Resolution Representation Learning for Visual Recognition

Qualcomm AI Hub Optimization: [HRNetFace on Snapdragon 8 Elite](https://aihub.qualcomm.com/models/hrnet_face)

4. Face Detection & Liveness
Google ML Kit Face Detection Guide: ML Kit: [Face Detection for Android](https://aihub.qualcomm.com/models/hrnet_face)

5. Deployment & Hardware Acceleration
LiteRT (Formerly TensorFlow Lite) Documentation: LiteRT Core Runtime

LiteRT-LM (LLM Inference API): LLM Inference Guide

Qualcomm AI Hub: Snapdragon 8 Elite Model Catalog

6. Forensic Standards (Watermarks & Signatures)
C2PA (Content Authenticity Initiative) Official Site: c2pa.org

IPTC Metadata Standard: IPTC Photo Metadata Standard
