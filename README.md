# Fake Away

**Fake Away** is a native Android (Kotlin + Jetpack Compose) verification suite built with **Vertical Slice + Clean Code Architecture** and complete **Arabic (`ar`), English (`en`), and Kurdish Sorani (`ckb`)** localization.

## Core Capabilities

1. **Offline Voice AI Detective (`features/voice_detector`)**
   - Multi-signal acoustic DSP forensics (high-frequency vocoder cutoff, harmonic spectral flatness, zero-floor digital silence ratio, pitch/ZCR micro-jitter rigidity, Mel trajectory smoothness) with 2s-window **Interactive Waveform Timeline Heatmap**.
   - Optional on-device quantized **Wav2Vec2 Anti-Spoof ONNX** pack (`63.4 MB`).
   - Direct `ACTION_SEND` sharing from **WhatsApp**, **Telegram**, and local audio/video files (decoded offline via `MediaExtractor` + `MediaCodec`).

2. **Floating Screen & Audio Shield (`features/screen_shield`)**
   - Draggable floating shield overlay (`SYSTEM_ALERT_WINDOW` + `MediaProjection`) over **Facebook**, **TikTok**, **WhatsApp**, or any app.
   - **Scan Screen:** Offline Tesseract OCR (`Arabic+eng` covering Arabic, Kurdish Sorani, and English) + 2D FFT spectral grid check + 8×8 spatial manipulation heatmap + C2PA/AI metadata scanner + optional **SMOGY Swin ONNX** vision model (`50.1 MB`).
   - **Scan Playing Audio:** Captures internal playing audio via `AudioPlaybackCapture` (Android 10+) with automatic microphone fallback.

3. **Hybrid Trilingual Fact-Checker (`features/fact_checker`) + Tathabbat Newsroom Backend (`backend/`)**
   - Always runs strong offline linguistic forensics (clickbait lexicon, urgency formatting, official source attribution, synthetic text uniformity) across **Arabic, English, and Kurdish Sorani**.
   - Online mode checks the integrated **Tathabbat (تثبّت – عين نينوى)** FastAPI Newsroom backend for human-verified verdicts, enriches with **Google News RSS + Wikipedia** corroboration and optional **Gemini Search Grounding**, and supports 1-tap mobile claim escalation (`POST /api/mobile/escalate`).

## Vertical Slice Structure

```
e:\hackathin
├── app/                     # Native Android Kotlin + Jetpack Compose (Fake Away)
│   └── src/main/java/com/fakeaway/app/
│       ├── core/            # Domain, FFT DSP, WAV/MediaCodec, ONNX, Room, i18n, RTL Theme
│       └── features/        # voice_detector, screen_shield, fact_checker, share_router, history, settings
└── backend/                 # Hardened FastAPI + SQLite Newsroom (Tathabbat | عين نينوى)
    ├── app/
    │   ├── core/            # Settings, SQLite auto-init, X-API-Key & URL security, AR/CKB/EN normalizer
    │   └── features/        # news_search, claims (with Audit trail), mobile_bridge
    ├── static/index.html    # Trilingual RTL/LTR Newsroom Dashboard
    └── tests/               # pytest suite (normalizer, security/XSS/CSV fixes, mobile E2E)
```

## Build & Verify

### 1. Run Tathabbat Newsroom Backend & Tests

```powershell
cd e:\hackathin\backend
python -m pytest -v
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 2. Build Android Tests & Signed Production APK (`app-release.apk`)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\MSI\AppData\Local\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleRelease
```

Optional script to pre-bundle quantized ONNX packs inside APK assets:

```powershell
.\scripts\fetch_offline_models.ps1
```
