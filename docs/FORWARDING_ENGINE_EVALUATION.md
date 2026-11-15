# Android Privacy Shield — Traffic Forwarding Engine Evaluation

**Document Version:** 1.0.0  
**Status:** Approved Architectural Decision  
**Scope:** Traffic forwarding and DNS filtering routing strategy for the Android Privacy Shield MVP.

---

## 1. Executive Summary

Android Privacy Shield is a local, privacy-first network protection tool operating via Android's `VpnService`. A core mandate defined in `ARCHITECTURE.md` (Section 5 & 7) and `RULES.md` (Section 5 & 7) is **network correctness**: the application must never disrupt permitted traffic or drop connections unintentionally as a shortcut.

This evaluation reviews options for packet forwarding and tunnel routing, concluding with the selection and architectural justification for the Phase 2/3 MVP implementation.

---

## 2. Evaluation of Forwarding Engines

### 2.1 Full-Tunnel tun2socks (BadVPN / Go-tun2socks / xjasonlyu)

* **Overview:** Translates arbitrary TCP and UDP IP packets intercepted by the TUN interface into standard user-space POSIX/Java sockets.
* **Licensing:**
  * Original BadVPN: GNU GPL v3. (High licensing burden for flexible mobile distribution).
  * Go-based variants: Apache 2.0 / MIT.
* **Architecture & Android Compatibility:**
  * Requires compiled native shared libraries (`.so`) for all Android ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
  * Substantial binary footprint (+15MB to +30MB for Go runtimes).
* **Performance & Battery:** High context switching overhead between kernel TUN, JNI, user-space goroutines/threads, and outbound sockets. Substantial battery drain on mobile devices under active load.
* **Security & Failure Surface:** Intercepting all TCP streams increases risk of connection drops, TCP window stalls, broken MTU/fragmentation, and memory leaks.

### 2.2 lwIP (Lightweight TCP/IP Stack)

* **Overview:** A widely-used open-source embedded TCP/IP implementation (used in Google's Intra and Outline).
* **Licensing:** Modified BSD License (permissive and distribution-friendly).
* **Architecture & Android Compatibility:** Small C library, highly portable, low memory overhead.
* **Trade-offs:**
  * Excellent for embedding, but requires complex JNI binding and native memory lifecycle management.
  * Necessary if and only if the application must inspect or intercept non-DNS TCP/IP traffic at full-device scale.
  * For DNS-only ad and tracker filtering, running a complete virtual TCP stack inside the app process introduces unnecessary maintenance and crash exposure.

### 2.3 DNS-Targeted TUN Routing with Protected Upstream Socket (Selected MVP Strategy)

* **Overview:**
  The `VpnService` builder configures a local virtual network interface with a dedicated private IP (e.g. `10.0.0.2/32`) and designates it as the system DNS server (`addDnsServer("10.0.0.2")`).
  Crucially, it adds a route **only** for this DNS server IP: `addRoute("10.0.0.2", 32)`.
* **Licensing:** Native Kotlin / Android Framework (Apache 2.0 / AOSP). Zero external native binary dependencies.
* **Network Correctness & Safety:**
  1. **Zero Dropped Permitted Traffic:** All application TCP, UDP, TLS, HTTP/HTTPS, and streaming packets never enter the TUN interface. They continue to flow directly through the physical network interface (Wi-Fi or Cellular) with 100% native speed, zero latency penalty, and zero risk of broken TCP states.
  2. **Deterministic DNS Interception:** All DNS queries destined for `10.0.0.2:53` are delivered by the kernel directly to the TUN file descriptor.
  3. **Loop Prevention via Protected Upstream Socket:** For allowed DNS queries, upstream resolution is performed over a `DatagramSocket` protected via `VpnService.protect(socket)`. The Android kernel ensures protected socket packets bypass the VPN interface and reach the physical gateway directly, preventing routing loops.
  4. **Immediate Local Synthesis for Blocked Queries:** For blocked queries, an RFC-compliant DNS response (A record with `0.0.0.0`, TTL 300) is synthesized in-memory and written back to the TUN interface.
  5. **Memory and Battery Efficiency:** Allocations are bounded to small DNS datagram buffers (< 4096 bytes). No heavy threads or full TCP state tables are retained.
* **Per-App Filtering:** Full compatibility with Android's `VpnService.Builder.addAllowedApplication` and `addDisallowedApplication`.

---

## 3. Comparison Matrix

| Evaluation Criteria | Full-Tunnel tun2socks | lwIP Native Stack | DNS-Targeted TUN Routing (Selected) |
| :--- | :--- | :--- | :--- |
| **Permitted Traffic Safety** | Medium (risk of TCP stalls) | Good (proven in Outline) | **Flawless (untouched physical flow)** |
| **License Compliance** | Complex (GPL / Apache mix) | Permissive (BSD) | **100% Clean (Standard Kotlin/AOSP)** |
| **Memory / Battery Footprint** | High | Low-Medium | **Minimal (< 5MB RAM overhead)** |
| **Binary Size Overhead** | +15MB – 30MB | +2MB – 4MB | **0 MB (No native blobs)** |
| **Routing Loop Prevention** | Complex per-socket protect | Complex per-socket protect | **Direct `VpnService.protect()`** |
| **Device Stability & Maintenance**| Complex multi-ABI NDK builds | Moderate NDK maintenance | **Simple, testable, robust JVM/Kotlin** |

---

## 4. Architectural Decision Record (ADR)

1. **Decision:** Adopt **DNS-Targeted TUN Routing with Protected Upstream Sockets** as the primary forwarding and filtering engine for the Android Privacy Shield MVP.
2. **Interface Abstraction:** The network packet processor is encapsulated behind clear interfaces (`DnsPacketProcessor`, `TunnelManager`), allowing future pluggable integration of an lwIP forwarding engine should full-tunnel non-DNS traffic inspection be required in Phase 6.
3. **Verification:** The implementation must be verified with unit tests covering IPv4/UDP/DNS parsing, synthetic response generation, checksum calculation (RFC 1071), rule evaluation precedence, and clean socket lifecycle management.
