# Roadmap / ロードマップ

Ordered by value for a real launch. Nothing here is needed to run or demonstrate the current version.

## Next (data and trust)

1. **Real data behind the existing ports.** A GTFS/GTFS-JP `TravelEstimator` for transit and an
   import job for verified opening hours and admissions with `verified_on` dates. The solver and the
   API stay unchanged (ports and adapters, [architecture.md](architecture.md#ports-and-adapters-for-travel-data)).
2. **Holiday data for every year**, loaded from the Cabinet Office's published CSV instead of a seed
   migration.
3. **Account lifecycle:** e-mail verification, password reset, self-service account deletion,
   passkeys (WebAuthn) as an alternative to passwords.

## Then (planning quality)

4. **Must-visit exchange neighbourhood** in `LocalSearch`, found by the quality study
   ([benchmarks.md](benchmarks.md#heuristic-vs-the-exact-optimum)).
5. **Budget-aware mode choice:** let route evaluation pick between the best and the cheapest leg
   when the budget is the binding constraint.
6. **Day balancing and later departures** as additional, explainable objective levels.
7. **Pinned visits:** "Senso-ji on day 2 at 9:00" as a hard time window set by the traveller.

## Then (operations)

8. **Horizontal scaling:** Spring Session JDBC, rate-limit buckets in PostgreSQL or Redis.
9. **Load test in CI** (k6 or Gatling) with a latency budget for the planning endpoint.
10. **Observability:** OpenTelemetry traces and metrics (Micrometer is already on the classpath via
    actuator), dashboards for planning time and rate-limit rejections.
11. **Automated assertions on security log events** and mutation testing (PIT) for the optimizer.
12. **Container hardening in CI:** image vulnerability scanning and signing (for example cosign),
    build caching for faster pipelines, and automated refreshes of the Temurin and Maven base images
    (Renovate understands their tags; Dependabot does not, so they are updated by hand today).

## Later (product)

13. Sharing a read-only itinerary link (with an explicit, revocable token).
14. Offline-capable itinerary view on phones; a tabbed editor/plan layout for small screens.
15. More languages (the i18n layer is locale-agnostic).
