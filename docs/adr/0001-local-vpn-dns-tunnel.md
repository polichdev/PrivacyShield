# ADR 0001: Local VPN TUN Interface for DNS Interception

## Status
Accepted

## Context
Mobile ad-blocking and tracker mitigation on non-rooted Android devices typically requires one of two approaches:
1. Configuring a private DNS provider in system settings.
2. Establishing a local `VpnService` with a TUN interface that captures network traffic.

A private DNS configuration cannot perform per-application policy filtering, blocklist imports, or dynamic on-device logging without sending queries to a third-party server.

## Decision
Privacy Shield implements a local `android.net.VpnService` capturing UDP port 53 (DNS) traffic directly on the device.
- Non-DNS IP packets are bypassed or handled locally without remote VPN routing.
- DNS queries are parsed on-device using a lightweight `DnsPacket` parser.
- Blocked domains receive immediate synthetic NXDOMAIN responses constructed on-device.
- Permitted queries are forwarded to the configured upstream DNS resolver (e.g. Cloudflare, Quad9, Google, or custom) using protected sockets that bypass the VPN loop.

## Consequences
- **Advantages**: 100% on-device operation, zero telemetry, no root required, fine-grained control over individual queries.
- **Trade-offs**: Android VPN key icon appears in the status bar; only one VPN service can be active on Android at a time.
