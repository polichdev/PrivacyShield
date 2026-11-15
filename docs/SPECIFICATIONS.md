# Technical Specifications

## 1. System Overview

Privacy Shield is an on-device Android privacy engine providing:
1. **Network Layer**: Local TUN packet capture and DNS query interception.
2. **Filtering Engine**: Rule-based domain blocker with custom allowlist/blocklist subscriptions.
3. **Audio Subsystem**: Background YouTube audio streamer with offline caching.
4. **Data Layer**: SQLite database running with Write-Ahead Logging (WAL) and Kotlin StateFlow reactive feeds.

---

## 2. Packet Processing & DNS Specification

### 2.1 TUN Interface Parameters
- **Interface Address**: `10.111.222.1/32`
- **MTU**: `1500` bytes
- **Route**: `10.111.222.2/32`
- **DNS Server**: `10.111.222.2` (captured locally on port 53)

### 2.2 DNS Packet Pipeline
```
[App Network Query]
        │ (UDP:53)
        ▼
[TUN Interface (VpnService)]
        │
        ▼
[DnsPacketParser] ──> Extract Query Domain & Transaction ID
        │
        ├─► [AppPolicy Repository] ──► Package Excluded? ──► Forward Upstream
        │
        ├─► [DomainRules: USER_ALLOW] ──► Match? ──► Forward Upstream
        │
        ├─► [DomainRules: BLOCKLIST] ──► Match?
        │         │
        │         └─► [Synthetic NXDOMAIN Generator] ──► Return to App
        │
        └─► [Protected Upstream Socket] ──► Resolve & Return to App
```

### 2.3 Synthetic NXDOMAIN Response Format
Synthetic negative responses are constructed on-device:
- `QR`: 1 (Response)
- `RCODE`: 3 (Name Error / NXDOMAIN)
- `ANCOUNT`: 0
- Answer section empty; Question section echoed from query.

---

## 3. Media & Offline Audio Pipeline

### 3.1 Stream Extraction
- Input: YouTube URL or 11-character video ID (`/watch?v=`, `youtu.be/`, `/shorts/`).
- Engine: `YouTubeStreamResolver` queries Invidious API instances with exponential fallback to direct chocolate stream endpoints.

### 3.2 Offline Storage Specification
- **Storage Location**: `Context.filesDir/offline_audio/`
- **Media Format**: AAC / M4A stream (`${videoId}.m4a`).
- **Metadata Format**: UTF-8 plain text (`${videoId}.meta`):
  ```
  [Line 0]: Video Title
  [Line 1]: Video Author / Channel
  ```
- **Redirection Logic**: `OfflineDownloadManager` handles cross-host HTTP 301/302 redirects with preserved `User-Agent`, `Referer`, and cookie headers up to 5 hops.

---

## 4. Database Schema Specification

The on-device SQLite database `privacy_shield.db` (Version 2) defines:

### `rules` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | TEXT | PRIMARY KEY | Unique UUID or deterministic identifier |
| `domain` | TEXT | NOT NULL | Normalized lowercase domain name |
| `match_type` | TEXT | NOT NULL | `EXACT` or `WILDCARD` |
| `source` | TEXT | NOT NULL | `STARTER`, `USER_ALLOW`, `USER_BLOCK`, `SUBSCRIPTION` |
| `created_at` | INTEGER | NOT NULL | Millisecond timestamp |

### `blocklists` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | TEXT | PRIMARY KEY | Subscription ID |
| `name` | TEXT | NOT NULL | User-visible display name |
| `url` | TEXT | NOT NULL | Remote hosts or plain domain list URL |
| `enabled` | INTEGER | NOT NULL | 1 (active) or 0 (disabled) |
| `rule_count` | INTEGER | NOT NULL | Cached count of parsed rules |
| `last_updated` | INTEGER | NOT NULL | Last successful sync timestamp |

### `dns_logs` Table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | Monotonic log ID |
| `domain` | TEXT | NOT NULL | Queried domain |
| `blocked` | INTEGER | NOT NULL | 1 (blocked) or 0 (allowed) |
| `timestamp` | INTEGER | NOT NULL | Millisecond timestamp |
