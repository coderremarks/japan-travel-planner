# Demo script / デモ手順

A walkthrough of about ten minutes for a technical interview. Start the application as described in
[deployment.md](deployment.md) (with or without Docker) and open <http://localhost:3000>.

## 1. Product (3 minutes)

1. **Landing page in both languages.** The browser language decides `/en` or `/ja`; the switch in the
   header keeps the current page. Everything — dates, yen, durations, plurals — is localised.
2. **Register** (or sign in to the demo account if `TABIJI_DEMO_PASSWORD` was set). Mention the
   password policy and that the session lives in an HttpOnly cookie.
3. **New trip** → the editor. Search "Senso-ji", "Ueno", "Tokyo National Museum", "Skytree",
   "Akihabara"; mark the museum as a must-visit; set 2 days, max 3 visits a day, a budget.
4. **Optimize.** Show the timeline: departure, legs with mode, minutes and fare, waiting time, return.
   Point out the *estimate* labels and the "sample data" badges — nothing is presented as fact.
5. **Compare with "Keep my order".** The plan panel states how many more places the optimized plan
   fits and how much travel it saves.
6. **Unscheduled places.** Add a place on a closing day or reduce the budget: each place that did not
   fit has a precise reason ("closed on all of your dates", "over budget", "no route with the travel
   modes you allowed"), and the plan lists what to change ("try different dates", "allow taxis").
7. **Japanese holiday rule.** A museum closed on Mondays opens on a Monday holiday and closes the next
   day instead (月曜休館・祝日の場合は翌平日休館). The API test `PlanningApiIT` uses 2026-11-23
   (勤労感謝の日).
8. **Exports:** calendar file (`.ics`, in the UI language), JSON export, print view.
9. **Edit after planning** → the plan is marked out of date. Open the same trip in two tabs and save
   in both → the second save is refused (`412`) instead of overwriting the first.

## 2. Code (5 minutes)

- `backend/optimizer/.../solver/IteratedLocalSearch.java` — the search as pseudo-code in the Javadoc;
  then `Objective.java` (lexicographic objective, no weights).
- `backend/optimizer/.../solver/PlanValidator.java` and `OptimizerPropertyTest.java` — how the solver
  is checked independently and against an exact solver.
- `backend/app/.../itinerary/application/ItineraryService.java` (`load`) and `OwnershipSecurityIT.java`
  — the BOLA defence and its test.
- `backend/app/.../security/SecurityConfiguration.java` — deny by default, CSRF, headers, rate limits.
- `frontend/src/proxy.ts` — locale negotiation and the per-request CSP nonce.
- `docs/api/openapi.json` → `frontend/src/lib/api/schema.d.ts` — the contract both sides are tested
  against.

## 3. Evidence (2 minutes)

```bash
cd backend && ./mvnw verify            # 157 backend tests, SpotBugs, coverage gates
cd frontend && npm run verify          # lint, types, 43 unit tests, production build
cd frontend && npx playwright test     # 5 browser journeys with accessibility checks
java -cp backend/benchmarks/target/benchmarks.jar dev.tabiji.bench.QualityStudy
```

Questions worth inviting: *why not microservices* (ADR-0001), *why sessions instead of JWT*
(ADR-0003), *how do you know the optimizer is good* ([benchmarks.md](benchmarks.md)), *what would you
change for production* ([deployment.md](deployment.md), [known-limitations.md](known-limitations.md)).
