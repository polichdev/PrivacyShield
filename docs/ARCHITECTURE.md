# Privacy Shield System Architecture

This document describes the high-level architecture, module topology, and data flow of Privacy Shield.

## Macro Topology

```mermaid
flowchart TD
    subgraph UI["UI & Presentation Layer (Jetpack Compose)"]
        Dashboard["DashboardScreen<br/>(Metrics & Pulse Hero)"]
        Filters["FiltersScreen<br/>(Blocklists & Custom Rules)"]
        Player["PlayerScreen<br/>(Playback Controls & Queue)"]
        Apps["ApplicationsScreen<br/>(Per-App VPN Routing)"]
        Settings["SettingsScreen<br/>(Upstream DNS, Theme, Lang)"]
    end

    subgraph VM["State & ViewModels (Lifecycle-Aware)"]
        DVM["DashboardViewModel"]
        FVM["FiltersViewModel"]
        YPS["YouTubePlayerState<br/>(StateFlows & Pruned Cache)"]
    end

    subgraph Data["Domain & Persistence (SQLite & Repositories)"]
        RR["RuleRepository<br/>(Matcher compiler & rules_version)"]
        APR["AppPolicyRepository"]
        SR["SettingsRepository"]
        STR["StatisticsRepository"]
        ODM["OfflineDownloadManager<br/>(Direct MP4/M4A & Meta Storage)"]
        DB[(privacy_shield.db<br/>WAL Mode SQLite)]
    end

    subgraph VPN["Network Engine & VPN Subsystem"]
        Service["PrivacyShieldVpnService<br/>(VpnService, Tun0 Interface)"]
        Worker["Tun0 Read Loop<br/>(Thread Pool of 16 Workers)"]
        Parser["DnsPacket<br/>(IPv4 UDP Header & Query Parser)"]
        Matcher["DomainRuleMatcher<br/>(O(1) Exact & Wildcard Tiers)"]
        Resolver["UdpUpstreamDnsResolver<br/>(ThreadLocal Sockets, Protect API)"]
        Synthesizer["DnsPacketSynthesizer<br/>(IPv4 Checksum & 0.0.0.0 Block / Upstream Wrap)"]
    end

    subgraph AudioEngine["Audio & Media Subsystem"]
        YAS["YouTubeAudioService<br/>(Foreground MediaService)"]
        Exo["ExoPlayer<br/>(Media3 + CacheDataSource)"]
        Session["MediaSession<br/>(System Media Notification)"]
        YSR["YouTubeStreamResolver<br/>(Adaptive Formats & Fallback)"]
    end

    UI --> VM
    VM --> Data
    Data --> DB
    Service --> Worker
    Worker --> Parser
    Parser --> Matcher
    Matcher --> Synthesizer
    Matcher --> Resolver
    Resolver --> Synthesizer
    Synthesizer --> Worker
    Player --> YPS
    Player --> YAS
    YAS --> Exo
    YAS --> Session
    YAS --> YSR
    YAS --> ODM
```

## Core Subsystems

### 1. High-Performance Local DNS VPN Engine
- **Address & Routing**: Privacy Shield configures a virtual TUN interface with a 32-bit subnet mask (`10.0.0.2/32`), setting the DNS server to `10.0.0.1` and routing strictly `10.0.0.1/32`.
- **Zero Traffic Overhead**: Non-DNS IP packets (TCP web browsing, video streaming, game traffic) are never diverted into the VPN tunnel. Only DNS UDP packets directed to `10.0.0.1:53` are ingested.
- **Worker Pipeline**: A dedicated reader thread continuously reads from the `ParcelFileDescriptor`. Packets are offloaded to an active 16-thread fixed executor pool.
- **Checksum & Validation**: Validates IPv4 headers and UDP lengths. The parser decodes DNS transaction IDs and query domain labels, normalizing them into ASCII IDN format.
- **Filter Evaluation**: The `DomainRuleMatcher` checks user allow rules, user block rules, and subscription lists in order of precedence using exact hash maps and recursive parent-domain lookups.
- **Blocked Queries**: Blocked queries receive a synthesized response mapping the domain to `0.0.0.0` with `ttl = 300` and an updated IPv4/UDP checksum.
- **Allowed Queries**: Allowed queries query upstream DNS (Cloudflare, Quad9, Google, or custom) using `UdpUpstreamDnsResolver` with `VpnService.protect(socket)` to prevent routing recursion.

### 2. Audio & Offline Download Engine
- **Background Playback**: Powered by AndroidX Media3 `ExoPlayer` wrapped in a foreground service (`YouTubeAudioService`) with interactive `MediaSession` controls.
- **Streaming & Resolution**: Resolves audio streams via `YouTubeStreamResolver` with fallback pipelines and prioritization of original and Ukrainian audio tracks.
- **Offline Storage**: `OfflineDownloadManager` fetches audio streams using HTTP redirect tracking with custom header and cookie preservation, saving direct `.m4a` files alongside persistent `.meta` metadata files.
- **Seamless Offline Fallback**: If network connectivity drops or the user initiates playback while offline, `YouTubeAudioService` instantly switches to the local audio file at the current millisecond timestamp without interrupting or throwing connection errors.

### 3. Reactive UI & State Management
- Built entirely on Jetpack Compose and Material 3 design tokens.
- Lifecycles are isolated using `collectAsStateWithLifecycle()` to prevent background battery drain.
- High-frequency UI events (playback progress sliders updating 4 times per second) are isolated in sub-composables to avoid whole-screen recompositions.
