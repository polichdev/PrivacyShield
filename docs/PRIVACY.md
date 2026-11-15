# Privacy Architecture & Zero-Telemetry Guarantees

Privacy Shield is designed under a strict **Zero-Telemetry, Local-First** guarantee.

## Core Privacy Invariants

### 1. Strictly Local On-Device Processing
- Privacy Shield does not operate any analytics server, crash tracking SDK, telemetry library, or external monitoring daemon.
- All DNS parsing, domain matching, rule filtering, and metrics calculation are performed on the device CPU inside the sandboxed Android process.

### 2. Local Database Privacy
- The SQLite database `privacy_shield.db` stores only query statistics (`total_queries`, `blocked_queries`, `allowed_queries`) and user-configured rules.
- Filter event logs stored locally in the database are never synced to any remote server and can be cleared at any time by clearing application storage.

### 3. Audio & YouTube Privacy
- Video streaming and audio playback do not require user authentication or Google account login.
- Stream resolution is performed through direct or public privacy-respecting instances without forwarding tracking identifiers, device IDs, or user analytics.
- Downloaded audio files are stored locally in the application's private storage directory (`files/offline_audio/`) inaccessible to other installed applications.
