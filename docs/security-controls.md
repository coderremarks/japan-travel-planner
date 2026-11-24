# Security controls / セキュリティ対策

Every control below points to the code that implements it and to the evidence that it works.
"Test" means an automated test that runs on every build; "Manual" means it was checked by hand and
how; "CI" means it is enforced by a GitHub Actions workflow on every push and pull request (green since
the first run on 2026-11-25). The threats these controls answer are in the [threat model](threat-model.md).

Test classes: `AuthFlowIT`, `OwnershipSecurityIT`, `WebSecurityIT`, `ItineraryApiIT`, `CatalogApiIT`,
`PlanningApiIT` (integration tests on PostgreSQL), `PasswordPolicyTest`, `CalendarExporterTest`,
`CandidateScoringTest`, `PlanningServiceTest`, `ArchitectureTest` (unit), `proxy.test.ts` (frontend
unit), `planning.spec.ts` (Playwright end-to-end).

## OWASP Top 10 (2021)

### A01 Broken access control

| Control | Implementation | Evidence |
| --- | --- | --- |
| Deny by default | Every route is listed in `SecurityConfiguration`; `anyRequest().denyAll()` | Test: `WebSecurityIT.unknownRoutesAndWrongMethodsGiveProblemResponses` |
| Object-level authorisation (BOLA/IDOR) | `ItineraryService.load` queries by ID **and** owner; foreign and unknown IDs both return 404; attempts are logged | Test: `OwnershipSecurityIT` — another user cannot read, update, delete, duplicate, plan or export; foreign and missing IDs look identical; lists show only own trips |
| Ownership cannot be reassigned | The owner comes from the session only; request DTOs have no owner field and unknown fields are rejected | Test: `OwnershipSecurityIT.ownerCannotBeReassignedThroughTheBody` |
| Role-based access | `/api/v1/admin/**` requires `ROLE_ADMIN` in the filter chain and `@PreAuthorize` in the service | Test: `WebSecurityIT.adminEndpointRequiresAdminRole`, `OwnershipSecurityIT.regularUsersCannotUseAdminEndpoints` |
| CSRF on every state change | Cookie-to-header double submit (`SpaCsrfTokenRequestHandler`), including login, registration and logout | Test: `AuthFlowIT.stateChangingRequestsWithoutCsrfTokenAreRejected`, `OwnershipSecurityIT.stateChangesWithoutCsrfTokenAreRejectedEvenWhenSignedIn`, `WebSecurityIT.logoutWithoutCsrfIsRefused` |
| No cross-origin access | CORS allows no origin unless configured; the browser uses one origin | Test: `WebSecurityIT.crossOriginRequestsGetNoCorsGrant` |
| Anonymous requests | Protected routes answer 401 problem details, never a redirect | Test: `OwnershipSecurityIT.anonymousRequestsAreUnauthenticated` |
| Safe post-login redirects | `safeRedirectPath` accepts only same-site relative paths (no `//host`, no schemes) | Test: `navigation.test.ts`; E2E: sign-in redirect journey |

### A02 Cryptographic failures

| Control | Implementation | Evidence |
| --- | --- | --- |
| Password storage | Argon2id (Spring Security's v5.8 parameters) via `DelegatingPasswordEncoder`; bcrypt hashes are verified and upgraded on next login | Test: registration and login flows in `AuthFlowIT` use it end to end |
| No home-made crypto | Hashing, random tokens and session IDs come from Spring Security / the JDK | Code review; ArchUnit forbids nothing here, so this is a review point |
| Cookies only over TLS in production | `__Host-TABIJI_SESSION`, `Secure`, `HttpOnly`, `SameSite=Lax`; CSRF cookie `Secure` follows the request scheme | Manual: production profile run on 2026-11-18 returned `__Host-TABIJI_SESSION=…; Path=/; Secure; HttpOnly; SameSite=Lax` |
| TLS and HSTS | Terminated at the edge proxy in a real deployment ([deployment.md](deployment.md)) | Not applicable locally (plain HTTP on localhost) |

### A03 Injection

| Control | Implementation | Evidence |
| --- | --- | --- |
| SQL injection | JPA with bound parameters only; search uses Criteria API specifications | Test: `CatalogApiIT.sqlInjectionAttemptsAreJustSearchText` |
| `LIKE` wildcard injection | `%`, `_` and `\` in search terms are escaped (`PlaceSpecifications`) | Test: `CatalogApiIT.likeWildcardsInSearchAreTreatedLiterally` |
| Cross-site scripting | React escaping; ESLint bans `dangerouslySetInnerHTML` and `innerHTML`; per-request CSP nonce with `'strict-dynamic'`; map markers contain only app-generated numbers | Test: `proxy.test.ts`; E2E: CSP and nonce on every script; lint in CI |
| Calendar (ICS) injection | RFC 5545 TEXT escaping, CR/LF removal, 75-octet line folding (`CalendarExporter`) | Test: `CalendarExporterTest` |
| Control characters in titles | `@Pattern("[^\\p{Cntrl}]*")` on titles | Test: `ItineraryApiIT.controlCharactersInTitlesAreRejected` |
| Log injection via request IDs | Only well-formed `X-Request-Id` values are reused; others are replaced | Test: `WebSecurityIT.wellFormedRequestIdsAreEchoedAndForgedOnesReplaced` |

### A04 Insecure design

| Control | Implementation | Evidence |
| --- | --- | --- |
| Threat model per milestone | [threat-model.md](threat-model.md) (STRIDE, residual risks) | Document |
| Bounded inputs | ≤ 50 stops, ≤ 14 days, 1–12 visits per day, budget ≤ ¥10,000,000, ≤ 100 itineraries per user, page size ≤ 50 | Test: `ItineraryApiIT.tripsLongerThanTwoWeeksAndUnknownPlacesAreRejected`, `beanValidationCoversSingleFields`, `crossFieldRulesAreValidated` |
| Bounded computation | Solver iteration caps and a 3 s wall-clock cap; the exponential exact solver is not reachable over HTTP | Test: `PlanningApiIT.exactStrategyIsNotExposedOverHttp`, `ItineraryOptimizerScenarioTest.exactSolverRefusesLargeProblems` |
| Suggestions cannot override the traveller | Score invariant in `CandidateScoring` | Test: `CandidateScoringTest` |
| No invented facts | Every leg and catalogue entry carries its source; missing transit data produces `NO_ROUTE` | Test: `PlanningApiIT.missingTransitDataIsReportedInsteadOfInvented`, `optimizedTokyoPlanRespectsHoursAndBudgetAndIsLabelledAsEstimate` |

### A05 Security misconfiguration

| Control | Implementation | Evidence |
| --- | --- | --- |
| API security headers | CSP `default-src 'none'`, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, COOP/CORP `same-origin`, `Permissions-Policy`, `Cache-Control: no-store` | Test: `WebSecurityIT.apiResponsesCarrySecurityHeaders` |
| Web security headers | Nonce-based CSP, `frame-ancestors 'none'`, `X-Frame-Options`, `nosniff`, `strict-origin-when-cross-origin`, `Permissions-Policy`, COOP, no `X-Powered-By` | E2E: "pages are served with a nonce-based Content-Security-Policy" |
| Minimal actuator | Only `health` (no details) and `info` are exposed | Test: `WebSecurityIT.actuatorExposesOnlyMinimalHealth`; Manual: `/actuator/env` and `/actuator/sbom` return 401 in the production profile |
| No internals in errors | `include-stacktrace: never`, catch-all handler, RFC 9457 bodies with codes only | Test: `WebSecurityIT.errorBodiesNeverContainInternals`, `ItineraryApiIT.malformedValuesAreRejectedWithoutLeakingParserDetails` |
| Secure defaults, explicit relaxations | `application.yml` is production-safe; `local`/`test` profiles relax only cookies, docs and demo data; API docs off in production | Manual: production profile returns 404 for `/v3/api-docs` and `/swagger-ui.html` |
| Hardened containers | Non-root users, read-only root filesystems, all capabilities dropped, `no-new-privileges`, only the edge port published on 127.0.0.1, internal database network | CI: `ci.yml` job `e2e` builds and runs the stack |

### A06 Vulnerable and outdated components

| Control | Implementation | Evidence |
| --- | --- | --- |
| Current, supported versions | Java 21 LTS, Spring Boot 4.1, Next.js 16, PostgreSQL 18 (ADR-0008) | Build files |
| Known-vulnerability scanning | `npm audit --omit=dev` (CI gate), OSV-Scanner over Maven and npm trees, CodeQL `security-extended` | Manual: `npm audit` reported 0 vulnerabilities in runtime dependencies on 2026-11-18; CI: `ci.yml`, `security.yml`, `codeql.yml` |
| Update flow | Dependabot for Maven, npm, Actions, Docker and Compose, grouped, 7-day cooldown | `.github/dependabot.yml` |
| Bill of materials | CycloneDX SBOM embedded in the API jar and kept as a CI artifact | Build output `META-INF/sbom/application.cdx.json` |

### A07 Identification and authentication failures

| Control | Implementation | Evidence |
| --- | --- | --- |
| Password policy | 12–128 characters, common-password list, must not contain the e-mail or its local part; reasons returned as codes, the password is never echoed | Test: `PasswordPolicyTest`, `AuthFlowIT.weakPasswordsAreRejectedWithReasonCodesButWithoutEchoingThePassword` |
| No user enumeration at login | Same 401 `INVALID_CREDENTIALS` for an unknown e-mail and a wrong password; Spring's provider hashes a dummy password for unknown users | Test: `AuthFlowIT.wrongPasswordAndUnknownEmailGiveTheSameAnswer` |
| Online guessing | Per-IP and per-account token buckets, checked **before** the password is verified → 429 with `Retry-After` | Test: `AuthFlowIT.repeatedFailedLoginsForOneAccountAreRateLimited` (the correct password is refused once the budget is spent) |
| Session fixation | New session ID and new CSRF token on login and registration | Test: `AuthFlowIT.sessionFixationIsPrevented`, `loginIsCaseInsensitiveOnEmailAndRotatesTheSessionId` |
| Logout | Invalidates the session and deletes the cookie; requires CSRF | Test: `AuthFlowIT.registerSignsInAndLogoutSignsOut` |

### A08 Software and data integrity failures

| Control | Implementation | Evidence |
| --- | --- | --- |
| Reproducible dependency resolution | `package-lock.json` with `npm ci`; install scripts disabled (`.npmrc`); Maven wrapper pinned with a SHA-256 checksum | Build files |
| Pinned build inputs | GitHub Actions pinned to commit SHAs; base images pinned by digest | Workflow and Docker files |
| API contract integrity | `OpenApiContractIT` + generated frontend types checked in CI | Test and CI |
| Plan integrity | Plans validated by the independent `PlanValidator` before storage; stored with input fingerprint and algorithm version | Test: `PlanningServiceTest.anInvalidPlanFromTheOptimizerIsRefusedAndNotStored`, `OptimizerPropertyTest`, `PlanValidatorTest`, `PlanningApiIT.storedPlanBecomesStaleWhenTheItineraryChanges` |
| Secrets never in the repository | `.env` ignored, `.env.example` empty, demo password only from the environment; gitleaks over the full history | CI: `security.yml` |

### A09 Security logging and monitoring failures

| Control | Implementation | Evidence |
| --- | --- | --- |
| Security events | Logger `security`: `login.succeeded`, `login.failed` (account as a short SHA-256 fingerprint, never the e-mail), `account.registered`, `itinerary.access-denied`, `csrf.rejected`, `access.denied` | Code review (no automated log assertions yet — roadmap) |
| Correlation | `X-Request-Id` in every response, in the MDC and in problem bodies | Test: `WebSecurityIT.wellFormedRequestIdsAreEchoedAndForgedOnesReplaced` |
| No secrets in logs | Passwords, cookies and tokens are never logged; DTO `toString` hides passwords | Code review |
| Machine-readable logs | ECS JSON in the `prod` profile | Manual: production profile run on 2026-11-18 |

### A10 Server-side request forgery

The server never fetches a URL supplied by a user. Travel data comes from a bundled file; future
providers will be configured endpoints, not user input. Map tiles are fetched by the browser, not the
server. Not applicable by design.

## OWASP API Security Top 10 (2023) — additional points

| Risk | Control | Evidence |
| --- | --- | --- |
| API1 Broken object-level authorisation | See A01 | `OwnershipSecurityIT` |
| API3 Broken object-property-level authorisation | Unknown request fields rejected; responses are explicit DTOs (no password hash, no other users' data) | `AuthFlowIT.massAssignmentOfRoleIsRejected` |
| API4 Unrestricted resource consumption | 64 KB body limit before parsing, header and form limits, connection and query timeouts, rate limits, page size ≤ 50, solver caps | `WebSecurityIT.oversizedBodiesAreRejectedBeforeParsing`, A04 |
| API5 Broken function-level authorisation | Admin routes in the filter chain and in method security | A01 |
| API8 Security misconfiguration | See A05 | `WebSecurityIT` |
| API9 Improper inventory management | Versioned paths, committed OpenAPI contract, docs disabled in production | `OpenApiContractIT` |

## Rate limits

| Bucket | Key | Default | Where |
| --- | --- | --- | --- |
| API | client IP | 300 / min | `RateLimitFilter` (before authentication) |
| Login | client IP | 20 / min | `RateLimitFilter` |
| Login | account fingerprint | 5 / 15 min | `AuthController` |
| Registration | client IP | 10 / hour | `RateLimitFilter` |
| Planning | user ID | 12 / min | `PlanningService` |

Buckets live in Caffeine caches bounded to 100,000 keys with a 2-hour idle expiry, so a flood of
distinct keys cannot exhaust memory. The client IP is taken from `X-Forwarded-For` **only** when the
request comes from a private-network proxy (Tomcat `RemoteIpValve`); the edge proxy must overwrite that
header ([deployment.md](deployment.md), ADR-0010).
