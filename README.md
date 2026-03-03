# Hush!

[![License: BSL 1.1](https://img.shields.io/badge/License-BSL%201.1-blue.svg)](LICENSE)

The AI dictation app that works in flight mode. Speak anywhere, transcribe on your phone, no cloud required.

<p align="center">
  <img src="screenshots/listening_baseline.png" width="280" alt="Hush listening screen" />
  &nbsp;&nbsp;
  <img src="screenshots/post_proccessing.png" width="280" alt="LLM post-processing settings" />
</p>

## Features

- **Privacy first** — on-device transcription by default, your audio never leaves your phone
- **Voice-to-text anywhere** — double-tap volume down to start/stop recording from any app
- **Auto-inject into text fields** — if a text field is focused, transcribed text is pasted directly at the cursor
- **Clipboard fallback** — text is always copied to clipboard, even when auto-inject is active
- **Background service** — persistent foreground notification with quick-action controls
- **Multi-provider transcription** — choose between Voxtral (Mistral), OpenAI Whisper, Groq, Local (on-device), or Moonshine (streaming)
- **Local on-device transcription** — Whisper tiny.en via ExecuTorch, no internet required
- **Moonshine streaming** — real-time on-device transcription via Moonshine SDK, live text as you speak
- **Streaming overlay** — floating overlay shows live transcription text in external apps, single paste on stop
- **Settings screen** — switch providers, configure API keys and models per provider
- **LLM post-processing** — optional AI cleanup of transcriptions (grammar, punctuation, formatting) via Anthropic or any OpenAI-compatible API. On-device post-processing is coming soon.
- **Custom blob/ring UI** — dark theme with animated glowing blobs and minimal ring-based mic button
- **Transcription history** — recent transcriptions stored locally with tap-to-copy
- **Usage dashboard** — streak tracking, transcription stats, weekly activity charts, cost estimates

## Setup Guide / Einrichtung

### English

#### Step 1: Install Hush

Hush is not on the Play Store yet — you install it directly from a file (this is called "sideloading"). Android will warn you about installing from unknown sources — that's a standard security prompt that appears for any app installed outside the Play Store. It exists because sideloaded apps skip Google's automated review. Since Hush is open source, you can inspect the code yourself or build it from source if you prefer. As a general rule, only sideload apps from sources you trust.

1. On your Android phone, go to the [Releases page](https://github.com/leonbubova/hush-app/releases/latest) and download the `.apk` file
2. Open the downloaded file — Android will ask if you want to install from this source, tap **Allow** (you only need to do this once)
3. Tap **Install**, then **Open**
4. Hush will ask for microphone and notification permissions — grant both

#### Step 2: Choose a transcription provider

Moonshine is pre-selected as the default provider — no API key needed. Just download the model and go:

- **Moonshine (streaming, default)** — works completely offline, no account or API key required. On first launch, go to Settings and download the model (one-time, ~26 MB). Once downloaded, you can dictate without internet — even in flight mode. Currently English only — German support is in the works.
- **Local (Whisper)** — also fully offline, no key needed. English only. Select it in Settings and download the model.

If you need multilingual support or higher accuracy, you can optionally use a cloud provider. This means your audio will be sent to that provider's servers for transcription — you bring your own API key, so there's no middleman and no Hush account involved.

- **Voxtral (Mistral):** [console.mistral.ai](https://console.mistral.ai/) → API Keys
- **OpenAI Whisper:** [platform.openai.com](https://platform.openai.com/) → API Keys
- **Groq:** [console.groq.com](https://console.groq.com/) → API Keys

To configure: open the drawer (hamburger menu) → **Settings** → select your provider → paste your API key.

#### Step 3: Start dictating

Open the app and tap the ring to start recording. Tap again to stop — your speech is transcribed and copied to the clipboard.

#### Step 4: Use it anywhere (optional)

This step is optional — you can skip it and just use Hush from inside the app. But if you want to dictate into *any* text field on your phone (messages, notes, browser, etc.), you need to enable the accessibility service:

1. Tap the "Enable background shortcut" banner in the app
2. This opens Android's accessibility settings
3. Find **Hush** in the list and enable it
4. Confirm the permission dialog

Once enabled, double-tap the volume down button to start dictating from anywhere. Double-tap again to stop. The transcribed text is automatically pasted into whatever text field you're typing in.

---

### Deutsch

#### Schritt 1: Hush installieren

Hush ist noch nicht im Play Store — du installierst die App direkt als Datei (nennt sich "Sideloading"). Android wird dich warnen, dass du aus unbekannten Quellen installierst — das ist eine Standard-Sicherheitsmeldung, die bei jeder App ausserhalb des Play Stores erscheint. Sie kommt, weil sidegeloadete Apps Googles automatische Pruefung ueberspringen. Da Hush Open Source ist, kannst du den Code selbst einsehen oder die App aus dem Quellcode bauen. Generell gilt: installiere nur Apps aus Quellen, denen du vertraust.

1. Oeffne auf deinem Android-Handy die [Releases-Seite](https://github.com/leonbubova/hush-app/releases/latest) und lade die `.apk`-Datei herunter
2. Oeffne die heruntergeladene Datei — Android fragt, ob du aus dieser Quelle installieren moechtest, tippe auf **Zulassen** (nur beim ersten Mal noetig)
3. Tippe auf **Installieren**, dann auf **Oeffnen**
4. Hush fragt nach Mikrofon- und Benachrichtigungsrechten — beides erlauben

#### Schritt 2: Anbieter waehlen

Moonshine ist als Standard-Anbieter vorausgewaehlt — kein API-Key noetig. Einfach Modell herunterladen und los:

- **Moonshine (Streaming, Standard)** — funktioniert komplett offline, kein Account oder API-Key noetig. Beim ersten Start in die Einstellungen gehen und das Modell herunterladen (einmalig, ~26 MB). Danach kannst du ohne Internet diktieren — sogar im Flugmodus. Aktuell nur Englisch — Deutsch ist in Arbeit.
- **Local (Whisper)** — ebenfalls komplett offline, kein Key noetig. Nur Englisch. In den Einstellungen auswaehlen und Modell herunterladen.

Wenn du mehrsprachige Unterstuetzung oder hoehere Genauigkeit brauchst, kannst du optional einen Cloud-Anbieter nutzen. Dabei wird dein Audio an die Server des Anbieters geschickt — du verwendest deinen eigenen API-Key, es gibt keinen Mittelsmann und keinen Hush-Account.

- **Voxtral (Mistral):** [console.mistral.ai](https://console.mistral.ai/) → API Keys
- **OpenAI Whisper:** [platform.openai.com](https://platform.openai.com/) → API Keys
- **Groq:** [console.groq.com](https://console.groq.com/) → API Keys

Zum Einrichten: Menue oeffnen (Hamburger-Symbol) → **Settings** → Anbieter waehlen → API-Key einfuegen.

#### Schritt 3: Diktieren

Oeffne die App und tippe auf den Ring um die Aufnahme zu starten. Nochmal tippen zum Stoppen — dein Text wird transkribiert und in die Zwischenablage kopiert.

#### Schritt 4: Ueberall nutzen (optional)

Dieser Schritt ist optional — du kannst Hush auch einfach nur in der App benutzen. Wenn du aber in *jedes* Textfeld auf deinem Handy diktieren moechtest (Nachrichten, Notizen, Browser, etc.), musst du die Bedienungshilfe aktivieren:

1. Tippe auf das Banner "Enable background shortcut" in der App
2. Es oeffnen sich die Android-Bedienungshilfe-Einstellungen
3. Finde **Hush** in der Liste und aktiviere es
4. Bestaetige den Berechtigungsdialog

Danach: doppelt auf die Leiser-Taste druecken um von ueberall zu diktieren. Nochmal doppelt zum Stoppen. Der Text wird automatisch in das aktive Textfeld eingefuegt.

## Architecture

```
MainActivity          — Jetpack Compose UI, permissions, navigation
MainViewModel         — state management, service binding, provider config
DictationService      — foreground service, recording orchestration, clipboard + broadcast
HushAccessibilityService — volume key interception, auto-inject via ACTION_PASTE
AudioRecorder         — MediaRecorder wrapper for audio capture
SettingsScreen        — provider selection and per-provider configuration UI
HushApp               — Application class, notification channel setup
UsageScreen           — Compose usage dashboard (streak, charts, heatmap, cost)
UsageRepository       — session persistence (SharedPreferences + JSON)
HistoryRepository     — transcription history persistence (EncryptedSharedPreferences + JSON)
TestTags              — central registry of Compose testTag constants

transcription/
  TranscriptionProvider — interface for all transcription backends
  TranscribeResult      — sealed class: Success(text) | Error(code, message)
  ProviderConfig        — sealed config hierarchy (Voxtral, OpenAI, Groq, Local, Moonshine)
  ProviderRepository    — encrypted persistence + legacy API key migration
  ProviderFactory       — resolves active provider from config (isStreaming() check for Moonshine)
  VoxtralProvider       — Mistral Voxtral API client
  OpenAiWhisperProvider — OpenAI Whisper API client
  GroqProvider          — Groq API client (OpenAI-compatible)
  LocalProvider         — on-device inference via ExecuTorch (encoder-decoder pipeline)
  AudioConverter        — M4A/AAC → 16kHz mono float PCM conversion
  MelSpectrogram        — 80-channel log-mel spectrogram (N_FFT=400, hop=160, 3000 frames)
  WhisperTokenizer      — BPE token ID → text decoding (50k vocab from assets)
  ModelManager          — model download, storage, and lifecycle management
  MoonshineProvider     — streaming on-device transcription via Moonshine SDK
  StreamingOverlayManager — floating TYPE_ACCESSIBILITY_OVERLAY for live streaming text in external apps
  PostProcessorConfig   — configuration for LLM post-processing (API type, key, model, prompt)
  TextPostProcessor     — LLM-based transcription cleanup via Anthropic or OpenAI-compatible APIs
```

### Auto-inject flow

```
DictationService.stopRecording()
  → ProviderFactory.resolve(context) → provider.transcribe(file)
  → copyToClipboard(text)
  → sendBroadcast(ACTION_INJECT_TEXT)
  → HushAccessibilityService receives broadcast
  → findFocus(FOCUS_INPUT)
  → if found: performAction(ACTION_PASTE)
  → if not found: no-op (text already on clipboard)
```

### Streaming flow (Moonshine)

```
DictationService.startStreaming()
  → MoonshineProvider.start() → live callbacks (onLineTextChanged, onLineCompleted)
  → if external app: broadcast ACTION_OVERLAY_SHOW → StreamingOverlayManager shows live text
  → on stop: copyToClipboard(finalText)
  → broadcast ACTION_OVERLAY_DISMISS → dismiss overlay
  → delayed broadcast ACTION_INJECT_TEXT → ACTION_PASTE (single paste)
```

## Tech stack

- Kotlin + Jetpack Compose
- Android AccessibilityService for global hotkey + text injection
- Multi-provider transcription: Voxtral (Mistral), OpenAI Whisper, Groq, Local (on-device)
- ExecuTorch for on-device ML inference (Whisper tiny.en)
- OkHttp for API calls
- EncryptedSharedPreferences for secure credential storage
- Moonshine SDK (`ai.moonshine:moonshine-voice`) for streaming on-device transcription
- LLM post-processing via Anthropic Messages API or OpenAI-compatible Chat Completions API
- Coroutines for async transcription

## Compatibility

- Android 8.0 (Oreo) and up — minSdk 26
- Tested on Pixel 9 Pro (Android 15)

## Building

Requires JDK 17 and Android SDK (no Android Studio needed):

```bash
# Set environment (adjust paths for your system)
export JAVA_HOME=/path/to/jdk17        # e.g. /opt/homebrew/opt/openjdk@17 (macOS)
export ANDROID_HOME=/path/to/android-sdk # e.g. ~/Android/Sdk (Linux), /opt/homebrew/share/android-commandlinetools (macOS)

# Debug build (com.hush.app.debug — "Hush Dev")
./gradlew assembleDebug

# Release build (com.hush.app — "Hush", requires signing config)
./gradlew assembleRelease
```

### Debug vs Release builds

Debug builds use a separate application ID (`com.hush.app.debug`) and app name ("Hush Dev"), so they can be installed side-by-side with the release build on the same device. This prevents a broken dev build from replacing the stable production app.

| | Debug | Release |
|---|---|---|
| Package | `com.hush.app.debug` | `com.hush.app` |
| App name | Hush Dev | Hush |
| Signing | debug keystore | release keystore |

For release builds, add to `local.properties` (not committed to git):

```
RELEASE_STORE_FILE=/path/to/keystore
RELEASE_STORE_PASSWORD=your-password
RELEASE_KEY_ALIAS=your-alias
RELEASE_KEY_PASSWORD=your-password
```

Install on device:

```bash
# Debug (arm64 ABI split)
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# Release
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Permissions

- `RECORD_AUDIO` — microphone access for dictation
- `INTERNET` — sending audio to transcription API
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` — background recording
- `POST_NOTIFICATIONS` — status notification
- Accessibility Service — volume key detection + text field injection

## On-Device Transcription

Hush is built around on-device transcription — your audio is processed locally and never sent to any server. No account, no API key, no internet connection needed.

Two engines are available:

### Moonshine (streaming)

Real-time transcription that shows text as you speak. Runs entirely on your phone — works in flight mode. Uses the Moonshine SDK with ONNX Runtime models.

| Model | Size | Type | Language |
|---|---|---|---|
| Moonshine tiny | ~26 MB | Streaming (live text) | English |

1. Open Settings → select "Moonshine"
2. Download the model (one-time, ~26 MB)
3. Start dictating — text appears in real time as you speak

### Whisper (batch)

Transcribes after you finish speaking. Uses ExecuTorch with quantized Whisper models.

| Model | Size | Quantization | Language |
|---|---|---|---|
| Whisper tiny.en INT4 | ~132 MB | 4-bit | English |
| Whisper tiny.en INT8 | ~136 MB | 8-bit | English |
| Whisper tiny.en FP32 | ~220 MB | Full precision | English |

1. Open Settings → select "Local (On-Device)"
2. Choose a model variant and download it
3. Start dictating — works fully offline

INT4 is the default and recommended for most devices — smallest download with minimal quality loss.

### Exporting Whisper models

Models are exported using [`optimum-executorch`](https://github.com/huggingface/optimum-executorch) with 3 quantization variants (INT4, INT8, FP32). See the [hush-app-models](https://github.com/leonbubova/hush-app-models) repo for export scripts and pre-built models.

## Testing

### Unit tests (no device needed)

```bash
./gradlew testDebugUnitTest
```

| Suite | Tests | Coverage |
|---|---|---|
| `VoxtralApiTest` | 12 | HTTP responses, auth, multipart, real audio upload |
| `OpenAiWhisperProviderTest` | 11 | Same + language param handling |
| `GroqProviderTest` | 11 | HTTP responses, auth, multipart |
| `ProviderConfigTest` | 14 | JSON round-trip, defaults, edge cases |
| `ProviderFactoryTest` | 9 | Provider resolution, display names, fallback, local provider |
| `AudioConverterTest` | 10 | Resampling math, stereo→mono, Int16→Float32, normalization |
| `LocalProviderTest` | 6 | Model not downloaded errors, metadata, requiresNetwork=false |
| `ModelManagerTest` | 10 | Model status, file paths, download/delete, available models |
| `UsageRepositoryTest` | 8 | Record/load/clear, MAX_SESSIONS cap, malformed JSON |
| `HistoryRepositoryTest` | 6 | Add/load/clear, prepend order, blank text no-op, accumulation |
| `MelSpectrogramTest` | 12 | Output shape, Hann window, mel filterbank, sine wave energy, log scaling |
| `WhisperTokenizerTest` | 12 | BPE decoding, EOS/special token filtering, vocab loading, known phrases |
| `MainViewModelTest` | 22 | Init state, navigation, provider management, history, usage, service toggle |
| `ProviderRepositoryTest` | 10 | Active provider, config roundtrip, migration, malformed JSON, all configs |
| `PostProcessorConfigTest` | 5 | Default values, JSON round-trip, missing fields, empty JSON |
| `TextPostProcessorTest` | 14 | MockWebServer: Anthropic/OpenAI success, headers, errors, fallback, trimming |

### Instrumented tests (emulator needed)

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

| Suite | Tests | Coverage |
|---|---|---|
| `HomeScreenStateTest` | 10 | All 5 dictation states, history items, empty history, accessibility banner, mic button |
| `NavigationTest` | 8 | Drawer open/close, navigate to Settings/Usage/Home via drawer |
| `SettingsScreenTest` | 6 | Provider options, default selection, Local model UI, API key field, save button |
| `DictationFlowE2ETest` | 5 | Fresh-install record→error flow, empty history, mic button, drawer button, accessibility |
| `SettingsFlowE2ETest` | 4 | Switch providers, Local shows download, persist selection across navigation |
| `AudioConverterInstrumentedTest` | 3 | M4A decode to 16kHz mono PCM, normalized range, non-silent output |
| `TranscriptionE2ETest` | 6 | Real audio-to-API transcription (skipped if API key not set) |

All UI tests use Compose `testTag` semantics via `TestTags.kt` — no coordinate tapping or UIAutomator XML dumps.

#### TranscriptionE2ETest

Real audio-to-API transcription tests (skipped if API key not set):

- Sends `test_audio.m4a` ("Hello this is a test for end to end testing") to each provider
- Asserts transcription contains expected words
- Keys are read from `local.properties` (gitignored):

```properties
TEST_VOXTRAL_KEY=your-key
TEST_OPENAI_KEY=your-key
TEST_GROQ_KEY=your-key
```

#### Prerequisites

- AVD named `hush-test` (Pixel 7 profile, 1080x2400 @ 420dpi, API 34)
- No prior install of `com.hush.app.debug` on the emulator (for DictationFlowE2ETest)

#### Environment setup

```bash
# Ensure JAVA_HOME and ANDROID_HOME are set (see Building section above)
EMU=$ANDROID_HOME/emulator/emulator
ADB=$ANDROID_HOME/platform-tools/adb
```

#### Running

```bash
# Start emulator (headless)
$EMU -avd hush-test -no-window -no-audio -no-snapshot-load -gpu host &
$ADB wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'

# Clean state for DictationFlowE2ETest
$ADB -s emulator-5554 uninstall com.hush.app.debug || true

# Run all instrumented tests
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest

# Pull screenshots
$ADB -s emulator-5554 pull /sdcard/Pictures/hush-tests/ screenshots/
```

## AI-Assisted Development

This project is developed with [Claude Code](https://claude.ai/claude-code). The repo includes dev tooling for emulator-based UI verification.

### Emulator helper (`scripts/emu.sh`)

A shell script that wraps all ADB interactions into single commands — navigation, tapping by visible text, screenshots, log inspection. Eliminates raw ADB boilerplate and multi-step `uiautomator dump` → `grep bounds` → `tap` workflows.

```bash
scripts/emu.sh build              # build + install on emulator
scripts/emu.sh launch             # start app
scripts/emu.sh nav settings       # open drawer → tap Settings
scripts/emu.sh tap "Button Text"  # find element by text → tap (auto-scrolls if off-screen)
scripts/emu.sh screenshot name    # save to dev-screenshots/
scripts/emu.sh dump               # show visible text elements
scripts/emu.sh logcat             # recent app errors
scripts/emu.sh help               # full command list
```

The script lives in `scripts/` (gitignored — contains device-specific paths). It is self-maintaining: when commands break due to UI changes, they should be fixed in-place. See `.claude/rules/emulator.md` for the full reference and maintenance rules.

## Privacy & Security

- **No data collection** — Hush has no analytics, no telemetry, no servers. Nothing leaves your phone unless you explicitly choose a cloud provider.
- **On-device by default** — Moonshine and Whisper run entirely on your phone, even in flight mode
- **Cloud is opt-in** — if you choose a cloud provider, audio is sent directly to that provider using your own API key. Hush never sees or stores your audio.
- API keys are stored in Android EncryptedSharedPreferences (AES-256)
- No credentials are included in the APK — each user provides their own API key
- Full credential audit performed 2026-02-24: no API keys, passwords, or secrets in codebase or git history

## Acknowledgements

Hush's on-device streaming transcription is powered by [Moonshine](https://github.com/usefulsensors/moonshine) from [Useful Sensors](https://usefulsensors.com/). Moonshine is the reason Hush can transcribe your voice in real time, entirely on your phone, with no internet connection — even in flight mode. It's fast, it's tiny (~26 MB), and it just works. Discovering Moonshine was one of those moments where everything clicked — it's exactly the kind of technology that makes privacy-first apps like this possible. Huge thanks to [Pete Warden](https://github.com/petewarden) and the Useful Sensors team for building something so good and making it available to the open-source community. ❤️
