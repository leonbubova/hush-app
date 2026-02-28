# Contributing to Hush

Thanks for your interest in contributing to Hush!

## License

Hush is licensed under the [Business Source License 1.1](LICENSE). By contributing, you agree that your contributions will be licensed under the same terms.

## Getting Started

1. Fork the repository
2. Clone your fork
3. Set up the development environment:

```bash
# Requirements: JDK 17, Android SDK
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

# Build
./gradlew assembleDebug

# Run tests
./gradlew testDebugUnitTest
```

4. Create a branch for your changes
5. Make your changes
6. Run tests and verify they pass
7. Submit a pull request

## Development Setup

See the [README](README.md) for detailed build instructions, including debug vs release builds and emulator setup.

### Local Configuration

Copy the example and fill in your values:
```bash
cp local.properties.example local.properties
```

## Pull Requests

- Keep PRs focused — one feature or fix per PR
- Include tests for new functionality
- Update documentation if behavior changes
- Ensure all existing tests pass

## Bug Reports

Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md) and include:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output if applicable

## Security Issues

**Do NOT open public issues for security vulnerabilities.** See [SECURITY.md](SECURITY.md) for responsible disclosure instructions.

## Code Style

- Kotlin + Jetpack Compose, no XML layouts
- Follow existing patterns in the codebase
- Keep it simple — avoid over-engineering
