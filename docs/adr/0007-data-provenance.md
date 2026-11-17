# ADR-0007: Every estimate is labelled; no invented facts

- Status: Accepted (2026-11-18)

## Context

Train timetables, fares, admission prices and opening hours change frequently and are often licensed
data. Showing guessed values as facts would mislead travellers.

## Decision

- Catalogue rows carry `data_source` (`SAMPLE_UNVERIFIED` for the bundled demo data) and `verified_on`.
- Every travel leg carries `estimated: true` and a `source` (`DISTANCE_MODEL`,
  `SAMPLE_TRANSIT_NETWORK`). Transit legs come from a simplified rail graph whose edge times are
  **computed from station distances and documented average speeds**, not from timetables.
- The UI shows "≈" values, an "estimate" badge and a "verify before you travel" notice.
- Real providers (ODPT / GTFS-JP, NAVITIME, Ekispert, Google Routes, OSRM) plug in behind the
  `TravelEstimator` port and set `estimated: false` only when their data is authoritative.

## Consequences

Honest output and a clear upgrade path; demo itineraries are plausible but explicitly not authoritative.
