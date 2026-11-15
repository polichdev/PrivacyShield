# Privacy Shield Security & Threat Model

This document specifies the security controls, boundary validations, and threat mitigations implemented in Privacy Shield.

## Threat Analysis & Mitigations

### 1. LAN DNS Spoofing & Cache Poisoning
- **Vulnerability**: In local Wi-Fi environments, hostile actors or rogue devices can send unsolicited UDP response packets pretending to be authoritative DNS answers.
- **Mitigation**: `UdpUpstreamDnsResolver` verifies the source IP address of every received packet against the designated primary and fallback DNS upstream addresses (`sender != primary && sender != fallback continue`). Furthermore, it validates that the response transaction ID matches the exact query transaction ID (`respTxId == queryTxId`).

### 2. Malicious Blocklist URLs & SSRF
- **Vulnerability**: User input specifying blocklist download URLs could attempt to invoke local file system paths (`file:///`), internal device schemes (`javascript:`), or unsupported protocols (`ftp://`).
- **Mitigation**: `RuleRepository.isValidBlocklistUrl()` enforces strict URL validation:
  - Must strictly start with `http://` or `https://`.
  - Parsed host must be non-empty and free of spaces or illegal characters.
  - Connection timeouts are capped to prevent denial-of-service hanging.

### 3. VPN Leak & Routing Boundary
- **Vulnerability**: Incorrect route definitions could unintentionally hijack non-DNS packets or leak application traffic.
- **Mitigation**:
  - The VPN route is strictly constrained to `10.0.0.1/32`.
  - Sockets querying upstream DNS are protected via `VpnService.protect()`.
  - Application package filtering allows fine-grained per-app selection, with the application's own package always allowed access to `10.0.0.1` so essential lookups function without failure.

### 4. Input Sanitization & Domain Normalization
- All domain strings undergo RFC 3490 Internationalizing Domain Names (IDN) ASCII conversion via `IDN.toASCII(name, IDN.USE_STD3_ASCII_RULES)`.
- Illegal labels, trailing periods, uppercase characters, and over-length domains (> 253 octets) are normalized or rejected before matching.
