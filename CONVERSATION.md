# FlowVoice — Android AI Dictation App

## TL;DR

Android dictation app competing with Wispr Flow (no Android app exists). **Core loop:** double-tap volume → record voice → Voxtral API transcription → clipboard. Native Kotlin + Jetpack Compose for full Android service access. **MVP is working and tested on a Pixel 9 Pro.**

---

## Current Status: MVP Complete ✓

The following works end-to-end on a physical device:

1. App launches with dark Material 3 UI
2. Prompts for microphone + notification permissions
3. Prompts for Mistral API key (stored in SharedPreferences)
4. Foreground service starts with persistent notification
5. **Double-tap volume down** (in-app) toggles recording
6. **Notification action button** toggles recording
7. Notification updates: idle → recording → processing → done
8. Audio recorded as m4a via MediaRecorder
9. Sent to Voxtral batch API (`POST /v1/audio/transcriptions`)
10. Transcription copied to clipboard automatically

---

## Decisions Made

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Framework | **Native Kotlin + Jetpack Compose** | Core value is native Android services. 80% of the app IS native code. Compose gives modern UI with Material 3. |
| Transcription | **Voxtral API** (Mistral) batch endpoint | Simple multipart POST, m4a directly supported. Realtime WebSocket available for future streaming. |
| Build toolchain | **Homebrew CLI-only** (no Android Studio) | OpenJDK 17 + Android SDK cmdline tools + Gradle wrapper. Lean ~700MB. |
| Device | **Pixel 9 Pro** via USB/ADB | ⚠️ Safety rule: always ask before running any ADB command. |

## Project Structure

```
wispr-killer/
├── build.gradle.kts              # Root: AGP 8.7.3, Kotlin 2.1.0
├── settings.gradle.kts           # dependencyResolutionManagement
├── gradle.properties
├── local.properties              # SDK path (gitignored)
├── gradlew / gradlew.bat
├── CONVERSATION.md               # This file
├── app/
│   ├── build.gradle.kts          # compileSdk 35, minSdk 26, Compose BOM, OkHttp
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions: INTERNET, RECORD_AUDIO, FOREGROUND_SERVICE, etc.
│       ├── java/com/flowvoice/app/
│       │   ├── FlowVoiceApp.kt       # Application class, notification channel setup
│       │   ├── MainActivity.kt        # Compose UI, volume button detection, permissions
│       │   ├── MainViewModel.kt       # UI state, service binding, API key management
│       │   ├── DictationService.kt    # Foreground service, notification, recording orchestration
│       │   ├── AudioRecorder.kt       # MediaRecorder wrapper (m4a/AAC output)
│       │   ├── VoxtralApi.kt          # OkHttp multipart POST to Mistral API
│       │   └── ui/theme/Theme.kt      # Material 3 dark theme with dynamic colors
│       └── res/
│           ├── drawable/ic_mic.xml, ic_mic_active.xml
│           ├── values/strings.xml, themes.xml, colors.xml
│           └── mipmap-hdpi/ic_launcher.xml
```

## Architecture

### Core Flow
```
MainActivity (Compose UI + volume key listener)
    ↕ binds to
DictationService (foreground service + notification)
    → AudioRecorder (MediaRecorder → .m4a file)
    → VoxtralApi (OkHttp multipart POST → transcription text)
    → ClipboardManager (copies result)
    → NotificationManager (updates status)
```

### Key Technical Details

- **Volume button detection:** `onKeyDown()` in Activity, 400ms double-tap window
- **Foreground service:** `FOREGROUND_SERVICE_MICROPHONE` type, `START_STICKY`
- **Notification:** Low importance channel, silent, with toggle action button
- **API:** `POST https://api.mistral.ai/v1/audio/transcriptions`, multipart/form-data, `Authorization: Bearer` header
- **Audio format:** m4a (AAC in MPEG-4), 44.1kHz, 128kbps — directly supported by Voxtral
- **State flow:** IDLE → RECORDING → PROCESSING → DONE/ERROR

## Dev Environment

```bash
# Build
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
cd "/path/to/project"
./gradlew assembleDebug

# Install (ask permission first!)
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# Logs
$ANDROID_HOME/platform-tools/adb logcat -s "VoxtralApi:*" "DictationService:*"
```

## Bugs Fixed

| Bug | Cause | Fix |
|-----|-------|-----|
| `dependencyResolution` build error | Wrong Gradle API name | Changed to `dependencyResolutionManagement` |
| compileSdk 34 incompatible with androidx.core 1.15.0 | Dependency requires SDK 35 | Installed platform-35, updated compileSdk |
| "Check API key" error on every request | Missing INTERNET permission in manifest | Added `<uses-permission android:name="android.permission.INTERNET" />` |

## Key Resources
- [Voxtral API docs](https://docs.mistral.ai/capabilities/audio_transcription) — batch + realtime endpoints
- [DontKillMyApp.com](https://dontkillmyapp.com) — OEM battery killing patterns
- [Android Accessibility Service docs](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [VoiceInteractionService docs](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)
