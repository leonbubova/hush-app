# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Hush, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

Instead, please email: **security@hush-app.dev**

Include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

## Response Timeline

- **Acknowledgment:** Within 48 hours
- **Initial assessment:** Within 1 week
- **Fix or mitigation:** Depends on severity, targeting 30 days for critical issues

## Scope

The following are in scope:
- Android app source code
- API key handling and credential storage
- Accessibility service security
- Network communication security
- Data privacy issues

The following are out of scope:
- Third-party API provider security (OpenAI, Mistral, Groq)
- Android OS vulnerabilities
- Physical device access attacks

## Security Design

- API keys are stored using Android EncryptedSharedPreferences (AES-256-GCM)
- No credentials are bundled in the APK
- Audio files are deleted immediately after transcription
- On-device transcription (Local/Moonshine) sends no data externally
- All cloud API calls use HTTPS
