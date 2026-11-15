# Contributing to Privacy Shield

Thank you for your interest in contributing to Privacy Shield. This project is built to deliver local, zero-telemetry network security and privacy on Android devices.

## Principles

1. **Zero Telemetry**: The application must never collect, transmit, or log user data to remote servers. All filtering, analytics, and rule evaluation remain 100% on-device.
2. **Minimal Dependencies**: Keep external libraries minimal, audited, and strictly necessary.
3. **No Code Comments Policy**: The project maintains self-documenting code without inline or block comments. All architectural reasoning belongs in `docs/` and ADR records.
4. **Localization First**: All user-facing strings must reside in resource files (`res/values/strings.xml` and `res/values-uk/strings.xml`) rather than hardcoded string literals.

## Development Workflow

1. Fork the repository and create a descriptive feature branch (`feature/your-feature-name` or `fix/issue-description`).
2. Ensure you have Android SDK 35 and Java 17 configured.
3. Make atomic, well-tested commits.
4. Verify tests pass locally:
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```
5. Open a Pull Request with a clear description of your changes, referencing any relevant issues or ADRs.

## Code Standards

- **Kotlin**: Follow official Kotlin coding conventions with Jetpack Compose idioms.
- **Coroutines & Flow**: Use structured concurrency with dedicated lifecycles and appropriate dispatchers (`Dispatchers.IO` for disk/network, `Dispatchers.Default` for packet parsing).
- **Architecture**: Adhere to Unidirectional Data Flow (UDF), separating repository, domain logic, and Compose UI layers.
