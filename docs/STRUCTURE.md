# Privacy Shield Project & Repository Structure

This document details the source tree layout, architecture layers, and module boundaries.

## Directory Tree

```
android-privacy-shield/
├── app/
│   ├── build.gradle.kts                   # Application Gradle build configuration
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # Permissions, Services, Activities, Receivers
│       │   ├── java/com/example/privacyshield/
│       │   │   ├── MainActivity.kt        # Single activity host & navigation bar
│       │   │   ├── data/                  # Persistence & Repositories
│       │   │   │   ├── DatabaseHelper.kt  # SQLite schema, indexes & starter list
│       │   │   │   └── Repositories.kt    # Rule, AppPolicy, Settings, Stats repositories
│       │   │   ├── filtering/             # Filtering & Parsing Core
│       │   │   │   ├── DomainRules.kt     # DomainRuleMatcher, IDN normalization
│       │   │   │   └── HostsParser.kt     # Hosts format file stream parser
│       │   │   ├── network/               # Packet Engine
│       │   │   │   ├── DnsPacket.kt       # IPv4 UDP & DNS parser/synthesizer
│       │   │   │   └── DnsPacketProcessor.kt # Query processor & UdpUpstreamDnsResolver
│       │   │   ├── ui/                    # Presentation Layer
│       │   │   │   ├── screens/           # Compose Screens
│       │   │   │   │   ├── DashboardScreen.kt
│       │   │   │   │   ├── ApplicationsScreen.kt
│       │   │   │   │   ├── FiltersScreen.kt
│       │   │   │   │   ├── PlayerScreen.kt
│       │   │   │   │   └── SettingsScreen.kt
│       │   │   │   ├── viewmodels/        # Lifecycle ViewModels
│       │   │   │   │   ├── DashboardViewModel.kt
│       │   │   │   │   └── FiltersViewModel.kt
│       │   │   │   └── theme/             # Color schemes, typography, Theme.kt
│       │   │   ├── vpn/                   # Android VpnService Subsystem
│       │   │   │   ├── PrivacyShieldVpnService.kt # TUN lifecycle & dispatch loop
│       │   │   │   ├── ProtectionController.kt    # State machine & uptime tracker
│       │   │   │   ├── VpnActionReceiver.kt       # Notification action receiver
│       │   │   │   └── VpnNotificationManager.kt  # Foreground notification builder
│       │   │   └── youtube/               # Audio & Offline Streaming Subsystem
│       │   │       ├── OfflineDownloadManager.kt # Multi-candidate redirect downloader
│       │   │       ├── PlayerNotificationHelper.kt # MediaStyle notification helper
│       │   │       ├── YouTubeAudioService.kt    # Foreground audio player service
│       │   │       ├── YouTubePlayerState.kt     # StateFlows & playback queue
│       │   │       ├── YouTubeStreamResolver.kt  # Piped/Invidious stream resolver
│       │   │       └── YouTubeUrlParser.kt       # YouTube URL parser
│       │   └── res/                       # Drawables, layouts, strings, themes
│       └── test/java/com/example/privacyshield/
│           ├── data/                      # Repository & URL validation tests
│           ├── filtering/                 # Domain rule & matcher unit tests
│           ├── network/                   # DNS packet parser & synthesizer unit tests
│           ├── vpn/                       # Protection controller tests
│           └── youtube/                   # YouTube URL parser tests
├── docs/                                  # Technical specifications & architecture
│   ├── ARCHITECTURE.md
│   ├── CONCURRENCY.md
│   ├── SECURITY.md
│   ├── PRIVACY.md
│   └── STRUCTURE.md
├── build.gradle.kts                       # Root project build file
├── settings.gradle.kts                    # Gradle project settings
└── README.md                              # Main project documentation
```
