# Android Privacy Shield — Architecture

**Version:** 0.1.0  
**Status:** Initial Architecture Specification  
**Platform:** Android  
**Language:** Kotlin  
**UI:** Jetpack Compose + Material 3  
**IDE:** IntelliJ IDEA  
**Build System:** Gradle Kotlin DSL  
**Primary Purpose:** Local network-level ad and tracker filtering

---

## 1. Project Overview

Android Privacy Shield is a native Android application designed to provide user-controlled, on-device network filtering for installed applications.

The application uses Android's `VpnService` API to establish a local virtual network interface and selectively filter supported network traffic.

The application must prioritize privacy, transparency, performance, and reliability.

### Primary Features

- Local network filtering.
- DNS-based ad and tracker blocking.
- Per-application filtering.
- Custom allowlists and blocklists.
- Filtering statistics.
- Local rule management.
- Background protection.
- Optional blocklist updates.
- Automatic recovery from service interruptions.
- User-controlled protection settings.

### Product Principles

1. Privacy first.
2. Local processing by default.
3. No mandatory user accounts.
4. No remote filtering infrastructure required.
5. No root access required.
6. Transparent network operations.
7. Minimal battery consumption.
8. Modular and testable architecture.
9. Graceful failure and recovery.
10. No false guarantees about blocking all advertisements.

### Scope and Limitations

DNS filtering cannot reliably remove advertisements served through the same domains as legitimate content.

In particular, removing all video advertisements from the official YouTube application or all audio advertisements from the official Spotify application is not a guaranteed capability.

Do not implement HTTPS interception, DRM circumvention, modification of third-party applications, or certificate injection.

---

## 2. Technology Stack

### Language and Runtime

- Kotlin
- Kotlin Coroutines
- Kotlin Flow
- Android SDK

### User Interface

- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel
- StateFlow

### Dependency Injection

- Hilt, if the selected Android Gradle Plugin and Kotlin versions are compatible.

### Data Storage

- Room — structured local data.
- DataStore — application preferences.
- SQLite — through Room.

### Networking

- Android `VpnService`
- Local DNS filtering
- Protected upstream sockets
- Pluggable traffic forwarding engine
- Optional encrypted upstream DNS transport

### Background Operations

- Foreground Service for VPN operation.
- WorkManager for eligible periodic maintenance and rule updates.
- Coroutines for asynchronous operations.

### Testing

- JUnit
- MockK
- Turbine
- Android instrumentation tests
- Compose UI tests

### Build and Distribution

- Gradle Wrapper
- Android SDK Command-line Tools
- ADB
- Debug APK
- Signed release APK

Android Studio must not be required to compile or install the application.

---

## 3. High-Level Architecture

```text
┌─────────────────────────────────────┐
│          Android Application        │
│                                     │
│          Jetpack Compose UI         │
│                                     │
│ Dashboard / Apps / Rules / Stats    │
└──────────────────┬──────────────────┘
                   │
              ViewModels
                   │
┌──────────────────▼──────────────────┐
│           Domain Layer              │
│                                     │
│ Protection Controller               │
│ Rule Manager                        │
│ Application Policy Manager          │
│ Statistics Manager                  │
└──────────────────┬──────────────────┘
                   │
┌──────────────────▼──────────────────┐
│          Data / Infrastructure      │
│                                     │
│ Room / DataStore / Repositories     │
│ Blocklist Storage                   │
│ Android System Adapters             │
└──────────────────┬──────────────────┘
                   │
┌──────────────────▼──────────────────┐
│            VPN Engine               │
│                                     │
│ VpnService                          │
│ TUN Interface                       │
│ Traffic Forwarding                  │
│ DNS Interception                    │
│ DNS Rule Evaluation                 │
└───────────┬─────────────┬───────────┘
            │             │
       Blocked DNS    Allowed Traffic
            │             │
       Local Reply    Protected Upstream
                          │
                       Internet
```

### Architecture Rules

- UI must not contain packet-processing logic.
- Domain services must not depend directly on Compose.
- VPN infrastructure must not depend on UI components.
- Database access must occur through repositories.
- The network forwarding engine must expose stable interfaces.
- Components must be independently testable.
- Business logic must remain separate from Android-specific APIs wherever practical.

---

## 4. Project Structure

```text
privacy-shield/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/privacyshield/
│   │   │   ├── MainActivity.kt
│   │   │   ├── PrivacyShieldApplication.kt
│   │   │   ├── di/
│   │   │   └── navigation/
│   │   └── res/
│   └── build.gradle.kts
│
├── core/
│   ├── common/
│   ├── model/
│   ├── data/
│   ├── database/
│   ├── preferences/
│   └── designsystem/
│
├── domain/
│   ├── protection/
│   ├── filtering/
│   ├── applications/
│   └── statistics/
│
├── vpn/
│   ├── service/
│   ├── tunnel/
│   ├── forwarding/
│   ├── dns/
│   └── monitoring/
│
├── features/
│   ├── dashboard/
│   ├── applications/
│   ├── blocklists/
│   ├── statistics/
│   ├── settings/
│   └── onboarding/
│
├── docs/
├── gradle/
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
├── ARCHITECTURE.md
├── RULES.md
└── AGENTS.md
```

The initial implementation may combine small packages into fewer Gradle modules. Do not create unnecessary module complexity before it provides a measurable benefit.

---

## 5. VPN Engine

### 5.1 Android VpnService

The VPN engine is responsible for establishing and maintaining the local network interface.

Required operations:

1. Request VPN authorization using `VpnService.prepare()`.
2. Start the VPN foreground service.
3. Configure the virtual interface.
4. Establish the VPN interface.
5. Process packets from the TUN file descriptor.
6. Forward permitted traffic.
7. Apply DNS filtering rules.
8. Handle network changes.
9. Stop and release resources correctly.

The VPN must never activate silently without the required system authorization.

### 5.2 Traffic Forwarding

A working VPN must preserve permitted internet connectivity.

`VpnService` creates the virtual interface but does not implement packet forwarding automatically.

The forwarding component must handle the selected traffic families and protocols correctly.

Initial design:

```text
Application Traffic
        |
        v
     TUN Interface
        |
        v
    Packet Router
        |
        +---- DNS request ----> DNS Filter
        |                          |
        |                    Allow / Block
        |                          |
        |                    Local DNS Reply
        |
        +---- Other Traffic ----> Forwarding Engine
                                      |
                                Protected Socket
                                      |
                                   Internet
```

Important requirements:

- Prevent VPN routing loops using protected sockets.
- Preserve DNS request identifiers and valid responses.
- Correctly handle timeouts and cancellation.
- Define IPv4 and IPv6 support explicitly.
- Avoid silently dropping unsupported traffic.
- Test TCP, UDP, and QUIC behavior as applicable.
- Do not advertise IPv6 support before validating it.
- Avoid introducing external proxy dependencies without review.

### 5.3 VPN Engine Strategy

For the first working implementation, evaluate a maintained open-source VPN or tun-to-socket forwarding engine.

Selection criteria:

- Android compatibility.
- License compatibility.
- Active maintenance.
- IPv4/IPv6 capabilities.
- TCP and UDP forwarding.
- Stability.
- Integration complexity.
- Performance.
- Security review.

Prefer a proven forwarding implementation over developing a complete TCP/IP stack from scratch.

The UI and domain layers remain Kotlin even if a reviewed native forwarding engine is necessary.

---

## 6. DNS Filtering Engine

### Responsibilities

- Extract DNS requests from supported traffic.
- Normalize requested domain names.
- Match domains against filtering rules.
- Allow or block requests.
- Cache safe filtering decisions.
- Record minimal local statistics.
- Forward allowed queries to the configured resolver.

### Rule Types

- Exact domain rules.
- Subdomain matching.
- Allowlist overrides.
- Denylist rules.
- User-defined rules.
- Imported host-format blocklists.

### Filtering Decision Model

```kotlin
sealed interface FilterDecision {
    data object Allow : FilterDecision

    data class Block(
        val ruleId: String
    ) : FilterDecision
}
```

### Evaluation Order

1. Critical application and system compatibility checks.
2. Explicit user allowlist.
3. Explicit user blocklist.
4. Enabled subscription blocklists.
5. Default allow.

The rule precedence must be documented, deterministic, and covered by tests.

### DNS Security and Limitations

- Do not intercept TLS content.
- Do not install user certificates.
- Do not claim to inspect encrypted application payloads.
- Recognize that encrypted DNS and hardcoded resolvers may bypass traditional DNS filtering.
- Do not report blocked requests as blocked advertisements unless that classification is supported.
- Preserve legitimate network functionality.

---

## 7. Application Filtering

Users must be able to choose which applications are included in VPN filtering.

Android supports application-specific VPN configuration through `VpnService.Builder`.

### Features

- Installed application list.
- Application name and icon.
- Protection toggle.
- Global protection policy.
- Application exceptions.
- Per-app statistics where reliably attributable.

### Policy Model

```kotlin
data class AppFilterPolicy(
    val packageName: String,
    val enabled: Boolean
)
```

### Important Constraints

Per-app filtering configuration applies to VPN routing.

Changes to the allowed/disallowed application set may require rebuilding the VPN interface.

VPN-level application selection is not equivalent to automatically identifying the originating application for every DNS packet.

Do not display fabricated per-app statistics.

Only show application-level counts when there is a reliable attribution mechanism.

---

## 8. Blocklist Manager

The Blocklist Manager handles filtering rule subscriptions.

### Features

- Bundled starter blocklist.
- User-provided blocklist URLs.
- Manual refresh.
- Optional scheduled refresh.
- Rule validation.
- Duplicate elimination.
- Rule compilation.
- Blocklist enable/disable.
- Import and export.

### Update Pipeline

```text
Download
   |
   v
Validate Source
   |
   v
Parse Rules
   |
   v
Normalize / Deduplicate
   |
   v
Compile Rule Index
   |
   v
Atomic Replacement
```

Never replace the active ruleset with an invalid or incomplete download.

Blocklist sources and update frequency must be transparent to the user.

---

## 9. Statistics System

Statistics must remain on the device unless explicitly exported.

### Initial Metrics

- DNS requests evaluated.
- DNS requests blocked.
- DNS requests allowed.
- Protection uptime.
- Active rule count.
- Last successful ruleset update.
- VPN connection status.

### Future Metrics

- Hourly activity charts.
- Daily protection trends.
- Per-application DNS statistics, where supportable.
- Recently blocked domains.
- Filtering performance.

Statistics are based on network activity and must not be described as guaranteed counts of advertisements removed.

---

## 10. Data Storage

### Room Database

Initial entities:

```text
BlocklistEntity
CustomRuleEntity
AppPolicyEntity
FilterEventEntity
RulesetVersionEntity
```

### DataStore

Store:

```text
protection_enabled
selected_applications
dns_provider
theme
language
statistics_retention
automatic_rules_updates
```

### Data Management

- Use versioned database migrations.
- Use parameterized database operations.
- Allow clearing statistics.
- Allow exporting user rules.
- Avoid storing complete browsing histories by default.
- Use bounded event retention.
- Keep sensitive diagnostic logs disabled by default.

---

## 11. User Interface

Use Jetpack Compose and Material 3.

### Screens

**Dashboard**

- Protection status.
- Connect/disconnect action.
- Basic statistics.
- VPN permission status.
- Latest protection activity.

**Applications**

- Installed application list.
- Search.
- Protection selection.
- Application-specific settings.

**Filters**

- Enabled blocklists.
- Custom allowlist.
- Custom blocklist.
- Ruleset refresh.
- Rule counts.

**Statistics**

- Request counts.
- Trends.
- Recent filtering events.
- Data reset controls.

**Settings**

- DNS provider.
- Startup behavior.
- Notifications.
- Theme.
- Language.
- Data retention.
- Privacy settings.

### Visual Direction

- Dark-first design.
- Modern Material 3 components.
- Minimal interface.
- Clear typography.
- Subtle colors.
- Consistent spacing.
- Accessible controls.
- Status-aware visuals.
- Support Ukrainian and English.

The design must not imply a successful connection before Android confirms that protection is active.

---

## 12. Background Processing

The VPN runs as a foreground service with the appropriate Android service configuration.

Requirements:

- Persistent service notification when required.
- Clear VPN connection state.
- Recovery after network changes.
- Graceful shutdown.
- Resource cleanup.
- No busy polling.
- No unnecessary background tasks.

Periodic blocklist updates should use WorkManager when appropriate.

Do not rely on WorkManager to maintain a continuously active VPN tunnel.

---

## 13. Security and Privacy

### Security Requirements

- No root permissions.
- No TLS interception.
- No arbitrary traffic rewriting.
- No remote code execution.
- No hidden telemetry.
- No collection of credentials.
- No unapproved traffic redirection.
- No automatic modification of other applications.
- No downloading and executing arbitrary code.

### Privacy Requirements

- Local filtering by default.
- Clear VPN disclosure.
- Explicit user authorization.
- User-controlled DNS resolvers.
- Minimal data retention.
- No mandatory cloud account.
- No transmission of browsing history to project-owned servers.

### Android Platform Compliance

The project must comply with Android VPN behavior and applicable distribution policies.

If distributed through Google Play, review and satisfy the current VpnService declaration, privacy disclosure, data safety, and foreground-service requirements.

---

## 14. Build Environment

Development should work without Android Studio.

### Required Tools

- IntelliJ IDEA
- Compatible JDK
- Android SDK Command-line Tools
- Android SDK Platform
- Android Build Tools
- Android Platform Tools / ADB
- Gradle Wrapper

The exact Kotlin, Android Gradle Plugin, Compose, SDK, and JDK versions must be pinned together in the project version catalog after confirming compatibility.

### Build Commands

```powershell
.\gradlew.bat assembleDebug

.\gradlew.bat testDebugUnitTest

.\gradlew.bat lintDebug

adb devices

adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Development Requirements

- Buildable from the command line.
- No required Android Studio installation.
- Reproducible dependencies.
- Versioned Gradle Wrapper.
- Debug and release build configurations.
- Testing on physical Android devices.

---

## 15. Testing Strategy

### Unit Tests

- Domain normalization.
- Rule matching.
- Allowlist precedence.
- Blocklist parsing.
- Filtering decisions.
- Statistics aggregation.
- Configuration validation.

### Integration Tests

- VPN service lifecycle.
- DNS request handling.
- Allowed traffic forwarding.
- Rule updates.
- Room migrations.
- Per-application VPN configuration.

### Device Tests

- Wi-Fi connectivity.
- Mobile data connectivity.
- Network switching.
- Background operation.
- Phone sleep/wake.
- VPN disconnect/reconnect.
- Android permission revocation.
- Application termination.
- IPv4 and IPv6.
- Encrypted DNS limitations.
- Conflict with another VPN.

### Test Environment

Use controlled DNS domains and test applications to verify filtering correctness.

Do not depend on advertisements from third-party commercial services for automated test success.

---

## 16. Development Roadmap

### Phase 1 — Foundation

- Kotlin Android project.
- Compose UI.
- Dependency configuration.
- Navigation.
- Theme.
- Domain interfaces.
- Repositories.
- Unit tests.

### Phase 2 — VPN Proof of Concept

- VPN consent.
- VpnService lifecycle.
- Foreground notification.
- Virtual network interface.
- Working traffic forwarding.
- Verified device connectivity.
- Clean disconnect behavior.

### Phase 3 — DNS Filtering MVP

- DNS interception.
- Rule evaluation.
- Test-domain blocking.
- DNS forwarding.
- Basic statistics.
- Local blocklist.

### Phase 4 — Application Control

- Installed application discovery.
- Per-app VPN selection.
- Policy persistence.
- Configuration changes.
- Connection rebuild when necessary.

### Phase 5 — Advanced Filtering

- Multiple blocklists.
- Custom rules.
- Rule updates.
- Allowlist.
- Detailed statistics.

### Phase 6 — Release Preparation

- Performance profiling.
- Battery testing.
- Privacy audit.
- Accessibility.
- Error recovery.
- Release signing.
- Distribution compliance review.

---

## 17. MVP Definition of Done

The first MVP is complete when:

1. The application builds without Android Studio.
2. The APK installs on a physical Android device.
3. The dashboard displays actual protection state.
4. Android VPN permission is requested correctly.
5. The VPN service starts and stops reliably.
6. Permitted network traffic continues working.
7. Controlled test-domain DNS queries are blocked correctly.
8. Allowed DNS queries resolve correctly.
9. User allowlist rules override applicable block rules.
10. At least one application-selection workflow works.
11. Filtering statistics reflect actual observed activity.
12. No user credentials or payloads are collected.
13. The app handles VPN revocation and network changes.
14. Disconnection releases networking resources.
15. Unit and integration tests pass.
16. The supported device workflow is verified manually.

Do not claim MVP completion based only on a working UI or a successfully established VPN interface.

---

## 18. Future Vision

Potential future capabilities include:

- Local network privacy dashboard.
- Application firewall controls.
- Advanced domain-based policies.
- DNS-over-HTTPS or DNS-over-TLS.
- More granular filtering analytics.
- Battery-aware rule evaluation.
- Importable privacy profiles.
- Optional custom network filtering modules.

All future capabilities must preserve user consent, platform compatibility, and secure network behavior.
