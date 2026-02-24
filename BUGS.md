# Hush — Known Bugs & Issues

## Found during post-MVP iteration (2026-02-23)

### BUG-001: Crash if RECORD_AUDIO permission not granted before service start
- **Severity:** Critical (app crash)
- **Found on:** Emulator (Android 14, cold install)
- **Repro:** Fresh install → app launches → ViewModel calls `startForegroundService()` immediately → `startForeground()` with `foregroundServiceType="microphone"` throws `SecurityException` because `RECORD_AUDIO` hasn't been granted yet
- **Root cause:** `MainViewModel.init` calls `startAndBindService()` synchronously, before the permission prompt in `MainActivity.requestPermissions()` has a chance to complete
- **Impact:** On physical device this was hidden because permission was already granted from previous install. Any fresh install or permission denial = crash
- **Fix needed:** Defer `startForegroundService()` until after `RECORD_AUDIO` permission is confirmed granted. Move the service start into the permission callback or guard it with a permission check.
- **Status:** OPEN

### BUG-002: Log.d calls invisible on Android 14 emulator
- **Severity:** Low (dev tooling only)
- **Found on:** Emulator (Android 14)
- **Repro:** Run app on emulator, check logcat — no DictationService/VoxtralApi/AudioRecorder tags appear
- **Root cause:** Android 14 suppresses `Log.d()` output even for debug builds unless log tags are explicitly set to DEBUG via `setprop`
- **Fix:** Changed all app logging from `Log.d` to `Log.i` (applied 2026-02-23)
- **Status:** FIXED

### BUG-003: onStateChanged dropped transcription text (pre-fix)
- **Severity:** High (feature broken)
- **Found on:** Code review
- **Root cause:** `onStateChanged` callback was `(DictationState) -> Unit`, dropping the `text` parameter that `updateState()` passed. UI card for last transcription never rendered.
- **Fix:** Changed signature to `(DictationState, String?) -> Unit`, ViewModel now updates `lastTranscription` on DONE state
- **Status:** FIXED

### BUG-004: All errors showed "Check API key and try again"
- **Severity:** Medium (misleading UX)
- **Found on:** Code review
- **Root cause:** `VoxtralApi.transcribe()` returned `String?` — all failures were `null` with no error context. UI had hardcoded error text.
- **Fix:** Created `TranscribeResult` sealed class with `Success`/`Error` variants. HTTP codes mapped to specific messages. Added `errorMessage` to `UiState`.
- **Status:** FIXED

### BUG-005: Notification flash on every onStartCommand
- **Severity:** Low (visual glitch)
- **Found on:** Code review
- **Root cause:** `startForeground()` was called on every `onStartCommand`, including when receiving `ACTION_TOGGLE` via PendingIntent. This briefly flashed the IDLE notification before the state update.
- **Fix:** Added `isForegroundStarted` flag, only call `startForeground()` on first invocation.
- **Status:** FIXED

### BUG-006: AudioRecorder.start() had no error handling
- **Severity:** Medium (silent failure)
- **Found on:** Code review
- **Root cause:** If `MediaRecorder.prepare()` or `start()` threw (e.g. mic in use, no permission), the exception was uncaught and the service would crash or enter a bad state.
- **Fix:** Wrapped in try/catch, `start()` now returns `Boolean`. Service shows "Microphone unavailable" error on failure.
- **Status:** FIXED

### BUG-007: Volume double-tap via `adb shell input keyevent` doesn't reach Activity.onKeyDown
- **Severity:** Low (test tooling only)
- **Found on:** Emulator
- **Root cause:** `adb shell input keyevent KEYCODE_VOLUME_DOWN` is intercepted by system `MediaSessionService` before reaching the app's `onKeyDown()`. This is an Android platform behavior, not a bug in our code.
- **Impact:** Can't automate volume-button testing via adb. Must use accessibility service path or physical device.
- **Status:** WON'T FIX (platform limitation)
