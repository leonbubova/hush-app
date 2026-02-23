# FlowVoice

Android AI dictation app — speak anywhere, transcribe instantly. A native alternative to Wispr Flow.

## Features

- **Voice-to-text anywhere** — double-tap volume down to start/stop recording from any app
- **Auto-inject into text fields** — if a text field is focused, transcribed text is pasted directly at the cursor
- **Clipboard fallback** — text is always copied to clipboard, even when auto-inject is active
- **Background service** — persistent foreground notification with quick-action controls
- **Voxtral transcription** — powered by Mistral's Voxtral speech-to-text API

## How it works

1. Enable the FlowVoice accessibility service in Settings → Accessibility
2. Enter your Voxtral API key in the app
3. Double-tap volume down to start recording (works from any app)
4. Double-tap again to stop — audio is sent to Voxtral for transcription
5. If a text field is focused, the transcription is auto-inserted; otherwise it's copied to clipboard

## Architecture

```
MainActivity          — Jetpack Compose UI, permissions, API key setup
MainViewModel         — state management, service binding
DictationService      — foreground service, recording orchestration, clipboard + broadcast
FlowVoiceAccessibilityService — volume key interception, auto-inject via ACTION_PASTE
AudioRecorder         — MediaRecorder wrapper for WAV capture
VoxtralApi            — Mistral Voxtral API client
FlowVoiceApp          — Application class, notification channel setup
```

### Auto-inject flow

```
DictationService.stopRecording()
  → copyToClipboard(text)
  → sendBroadcast(ACTION_INJECT_TEXT)
  → FlowVoiceAccessibilityService receives broadcast
  → findFocus(FOCUS_INPUT)
  → if found: performAction(ACTION_PASTE)
  → if not found: no-op (text already on clipboard)
```

## Tech stack

- Kotlin + Jetpack Compose
- Android AccessibilityService for global hotkey + text injection
- Mistral Voxtral API for speech-to-text
- Coroutines for async transcription

## Building

Requires JDK 17 and Android SDK (no Android Studio needed):

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew assembleDebug
```

Install on device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

- `RECORD_AUDIO` — microphone access for dictation
- `INTERNET` — sending audio to Voxtral API
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` — background recording
- `POST_NOTIFICATIONS` — status notification
- Accessibility Service — volume key detection + text field injection
