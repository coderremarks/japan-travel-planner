# ADR-0002: Framework-free optimizer; TOPTW + Iterated Local Search

- Status: Accepted (2026-11-18)

## Context

Itinerary planning with opening hours, multiple days, must-visit places, a budget and per-day limits is
a multi-day *Tourist Trip Design Problem*, usually modelled as the *Team Orienteering Problem with Time
Windows* (TOPTW). It is NP-hard. Asking an LLM to "arrange" places would be neither explainable nor
verifiable, and exact MIP solvers add a heavy native dependency for instance sizes (≤ 60 candidates)
where good heuristics are known to perform well.

## Decision

- Implement the engine in plain Java 21 in its own module (`backend/optimizer`), with an immutable
  input/output model and no I/O.
- Algorithm: feasibility pre-analysis → must-visit-first cheapest insertion → optional places by
  `score² / added time` (Vansteenwegen et al., 2009) → local search (2-opt, or-opt, inter-day
  relocate/swap, re-insertion, replacement) → deterministic Iterated Local Search with a fixed seed.
- The objective is lexicographic: must-visits scheduled, then preference score, then travel minutes,
  then cost — easy to explain to users and to test.
- An exhaustive reference solver for small instances lives in the module and is used by tests and by
  the quality study.

## Consequences

- The engine is unit-testable without Spring and benchmarkable with JMH.
- Results are reproducible: the search is bounded by iteration counts, not wall-clock time; a time cap
  exists only as a safety net and is reported when it triggers.
- Heuristic results are not guaranteed optimal; the measured gap to the exact solver is published in
  `docs/benchmarks.md`.
