# Changelog

All notable changes to Privacy Shield are documented in this file.

## [0.3.9] - 2026-11-15

### Added
- Comprehensive i18n architecture with centralized string resources in `values/strings.xml` and `values-uk/strings.xml`.
- Standard project governance files (`CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `LICENSE`).
- Architecture Decision Records (ADRs) and comprehensive technical specifications in `docs/`.

### Changed
- Refactored UI components (`FiltersScreen`, `PlayerScreen`, `SettingsScreen`) to use Compose `stringResource` without inline language checks.
- Refactored background services and notification helpers (`PlayerNotificationHelper`, `VpnNotificationManager`, `YouTubeAudioService`) to use Android resource lookups.
- Removed all inline and block comments across the codebase for strict adherence to the project's self-documenting code rule.

### Fixed
- Offline audio playback seamlessly resumes when network connection drops without showing network errors.
- Preserved track metadata (`$videoId.meta`) ensuring offline queue items retain titles and authors.
- Offline download redirection logic properly retains headers across cross-host redirects.

## [0.3.8] - 2026-11-14

### Added
- Local offline audio caching for YouTube background audio.
- Custom domain allowlist and blocklist management with CSV import/export.
- On-device SQLite query logging with live metrics.
