# ADR 0002: On-Device Rule Evaluation and Tri-State Hierarchy

## Status
Accepted

## Context
DNS rule evaluation must execute within a strict latency budget (<2ms per query) while supporting tens of thousands of blocklist rules, custom user additions, and application isolation policies.

## Decision
Privacy Shield implements a three-tier rule evaluation pipeline:
1. **Application Policy Check**: Evaluates if the originating package is isolated or exempted.
2. **Explicit User Allowlist**: Overrides all blocklists. If a domain or its parent wildcard is in the user's allowlist, it is immediately permitted.
3. **Blocklist Evaluation**: Checks the domain against user-defined blocklists and subscribed hosts lists (in-memory fast lookup). If matched, the query is blocked with synthetic NXDOMAIN.
4. **Default Action**: If no rules match, the query is resolved upstream.

## Consequences
- **Advantages**: Deterministic behavior where user allowlists always take precedence; sub-millisecond lookup latency.
- **Trade-offs**: In-memory storage scales with total rule count (~2-5 MB for standard lists).
