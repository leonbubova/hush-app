# Changelog

All notable changes to Hush will be documented in this file.

## [Unreleased]

### Added
- Moonshine streaming on-device transcription with live overlay
- Local on-device transcription via ExecuTorch Whisper (tiny.en, 3 quantizations)
- Multi-provider architecture: Voxtral, OpenAI Whisper, Groq, Local, Moonshine
- Streaming overlay for live transcription text in external apps
- Settings screen with per-provider configuration
- Usage dashboard with streak tracking, weekly charts, and cost estimates
- Transcription history with tap-to-copy
- SHA256 integrity verification for model downloads
- Accessibility service for global volume key hotkey + auto-inject
- Debug build variant with separate app identity (side-by-side install)
- Emulator audio injection scripts for testing
- 157 unit tests, 42 instrumented tests

### Fixed
- AudioConverter using input format instead of codec output format
- Silent crash on longer recordings (MAX_DECODE_TOKENS 128 -> 448)
- 16KB page alignment warning on Android 15
- Log.d invisible on Android 14 (switched to Log.i)

### Security
- All credentials stored in EncryptedSharedPreferences (AES-256-GCM)
- No secrets in codebase or git history
- BSL 1.1 license for source-available distribution
