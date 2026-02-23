# Hush

Android AI dictation app — speak anywhere, transcribe instantly. A native alternative to Wispr Flow.

## Features

- **Voice-to-text anywhere** — double-tap volume down to start/stop recording from any app
- **Auto-inject into text fields** — if a text field is focused, transcribed text is pasted directly at the cursor
- **Clipboard fallback** — text is always copied to clipboard, even when auto-inject is active
- **Background service** — persistent foreground notification with quick-action controls
- **Voxtral transcription** — powered by Mistral's Voxtral speech-to-text API
- **Custom blob/ring UI** — dark theme with animated glowing blobs and minimal ring-based mic button
- **Transcription history** — recent transcriptions stored locally with tap-to-copy

## Setup Guide / Einrichtung

### English

#### Step 1: Get a Mistral API key

1. Go to [console.mistral.ai](https://console.mistral.ai/) and create an account (or sign in)
2. Navigate to **API Keys** in the left sidebar ([direct link](https://console.mistral.ai/api-keys/))
3. Click **Create new key**
4. Give it a name (e.g. "Hush") and click **Create**
5. Copy the key — you'll need it in the next step

#### Step 2: Install Hush

1. Download the APK and open it on your Android phone
2. If prompted, allow installation from unknown sources
3. Open Hush — it will ask for microphone and notification permissions, grant both

#### Step 3: Enter your API key

1. Tap the gear icon (top right)
2. Paste your Mistral API key
3. Tap **Save**

#### Step 4: Enable the accessibility service

1. Tap the "Enable background shortcut" banner in the app
2. This opens Android's accessibility settings
3. Find **Hush** in the list and enable it
4. Confirm the permission dialog

#### Step 5: Start dictating

- **In the app:** tap the ring to start/stop recording
- **From anywhere:** double-tap volume down to start, double-tap again to stop
- Transcribed text is automatically pasted into the focused text field, or copied to clipboard

---

### Deutsch

#### Schritt 1: Mistral API-Key erstellen

1. Gehe zu [console.mistral.ai](https://console.mistral.ai/) und erstelle ein Konto (oder melde dich an)
2. Klicke links auf **API Keys** ([Direktlink](https://console.mistral.ai/api-keys/))
3. Klicke auf **Create new key**
4. Vergib einen Namen (z.B. "Hush") und klicke **Create**
5. Kopiere den Key — du brauchst ihn gleich

#### Schritt 2: Hush installieren

1. Lade die APK herunter und oeffne sie auf deinem Android-Handy
2. Falls noetig, erlaube die Installation aus unbekannten Quellen
3. Oeffne Hush — die App fragt nach Mikrofon- und Benachrichtigungsrechten, beides erlauben

#### Schritt 3: API-Key eingeben

1. Tippe auf das Zahnrad-Symbol (oben rechts)
2. Fuege deinen Mistral API-Key ein
3. Tippe auf **Save**

#### Schritt 4: Bedienungshilfe aktivieren

1. Tippe auf das Banner "Enable background shortcut" in der App
2. Es oeffnen sich die Android-Bedienungshilfe-Einstellungen
3. Finde **Hush** in der Liste und aktiviere es
4. Bestaetige den Berechtigungsdialog

#### Schritt 5: Diktieren

- **In der App:** Tippe auf den Ring um die Aufnahme zu starten/stoppen
- **Von ueberall:** Doppelt auf Leiser-Taste druecken zum Starten, nochmal doppelt zum Stoppen
- Der transkribierte Text wird automatisch in das aktive Textfeld eingefuegt oder in die Zwischenablage kopiert

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
