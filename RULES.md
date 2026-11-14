# Android Privacy Shield — AI Agent Development Rules

**Version:** 1.0  
**Priority:** Mandatory Project Engineering Guidelines

---

## 1. Agent Responsibilities

You are developing a production-oriented native Android application using Kotlin and Jetpack Compose.

Your responsibility is to implement reliable, secure, maintainable, and testable functionality according to `ARCHITECTURE.md`.

The current project priority is the local VPN and DNS filtering MVP.

### Mandatory Workflow

Before implementation:

1. Read `ARCHITECTURE.md`.
2. Read `RULES.md`.
3. Inspect relevant project files.
4. Check existing implementations.
5. Identify dependencies and affected components.
6. Plan nontrivial modifications.

During implementation:

1. Follow established architecture.
2. Make focused changes.
3. Reuse existing services.
4. Add appropriate tests.
5. Preserve unrelated functionality.
6. Avoid unnecessary dependencies.

After implementation:

1. Inspect `git diff`.
2. Execute relevant Gradle checks.
3. Review potential security consequences.
4. Document significant changes.
5. Report unresolved problems accurately.

Never claim that a feature works unless it has been appropriately verified.

---

## 2. Technology Rules

Approved stack:

- Kotlin.
- Jetpack Compose.
- Material 3.
- Kotlin Coroutines and Flow.
- Android VpnService.
- Room.
- DataStore.
- Gradle Kotlin DSL.
- JUnit and appropriate Android testing libraries.

### Restrictions

- Do not replace Kotlin with Java as the primary language.
- Do not introduce React, Flutter, Electron, or Capacitor.
- Do not introduce a web-based UI.
- Do not require Android Studio.
- Do not add a remote backend for core filtering.
- Do not introduce mandatory cloud services.
- Do not add root-based dependencies.
- Do not install packages without checking licenses and compatibility.

A native forwarding component may be used if its necessity, security, and licensing are documented.

---

## 3. Kotlin Coding Standards

Use idiomatic Kotlin.

### Requirements

- Prefer immutable data.
- Use data classes for domain models.
- Use sealed interfaces for finite states.
- Use suspend functions for asynchronous operations.
- Use Flow for observable state where appropriate.
- Use structured concurrency.
- Avoid blocking the main thread.
- Avoid uncontrolled GlobalScope usage.
- Handle cancellation correctly.
- Avoid unsafe null assertions.
- Keep public interfaces explicit.
- Prefer constructor injection.

### Forbidden Practices

- Silent exception swallowing.
- Global mutable state without synchronization.
- Unbounded coroutines.
- Blocking operations on the UI thread.
- Hardcoded production credentials.
- Overly large classes with unrelated responsibilities.

Do not introduce abstractions without a concrete reason.

---

## 4. Architecture Rules

Follow this dependency direction:

```text
Compose UI
    ↓
ViewModel
    ↓
Domain Use Cases
    ↓
Repository Interfaces
    ↓
Data / Android Infrastructure
```

### Rules

- ViewModels must not parse network packets.
- UI must not directly operate the TUN interface.
- Filtering rules must not depend on Compose.
- Domain logic should be testable without Android.
- Database implementation details must not leak into UI.
- VPN operations must be encapsulated behind interfaces.
- Shared business logic must not be duplicated.

Do not introduce dependency cycles.

---

## 5. VPN Service Rules

VpnService is a security-sensitive component.

### Mandatory Requirements

1. Request system VPN authorization.
2. Respect user consent.
3. Use correct Android service declarations.
4. Follow foreground-service requirements.
5. Handle service shutdown.
6. Handle VPN permission revocation.
7. Close tunnel descriptors safely.
8. Prevent network routing loops.
9. Clean up active resources.
10. Preserve permitted network connectivity.

### Critical Restrictions

- Never assume `Builder.establish()` means filtering works.
- Never claim successful protection before verifying active service state.
- Never drop permitted traffic intentionally as a shortcut.
- Never bypass the Android VPN consent dialog.
- Never attempt to start a hidden VPN.
- Never intercept TLS by installing certificates.
- Never collect decrypted application payloads.

If packet forwarding is incomplete, report the feature as incomplete.

---

## 6. DNS Filtering Rules

The DNS filter must behave deterministically.

### Requirements

- Normalize domain names.
- Validate input.
- Match rules consistently.
- Implement documented precedence.
- Handle malformed packets safely.
- Preserve allowed DNS functionality.
- Return valid responses for blocked domains according to the chosen policy.
- Avoid unbounded caches.
- Support controlled cancellation.

### Restrictions

- Do not block all DNS traffic.
- Do not assume every blocked request represents an advertisement.
- Do not silently rewrite unrelated DNS records.
- Do not report success when packets were never inspected.
- Do not claim complete protection against encrypted DNS.

DNS filtering must have isolated unit tests.

---

## 7. Traffic Forwarding Rules

The forwarding engine must prioritize network correctness.

### Requirements

- Preserve valid connections.
- Avoid TUN-to-upstream routing loops.
- Handle network changes.
- Avoid file descriptor leaks.
- Bound memory usage.
- Handle socket failures.
- Provide explicit protocol support.
- Gracefully reject unsupported configurations.
- Maintain safe shutdown behavior.

### Dependency Review

Before integrating any forwarding library:

1. Review its source and maintenance status.
2. Review its license.
3. Check Android architecture support.
4. Verify TCP/UDP compatibility.
5. Evaluate IPv6 handling.
6. Assess security risks.
7. Document integration decisions.

Do not implement a custom full TCP/IP stack unless explicitly justified and approved.

---

## 8. Application Filtering Rules

- Use Android-supported per-app VPN configuration.
- Validate installed package identifiers.
- Handle uninstalled applications.
- Preserve user preferences.
- Rebuild the VPN configuration when required.
- Handle application discovery limitations.
- Do not request unrestricted package visibility unless necessary and justified.
- Do not invent per-app request attribution.

Application filters and DNS domain rules must be treated as separate concepts.

---

## 9. Privacy Rules

The application must be privacy-first.

### Mandatory

- No mandatory user registration.
- No hidden telemetry.
- No collection of passwords.
- No collection of authentication cookies.
- No sale or upload of network history.
- No silent external requests to project-owned services.
- Explicit consent for relevant data collection.
- Local statistics by default.
- User-controlled data deletion.

### Logs

Never log:

- Authentication tokens.
- Cookies.
- Complete HTTP payloads.
- Sensitive personal information.
- Unnecessary network metadata.

Debug logs must be restricted and removable.

---

## 10. Security Rules

Security has priority over convenience.

### Prohibited

- HTTPS man-in-the-middle interception.
- Certificate authority injection.
- DRM bypass mechanisms.
- Modification of third-party APKs.
- Arbitrary downloaded code execution.
- Unapproved VPN traffic redirection.
- Background surveillance.
- Hidden traffic collection.
- Unnecessary elevated privileges.

### Third-Party Applications

Do not build application-specific bypasses that depend on modifying protected clients or breaking content protection.

Support must be described accurately.

Do not promise to eliminate all Spotify or YouTube advertisements.

The core product is a transparent local network privacy filter.

---

## 11. Jetpack Compose Rules

### Requirements

- Use unidirectional data flow.
- Keep UI state in appropriate ViewModels.
- Use StateFlow where suitable.
- Reuse common components.
- Support loading and error states.
- Use Material 3 theming.
- Respect accessibility requirements.
- Support varying screen sizes.
- Prevent unnecessary recompositions.

### Design Principles

- Dark-first interface.
- Minimalist style.
- Consistent spacing.
- Subtle accents.
- Clear navigation.
- Readable typography.
- Accessible contrast.
- Consistent iconography.

### Forbidden

- Fake statistics.
- Fake VPN connection states.
- Fake download progress.
- Nonfunctional buttons.
- Mock data presented as production data.

Mock data is allowed only in clearly isolated previews and tests.

---

## 12. State Management Rules

All important system states must be explicit.

Example:

```kotlin
sealed interface ProtectionState {
    data object Disconnected : ProtectionState
    data object Connecting : ProtectionState
    data object Connected : ProtectionState
    data object Disconnecting : ProtectionState

    data class Error(
        val message: String
    ) : ProtectionState
}
```

Do not rely on a single UI Boolean to represent the complete VPN lifecycle.

The displayed connection state must reflect actual backend conditions.

---

## 13. Database Rules

- Use Room for structured data.
- Use DataStore for preferences.
- Apply database migrations.
- Use transactions where necessary.
- Avoid unbounded history tables.
- Do not store raw network payloads.
- Keep schema ownership clear.
- Avoid unnecessary personal data.
- Include export and delete functionality where relevant.

Do not delete user rules during migrations.

---

## 14. Performance Rules

The VPN must remain efficient during background operation.

### Requirements

- Avoid constant busy loops.
- Avoid blocking the main thread.
- Bound queues and caches.
- Avoid repetitive database access for every packet.
- Compile rules into efficient in-memory structures.
- Use asynchronous I/O where appropriate.
- Minimize allocations in high-frequency packet handling.
- Measure CPU, RAM, and battery usage.
- Clean up unused resources.

Do not optimize without measurements when correctness is at risk.

---

## 15. Testing Requirements

### Unit Tests

Required for:

- Domain matching.
- Rule precedence.
- DNS normalization.
- Blocklist parsing.
- Filtering decisions.
- State transitions.
- Statistics aggregation.

### Integration Tests

Required for:

- VPN lifecycle.
- DNS processing.
- Forwarding behavior.
- App selection.
- Blocklist updates.
- Database operations.

### Manual Device Testing

Verify:

- Wi-Fi connectivity.
- Mobile data connectivity.
- VPN connection/disconnection.
- Internet availability.
- DNS filtering.
- Screen-off behavior.
- VPN permission revocation.
- Network switching.
- Application filtering.

Never report a device-only test as passed unless it ran on a real device or an appropriate emulator.

---

## 16. Build Rules

The project must support IntelliJ IDEA and command-line Gradle builds.

### Required Commands

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

For connected Android instrumentation tests, use the applicable `connectedAndroidTest` task.

### Requirements

- Use Gradle Wrapper.
- Pin dependency versions.
- Use a compatible Android SDK.
- Keep builds reproducible.
- Never commit local SDK paths.
- Never commit signing keys.
- Never commit production secrets.
- Do not require Android Studio-specific project generation.

All required project configuration must be stored in the repository, except machine-specific SDK and credential settings.

---

## 17. Git Rules

- Never push directly to `main`.
- Use feature branches.
- Keep commits focused.
- Review diffs before committing.
- Do not commit generated binaries unnecessarily.
- Do not commit credentials.
- Do not delete unrelated files.
- Do not overwrite user changes without approval.

Critical branch protections must be enforced through Git hosting rules or blocking hooks, not merely through this document.

---

## 18. Agent Execution Rules

When given a development task:

1. Understand the requested behavior.
2. Read relevant architecture.
3. Inspect current implementation.
4. Identify the smallest safe change.
5. Implement actual functionality.
6. Write tests.
7. Run applicable checks.
8. Review the diff.
9. Report results.

### Agent Must Not

- Implement unrelated features.
- Rewrite architecture without authorization.
- Introduce unnecessary dependencies.
- Hide build failures.
- Delete tests to bypass failures.
- Claim a feature is complete without validation.
- Generate placeholder implementations and mark them done.
- Ignore network safety problems.

### Required Final Report

Every nontrivial implementation must report:

- What was implemented.
- Files modified.
- Tests executed.
- Tests passed or failed.
- Known limitations.
- Remaining work.

---

## 19. Development Priority

Always follow this priority:

1. Network correctness.
2. User security.
3. Privacy.
4. Reliability.
5. Architecture.
6. Testability.
7. Performance.
8. User experience.
9. Additional features.

A visually attractive app that breaks normal internet connectivity is not an acceptable MVP.

---

## 20. Definition of Done

A development task is complete only when:

- The requested functionality works.
- Architecture boundaries are respected.
- Security requirements are satisfied.
- Relevant automated tests pass.
- Relevant build checks pass.
- Actual device behavior is tested where required.
- Failure and cleanup paths are handled.
- No unrelated functionality was broken.
- Documentation reflects implemented behavior.

If any condition remains unverified, clearly report it.

**Core Principle: Build real, measurable, reliable protection rather than simulated functionality.**
