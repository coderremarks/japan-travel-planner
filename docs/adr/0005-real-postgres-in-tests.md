# ADR-0005: Real PostgreSQL in tests: Testcontainers, embedded fallback

- Status: Accepted (2026-11-18)

## Context

H2 "PostgreSQL mode" differs from PostgreSQL in exactly the places this project relies on (JSONB,
deferrable constraints, `CHECK` constraints, `timestamptz`, case-insensitive search). Testcontainers needs
Docker, which is missing on some developer machines (including the one this project was built on), and
Docker Desktop needs a paid subscription in larger companies.

## Decision

Integration tests always run against **real PostgreSQL 18**: a Testcontainers container when Docker is
available (CI), otherwise embedded PostgreSQL binaries (`io.zonky.test:embedded-postgres`). The choice is
made once per JVM by a small test-support class and can be forced with
`-Dtabiji.test.db=container|embedded`. The same mechanism powers `./mvnw spring-boot:test-run` for local
development without Docker.

## Consequences

- Same SQL dialect and Flyway migrations everywhere; no H2-only bugs.
- Two code paths in test support; both are exercised (CI uses containers, local runs use embedded
  binaries).
