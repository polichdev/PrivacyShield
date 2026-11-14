# Android Privacy Shield

This is an early foundation, not an operating VPN. No traffic is intercepted or filtered. The domain matcher is isolated from Android and is not yet connected to packet processing. It applies user allow rules, then user block rules, then subscription rules, and defaults to allow; matching rules of the same source are ordered by rule ID. Invalid domain input is reported separately. Critical compatibility checks are not implemented.

The initial configuration targets API 35 with JDK 17, Android Gradle Plugin 8.9.2 and Gradle 8.11.1. Install Android SDK Platform 35 and Build Tools 35.0.0 and configure the SDK locally. The Gradle Wrapper scripts and JAR are **not yet present**: network access was unavailable during initial setup. Generate them using a trusted Gradle 8.11.1 installation (`gradle wrapper --gradle-version 8.11.1`), then run `./gradlew assembleDebug testDebugUnitTest lintDebug` (or `gradlew.bat` on Windows). No build or device tests have been verified yet.

Next: verify the build, evaluate and document a licensed forwarding engine, and only then implement the consent-based VPN lifecycle and DNS packet handling. Never establish a tunnel that cannot forward permitted traffic.
