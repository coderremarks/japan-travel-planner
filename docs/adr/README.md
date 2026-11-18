# Architecture Decision Records

Format: Michael Nygard's ADR template — context, decision, consequences. An accepted ADR is not edited
later; a change of mind is a new ADR that supersedes the old one.

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-modular-monolith.md) | Modular monolith with Maven modules | Accepted |
| [0002](0002-framework-free-optimizer.md) | Framework-free optimizer; TOPTW + Iterated Local Search | Accepted |
| [0003](0003-server-side-sessions.md) | Server-side sessions + CSRF tokens instead of JWT in the browser | Accepted |
| [0004](0004-same-origin-api-proxy.md) | Same-origin API through the Next.js proxy | Accepted |
| [0005](0005-real-postgres-in-tests.md) | Real PostgreSQL in tests: Testcontainers, embedded fallback | Accepted |
| [0006](0006-plan-snapshot-jsonb.md) | Store generated plans as immutable JSONB snapshots | Accepted |
| [0007](0007-data-provenance.md) | Every estimate is labelled; no invented facts | Accepted |
| [0008](0008-platform-versions.md) | Java 21 LTS, Spring Boot 4.1, PostgreSQL 18, Next.js 16 | Accepted; TypeScript version amended by 0009 |
| [0009](0009-frontend-as-built.md) | Dependency-free i18n, TypeScript 5.9, no Cache Components | Accepted |
| [0010](0010-edge-proxy-entry-point.md) | An edge proxy is the only public entry point | Accepted; amends 0004 |
