# FlowVoice — Android AI Dictation App

## TL;DR

Android dictation app competing with Wispr Flow (no Android app exists). **Core loop:** double-tap volume → record voice → Voxtral API transcription → clipboard. Native Kotlin + Jetpack Compose for full Android service access. **MVP is working and tested on a Pixel 9 Pro.**

---

## Current Status: Auto-inject Working ✓

The following works end-to-end on a physical Pixel 9 Pro:

1. App launches with dark Material 3 UI
2. Prompts for microphone + notification permissions
3. Prompts for Mistral API key (stored in SharedPreferences)
4. Foreground service starts with persistent notification
5. **Double-tap volume down** toggles recording **from any app** (via accessibility service)
6. **Notification action button** toggles recording
7. Notification updates: idle → recording → processing → done
8. Audio recorded as m4a via MediaRecorder
9. Sent to Voxtral batch API (`POST /v1/audio/transcriptions`)
10. Transcription copied to clipboard automatically
11. **If a text field is focused, transcription is auto-pasted at cursor position**

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
│       │   ├── MainActivity.kt        # Compose UI, permissions
│       │   ├── MainViewModel.kt       # UI state, service binding, API key management
│       │   ├── DictationService.kt    # Foreground service, notification, recording, clipboard + broadcast
│       │   ├── FlowVoiceAccessibilityService.kt  # Volume key hotkey + auto-inject
│       │   ├── AudioRecorder.kt       # MediaRecorder wrapper (m4a/AAC output)
│       │   ├── VoxtralApi.kt          # OkHttp multipart POST to Mistral API
│       │   └── ui/theme/Theme.kt      # Material 3 dark theme with dynamic colors
│       └── res/
│           ├── drawable/ic_mic.xml, ic_mic_active.xml
│           ├── values/strings.xml, themes.xml, colors.xml
│           ├── xml/accessibility_config.xml  # Accessibility service config
│           └── mipmap-hdpi/ic_launcher.xml
```

## Architecture

### Core Flow
```
FlowVoiceAccessibilityService (global volume key listener)
    → double-tap volume down → starts DictationService

MainActivity (Compose UI)
    ↕ binds to
DictationService (foreground service + notification)
    → AudioRecorder (MediaRecorder → .m4a file)
    → VoxtralApi (OkHttp multipart POST → transcription text)
    → ClipboardManager (copies result)
    → sendBroadcast(ACTION_INJECT_TEXT)
    → NotificationManager (updates status)

FlowVoiceAccessibilityService (receives broadcast)
    → findFocus(FOCUS_INPUT) → performAction(ACTION_PASTE)
```

### Key Technical Details

- **Volume button detection:** AccessibilityService `onKeyEvent()`, 400ms double-tap window, works from any app
- **Auto-inject:** After clipboard copy, broadcast triggers ACTION_PASTE into focused input field; no-op if nothing focused
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

---

## Session Log

### Session 1 — 2026-02-23: MVP Built and Working

**Timeline:**
1. Started with zero Android dev environment (no Java, no SDK, no Android Studio)
2. Chose native Kotlin + Jetpack Compose over Expo/React Native — right call, the app is 80% native services
3. Installed entire toolchain via Homebrew in parallel while writing code (~700MB: OpenJDK 17, Android SDK 35, platform-tools)
4. Scaffolded full project from scratch: 7 Kotlin files, Gradle config, manifest, resources
5. First build attempt failed — `dependencyResolution` API wrong in settings.gradle.kts, fixed to `dependencyResolutionManagement`
6. Second build failed — compileSdk 34 too old for androidx.core 1.15.0, bumped to 35
7. Third build: success! 9.6MB APK
8. USB debugging setup with Pixel 9 Pro was a journey — flaky cable, had to replug multiple times, device kept disappearing
9. Installed APK, first test: "Error — check API key" on every recording
10. Added logging, rebuilt, reinstalled — discovered **INTERNET permission was missing from manifest** (classic Android gotcha)
11. One-line fix, rebuild, reinstall — **IT WORKS**. Full loop: double-tap volume → record → Voxtral transcription → clipboard

**The moment:** First successful transcription copied to clipboard from voice on a Pixel 9 Pro. The entire MVP — from zero toolchain to working app — done in a single session with Claude Code.

**What went right:**
- Parallel strategy (install toolchain while writing code) saved significant time
- Native Kotlin was the right call — no framework abstraction fighting us
- Voxtral batch API is dead simple (multipart POST, m4a in, text out)
- Foreground service + notification bar worked first try

**What bit us:**
- Forgot INTERNET permission (every Android dev has done this at least once)
- USB cable issues — data connection kept dropping
- No way to see app logs without device connected (added logging after the fact)

**Code analysis done post-MVP (found several issues to fix):**
- `onStateChanged` lambda drops the transcription text — last transcription card never renders
- All errors show same "Check API key" message regardless of actual cause
- `AudioRecorder.start()` has no try/catch — permission denial would crash
- `VoxtralApi` swallows HTTP error codes — 401 vs 429 vs 503 all become `null`
- `onStartCommand` unconditionally calls `startForeground` with IDLE notification — flashes notification state

**Git:** Initial commit `0e6fa39` — "Initial MVP: working dictation app with Voxtral transcription"

### Session 2 — 2026-02-23: Accessibility Service + Auto-inject

**What was built:**
1. `FlowVoiceAccessibilityService` — global double-tap volume down hotkey that works from any app (not just in-app)
2. Auto-inject feature — transcribed text automatically pastes into any focused text field
3. Bug fixes from post-MVP code review (error handling, AudioRecorder safety, VoxtralApi HTTP error codes)
4. Unit tests for VoxtralApi
5. README.md

**Auto-inject flow:**
```
DictationService → copyToClipboard(text) → sendBroadcast(ACTION_INJECT_TEXT)
  → FlowVoiceAccessibilityService receives broadcast
  → rootInActiveWindow.findFocus(FOCUS_INPUT)
  → if found: performAction(ACTION_PASTE)
  → if not: no-op (text already on clipboard)
```

**Key details:**
- Accessibility config needs `canRetrieveWindowContent="true"` and `flagRetrieveInteractiveWindows`
- Broadcast is app-scoped (`setPackage(packageName)` + `RECEIVER_NOT_EXPORTED`)
- Tested on Pixel 9 Pro — worked first try

**Git:** `d3005ca` — "Add accessibility service with auto-inject into focused text fields"
