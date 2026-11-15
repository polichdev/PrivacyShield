# ADR 0004: Centralized i18n via Android Resource Qualifiers

## Status
Accepted

## Context
Initial iterations contained fragmented string literals and conditional language branching (`if (ukrainian) ... else ...`) inside Compose UI screens and background services. This prevented maintainability, testability, and standard translation workflows.

## Decision
1. **Eliminate Hardcoded UI Strings**: All user-visible strings, dialog text, statuses, notification actions, and error messages are centralized in:
   - `res/values/strings.xml` (Default / English)
   - `res/values-uk/strings.xml` (Ukrainian)
2. **Standard Compose & Context Invocations**:
   - Compose UI layers access strings exclusively via `stringResource(R.string.<id>)`.
   - Android Services, Receivers, and Notification helpers access strings via `context.getString(R.string.<id>)`.
3. **Locale Configuration**:
   - `MainActivity.attachBaseContext` loads the user's selected language preference from storage into `Configuration.setLocale()`, ensuring Android's native resource resolution automatically serves the appropriate language table across both Activity and Services.

## Consequences
- **Advantages**: Compile-time safety for string resources, clean decoupling of UI presentation from localization logic, standard Android tooling support.
- **Trade-offs**: None.
