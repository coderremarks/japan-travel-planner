# ADR-0006: Store generated plans as immutable JSONB snapshots

- Status: Accepted (2026-11-18)

## Context

A generated plan is derived data: it is produced in one step, always read whole, never queried by its
internals, and must not silently change when catalogue data changes later.

## Decision

Inputs (itinerary, stops, preferences) are normalised tables. The latest generated plan is stored in
`itinerary_plan.plan_json` (`jsonb`) together with the algorithm version and a SHA-256 fingerprint of the
inputs. When the inputs change, the fingerprint no longer matches and the UI marks the plan as out of
date.

## Consequences

- One row read per plan view, no joins; the plan format evolves through an explicit `schemaVersion`
  field.
- Plans cannot be queried relationally (e.g. "all plans visiting Kinkaku-ji"). That is not a
  requirement; a reporting table could be added if it becomes one.
