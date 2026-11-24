# Testing strategy / テスト戦略

The goal is evidence, not a percentage: every important behaviour — above all every security
property and every planning rule — has a test that fails if it breaks. Tests run against a **real
PostgreSQL 18** (ADR-0005), never an in-memory substitute, so SQL, constraints and migrations are
tested exactly as they run in production.

## Test suites

| Suite | Tool | Count | Scope |
| --- | --- | ---: | --- |
| Optimizer unit + scenario + property | JUnit 6 (Jupiter), AssertJ | 74 | Graph algorithms (Dijkstra checked against Floyd–Warshall), time windows, travel estimation, route evaluation, solver scenarios, the independent validator, random-instance properties |
| API unit | JUnit 6, Mockito, ArchUnit | 25 | Opening-hours rules, password policy, ICS escaping and folding, score invariant, refusal of invalid plans, 9 architecture rules |
| API integration | Spring Boot test, MockMvc, PostgreSQL | 58 | Every endpoint through the real security filter chain and database: authentication, CSRF, ownership (BOLA), validation, concurrency, planning, exports, headers, the OpenAPI contract |
| Frontend unit | Vitest, Testing Library, jsdom | 43 | i18n (catalogue parity, plurals, formatting), API client (CSRF middleware, errors), safe redirects, draft model, plan panel rendering, CSP construction |
| End-to-end | Playwright (Chromium) + axe-core | 5 | Real browser against the running stack: sign-up → plan → export; the full Japanese flow; duplicate and delete; sign-in redirect; CSP nonce on every script; WCAG checks on each screen |

Counts are from the runs on 2026-11-18 (`./mvnw verify`, `npm run verify`, `npx playwright test`).
Three more Playwright specs regenerate the README screenshots and are skipped unless
`SCREENSHOTS=1` is set.

## What is tested where

**Optimizer** (pure Java, no Spring) — the most heavily tested part, because it is where subtle bugs
hide:

- *Scenario tests* name the cases a traveller would recognise: a must-visit closed on every trip day,
  a place open only outside the traveller's hours, a visit longer than the opening window, a day trip
  that is too far, a must-visit over budget, conflicting must-visits, a museum closed on day 1 and
  visited on day 2, lunch breaks, priorities, geographic ordering, the keep-order baseline, missing
  transit data and the effect of allowing taxis.
- *Property tests* over random instances: 600 plans never violate a hard constraint; on 400 small
  instances the heuristic reaches the exact optimum in at least 95 % of cases and never beats it;
  identical input gives identical plans; *Optimize* is never worse than *Keep my order*.
- The **`PlanValidator`** re-derives every time and total without using any solver code, so a solver
  bug cannot also hide itself in the check.

**Mocks are the exception.** Most tests run real code against a real database, because mocks would
hide exactly the SQL, transaction and security-filter behaviour worth testing. Mockito is used where a
real collaborator cannot be made to misbehave: `PlanningServiceTest` makes a stand-in optimizer
return an invalid plan and proves that the service refuses it and stores nothing. Mockito is loaded as
a declared JVM agent (`backend/app/pom.xml`) rather than self-attaching, which JDK 21 deprecates.

**API integration tests** are written from an attacker's point of view as much as a user's:
`OwnershipSecurityIT` signs in as a second user and tries every itinerary endpoint on someone else's
trip; `AuthFlowIT` covers fixation, enumeration, CSRF, mass assignment and rate limits; `WebSecurityIT`
checks headers, body limits, error bodies, CORS, the actuator surface and request-ID handling. The
full mapping from control to test is in [security-controls.md](security-controls.md).

**Contract tests** connect the two halves: `OpenApiContractIT` compares the OpenAPI document generated
from the code with the committed `docs/api/openapi.json`, and CI regenerates the frontend's types from
that file and fails on any difference. An API change therefore shows up as a reviewed diff instead of
a runtime surprise in the browser.

**End-to-end tests** run against a production build (`next build` + `next start`, or the compose
stack in CI), not the dev server, and run an axe accessibility scan on every screen they visit. They
block OpenStreetMap tile requests so they never depend on a third-party service.

## Coverage

Measured with JaCoCo (backend, unit + integration tests) and V8 (frontend unit tests) on 2026-11-18.

| Module | Lines | Branches | Build gate |
| --- | ---: | ---: | --- |
| `backend/optimizer` | 95.1 % (1,607 / 1,690) | 88.8 % (876 / 986) | 90 % / 80 % |
| `backend/app` | 90.9 % (1,364 / 1,500) | 74.2 % (241 / 325) | 85 % / 65 % |
| `frontend` unit tests | 33.9 % (160 / 472) | 34.8 % (130 / 374) | none |

The frontend number is low on purpose and should be read correctly: the logic modules are unit-tested
(i18n dictionary and config 100 %, API client 100 %, draft model 95 %, navigation 90 %, formatting 87 %,
plan panel 85 %), while forms, the editor and the trip list are exercised by the Playwright journeys,
whose coverage is not measured. The gates sit a few points below the measured values so that they
catch regressions without making the build flaky.

## Static analysis

| Tool | Scope | Enforced |
| --- | --- | --- |
| `javac -Xlint:all -Werror` | Java | Every compilation |
| Spotless (palantir-java-format) | Java formatting | `verify` |
| SpotBugs + FindSecBugs | Java bug and security patterns; 0 findings, a few narrow exclusions each justified in `config/spotbugs-exclude.xml` | `verify` |
| ArchUnit | Layering, cycles, framework-free optimizer, coding rules | Unit tests |
| ESLint (Next.js + TypeScript rules, no `dangerouslySetInnerHTML`, no `innerHTML`, no `console`) | TypeScript | `npm run verify`, zero warnings |
| TypeScript `strict` | TypeScript | `npm run verify` |
| Prettier | TypeScript, JSON, CSS, Markdown in `frontend/` | `npm run verify` |
| CodeQL (`security-extended`) | Java, TypeScript, workflows | CI |
| OSV-Scanner, `npm audit`, gitleaks | Dependencies, secrets | CI |

## Running the tests

```bash
# Backend: everything (tests, Spotless, SpotBugs, coverage gates). Uses Docker if available,
# otherwise embedded PostgreSQL binaries — no installation needed.
cd backend && ./mvnw verify

# Frontend: format, lint, type check, unit tests, production build
cd frontend && npm ci && npm run verify

# End-to-end: start the API and a production web build, then run Playwright
cd backend && ./mvnw -pl app spring-boot:test-run           # terminal 1 (port 8080)
cd frontend && npm run build && npm start -- --port 3100    # terminal 2
cd frontend && npx playwright install chromium && npx playwright test   # terminal 3
```

Force a database mode with `-Dtabiji.test.db=container` (Testcontainers) or `-Dtabiji.test.db=embedded`.
CI uses `container`, so a missing Docker daemon fails the build instead of silently switching.

## Continuous integration

`.github/workflows/ci.yml` runs on every pull request and every push to `main`: the backend gate, the
frontend gate plus the contract and audit checks, then the end-to-end suite against the compose stack
(both container images are built from scratch). `codeql.yml` and `security.yml` add static analysis,
dependency and secret scanning. The current status is shown by the badges in the README; the first
run on 2026-11-25 passed every job.

What the first runs found, and what changed:

- CodeQL flagged that CSRF protection was switched off on the read-only API-documentation filter
  chain. Switching it off protected nothing, so it is on again (`SecurityConfiguration`).
- Dependabot's first proposals included Java 24 (already end-of-life), JDK 26, Node.js 26 and a
  TypeScript major that the contract tooling does not support; the grouped update failed the frontend
  job, which is the guard working as intended. `dependabot.yml` now keeps the LTS runtime lines
  (ADR-0008) and TypeScript 5 (ADR-0009) and delivers major versions as separate pull requests.

## Not covered yet

- Load and concurrency tests of the HTTP API (planned: a k6 or Gatling scenario in CI).
- Automated assertions on security log events (they are reviewed, not asserted).
- Cross-browser end-to-end runs (Firefox and WebKit projects can be added to `playwright.config.ts`).
- Mutation testing (PIT) to measure how strong the optimizer tests are, beyond line coverage.
- Visual regression testing of the UI.
