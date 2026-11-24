# Known limitations / 既知の制約

Honest boundaries of the current version. Each item says why it is acceptable for now and what would
remove it ([roadmap](roadmap.md)).

## Data

| Limitation | Impact | Path forward |
| --- | --- | --- |
| The catalogue (44 places in 5 areas), opening hours and admissions are **sample data**, not verified facts | Plans demonstrate the method, not real-world schedules. Every place is marked `SAMPLE_UNVERIFIED` and labelled in the UI | Import official or open data (for example municipal open-data catalogues) with `verified_on` dates; keep the provenance labels |
| Transit times and fares come from a **simplified model** (average speeds, frequency-based waits, round fares), not timetables | Estimates can be off by several minutes or hundreds of yen; all legs are labelled as estimates | A GTFS/GTFS-JP based `TravelEstimator` or a licensed routing API, behind the existing port |
| No traffic, weather, crowds or seasonal hours | Taxi times use fixed speeds; seasonal opening hours must be entered as closures | Provider data where licensed; per-season hours in the schema |
| Japanese holidays are seeded for **2026–2027** only | For other years the plan shows a notice and applies weekday rules | Seed further years from the Cabinet Office CSV, or load it on start-up |
| The sample rail network covers parts of Tokyo, Kyoto, Osaka and their connections | Places without a station nearby cannot be reached by transit (reported as `NO_ROUTE`, never invented) | Full network data |

## Optimizer

| Limitation | Impact | Path forward |
| --- | --- | --- |
| Heuristic, not exact | Reaches the exact optimum in 95.8 % of small benchmark instances; the mean score gap is 0.18 % ([benchmarks.md](benchmarks.md)) | Expected for an NP-hard problem; the exact solver remains available for tests |
| No move exchanges one must-visit for another | When more must-visits are requested than can fit, the plan keeps the maximum *number* but not always the best-scoring ones | Add a "replace must-visit" neighbourhood to `LocalSearch` |
| Mode choice is fixed per pair before the search | A cheaper but slower mode is not chosen even when it would let one more place fit the budget | Budget-aware mode choice inside route evaluation (costlier inner loop) |
| No preference for balanced days | A short trip may leave the last day empty when everything fits earlier | A secondary objective level for day balance |
| Waiting is allowed and simply shown | A plan can start with waiting for the first place to open | Shift departure times forward when the first visit would wait |
| Single-threaded per request, 3 s cap | The largest trips (50 places × 14 days) take about 1–2 s on a laptop | Fine for the demo; parallel restarts would cut the time if needed |

## Product

| Limitation | Impact | Path forward |
| --- | --- | --- |
| No e-mail verification, password reset or MFA/passkeys | Registration reveals whether an e-mail is already registered (rate-limited); a forgotten password cannot be recovered | E-mail flow; WebAuthn passkeys (supported by Spring Security 7) |
| No account deletion or data export of the whole account | Users cannot remove their account themselves | Self-service deletion (the database already cascades) |
| On phones the plan is shown below the editor | More scrolling on small screens | A tabbed editor / plan layout on narrow viewports |
| Only English and Japanese | — | The i18n layer supports more locales; add catalogues |
| Map tiles come from OpenStreetMap's public servers | Their usage policy limits heavy use; the tile server sees the viewer's IP and area | Self-hosted or commercial tiles for real traffic |

## Operations

| Limitation | Impact | Path forward |
| --- | --- | --- |
| Sessions and rate-limit buckets are in memory | One API instance only; a restart signs everyone out | Spring Session JDBC and a shared bucket store |
| The compose stack is tested on Linux only | CI builds and runs it on GitHub's Ubuntu runners on every push; it has not been tried on Docker Desktop for Windows or macOS | Run `docker compose up` once on a Docker Desktop machine |
| No load test | Throughput under concurrent users is unknown | k6/Gatling scenario in CI |
| Code scanning uploads (CodeQL, OSV) rely on the repository being public | A private fork without GitHub Advanced Security would fail those jobs | Keep the repository public, or disable SARIF upload in a private fork |
