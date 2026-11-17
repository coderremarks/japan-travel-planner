# ADR-0001: Modular monolith with Maven modules

- Status: Accepted (2026-11-18)

## Context

One developer, one relational data set, strongly consistent edits (an itinerary and its plan change
together), and no part of the system has a scaling profile that would justify its own deployment — the
CPU-heavy optimizer finishes in milliseconds for realistic inputs.

## Decision

Ship one Spring Boot API plus one Next.js frontend. Inside the backend, enforce boundaries at **build
time**: the optimizer is a separate Maven module with no Spring/JPA dependency; inside the app, packages
are organised by feature (`account`, `catalog`, `itinerary`, `transport`) and then by layer (`api`,
`application`, `domain`, `infrastructure`), with ArchUnit tests guarding the dependency rules.

## Consequences

- No network hops, no distributed transactions, simple local development and debugging.
- If the optimizer ever needs independent scaling, it is already isolated behind `PlanningService` and
  can be extracted behind the same interface.
- Keeping feature packages decoupled takes discipline; ArchUnit turns that discipline into failing tests.
