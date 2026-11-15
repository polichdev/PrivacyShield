# ADR 0003: Background Audio Playback and Seamless Offline Sync

## Status
Accepted

## Context
Users require background audio listening for shared YouTube links, with support for offline storage and uninterrupted playback when transitioning between network connectivity and offline states.

## Decision
1. **Audio Engine**: Utilize AndroidX Media3 `ExoPlayer` hosted within a foreground service (`YouTubeAudioService`) coupled with a `MediaSession`.
2. **Metadata & File Storage**: Downloaded audio streams are stored directly in the application's private files directory (`$filesDir/offline_audio/$videoId.m4a`) accompanied by an atomic metadata file (`$videoId.meta`).
3. **Seamless Offline Fallback**: When `ExoPlayer` encounters a network playback exception, the service inspects whether an offline file exists for the current track. If present, it transparently resumes playback from the exact position from local storage without presenting connection errors.
4. **Cache Sharing**: Streaming audio utilizes Media3 `SimpleCache` with LRU eviction to prevent repeated network fetching for cached chunks.

## Consequences
- **Advantages**: Robust offline experience without network dependencies; preserved audio titles and artist metadata.
- **Trade-offs**: Local storage footprint managed by explicit user deletion or queue clearing.
