# API / API 仕様

REST over JSON, versioned under `/api/v1`. The machine-readable contract is
[`docs/api/openapi.json`](api/openapi.json) (OpenAPI 3.1), generated from the code and committed:
`OpenApiContractIT` fails the build when code and contract drift apart, and the frontend's TypeScript
types are generated from the same file. Locally, the interactive Swagger UI is available at
<http://localhost:8080/swagger-ui.html> when the API runs with the `local` profile; it is disabled in
production.

## Authentication model

Server-side sessions with an HttpOnly cookie and CSRF tokens (ADR-0003) — no tokens in JavaScript
storage.

| Item | Value |
| --- | --- |
| Session cookie | `__Host-TABIJI_SESSION` in production (`Secure`, `HttpOnly`, `SameSite=Lax`, `Path=/`, 30-minute idle timeout); `TABIJI_SESSION` without `Secure` for plain-HTTP localhost runs |
| CSRF | Double submit: the API sets the readable `XSRF-TOKEN` cookie; every `POST`, `PUT` and `DELETE` must echo it in the `X-XSRF-TOKEN` header — including login and registration |
| On sign-in | The session ID and the CSRF token are rotated (session fixation protection) |
| Passwords | 12–128 characters, not a common password, must not contain the e-mail address; stored as Argon2id |

```bash
# A complete session with curl against a local API (cookie jar in jar.txt)
curl -s -c jar.txt http://localhost:8080/api/v1/auth/csrf
TOKEN=$(awk '$6 == "XSRF-TOKEN" { print $7 }' jar.txt)
curl -s -b jar.txt -c jar.txt -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $TOKEN" \
  -d '{"email":"you@example.com","password":"a long passphrase you made up","displayName":"You","preferredLocale":"en"}' \
  http://localhost:8080/api/v1/auth/register
TOKEN=$(awk '$6 == "XSRF-TOKEN" { print $7 }' jar.txt)   # rotated on sign-in
curl -s -b jar.txt http://localhost:8080/api/v1/itineraries
```

## Endpoints

| Method | Path | Auth | Success | Purpose |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/auth/csrf` | – | 200 | Issue the CSRF cookie |
| POST | `/api/v1/auth/register` | – | 201 | Create an account and sign in |
| POST | `/api/v1/auth/login` | – | 200 | Sign in |
| POST | `/api/v1/auth/logout` | session | 204 | Sign out (Spring Security's logout filter) |
| GET | `/api/v1/auth/me` | – | 200 | Current user, or 401 |
| GET | `/api/v1/areas` | – | 200 | Destination areas |
| GET | `/api/v1/base-locations?area=` | – | 200 | Places to stay; each trip day starts and ends there |
| GET | `/api/v1/places?area=&category=&q=&page=&size=` | – | 200 | Search the catalogue (Japanese and English names) |
| GET | `/api/v1/places/{id}` | – | 200 | Place details with opening rules and data provenance |
| GET | `/api/v1/itineraries?page=&size=` | user | 200 | My itineraries, most recently updated first |
| POST | `/api/v1/itineraries` | user | 201 | Create (`Location` and `ETag` headers) |
| GET | `/api/v1/itineraries/{id}` | owner | 200 | Read (`ETag`) |
| PUT | `/api/v1/itineraries/{id}` | owner | 200 | Replace the editable fields; requires `If-Match` |
| DELETE | `/api/v1/itineraries/{id}` | owner | 204 | Delete with its plan |
| POST | `/api/v1/itineraries/{id}/duplicate?lang=en\|ja` | owner | 201 | Copy without the plan; title suffix in the given language |
| POST | `/api/v1/itineraries/{id}/plan` | owner | 200 | Generate a plan: `{"strategy": "OPTIMIZE" \| "KEEP_ORDER"}` |
| GET | `/api/v1/itineraries/{id}/plan` | owner | 200 | Latest plan; `stale: true` if the trip changed since |
| GET | `/api/v1/itineraries/{id}/calendar.ics?lang=en\|ja` | owner | 200 | iCalendar export of the latest plan |
| GET | `/api/v1/itineraries/{id}/export.json` | owner | 200 | Itinerary and plan as a JSON download |
| GET | `/api/v1/admin/stats` | ADMIN | 200 | Aggregate counts only, no personal data |

"owner" means: signed in **and** the itinerary belongs to you. Anything else — another user's
itinerary or an ID that does not exist — returns the same `404 ITINERARY_NOT_FOUND`
(see `OwnershipSecurityIT`). Every route not listed here is denied.

## Conventions

**Requests.** `Content-Type: application/json`, at most 64 KB per body (`413 PAYLOAD_TOO_LARGE`).
Unknown fields are rejected, not ignored (`400 UNKNOWN_FIELD`): sending `ownerId`, `role` or `version`
cannot change anything (mass-assignment protection). Validation errors list every offending field:

```json
{
  "type": "urn:tabiji:problem:validation-failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "The request contains invalid values.",
  "instance": "/api/v1/itineraries",
  "code": "VALIDATION_FAILED",
  "requestId": "6f1c…",
  "errors": [{ "field": "endDate", "code": "TRIP_TOO_LONG" }]
}
```

**Errors** are RFC 9457 problem details (`application/problem+json`) with a stable machine-readable
`code`, which the frontend translates; they never contain stack traces, SQL or framework messages.

| Status | Codes |
| --- | --- |
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `UNKNOWN_FIELD`, `PASSWORD_TOO_WEAK` |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS` (same answer for a wrong password and an unknown e-mail) |
| 403 | `FORBIDDEN`, `CSRF_TOKEN_INVALID` |
| 404 | `NOT_FOUND`, `ITINERARY_NOT_FOUND`, `PLACE_NOT_FOUND`, `PLAN_NOT_FOUND` |
| 405 / 406 / 415 | `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE` |
| 409 | `EMAIL_ALREADY_REGISTERED`, `CONCURRENT_MODIFICATION`, `CONFLICT` |
| 412 / 428 | `VERSION_MISMATCH` (stale `If-Match`), `VERSION_REQUIRED` (missing `If-Match`) |
| 413 | `PAYLOAD_TOO_LARGE` |
| 422 | `NOTHING_TO_PLAN` (an itinerary without places) |
| 429 | `TOO_MANY_REQUESTS`, with `Retry-After` in seconds |
| 500 | `INTERNAL_ERROR` (details only in the server log, linked by `requestId`) |

**Request IDs.** Every response carries `X-Request-Id`. A well-formed ID sent by the caller is reused
(useful for tracing through the proxy); a malformed one is replaced, so log injection through this
header is not possible.

**Optimistic concurrency.** `GET` and `PUT` return the itinerary's version as an `ETag`. `PUT` must
send it back in `If-Match`; if someone else saved in between, the answer is `412` and nothing is
overwritten. The web app then offers to reload.

**Pagination.** `page` (0-based) and `size` (1–50, default 20); responses have the shape
`{ content, page, size, totalElements, totalPages }`.

**Rate limits** (per instance, token buckets; values from `application.yml`):

| Limit | Default |
| --- | --- |
| Any API request, per client IP | 300 per minute |
| Login attempts, per client IP | 20 per minute |
| Login attempts, per account (successful or not) | 5 per 15 minutes, checked before the password is verified |
| Registrations, per client IP | 10 per hour |
| Plan generation, per user | 12 per minute |

**Plans.** A plan document contains the status, a summary (totals), the days with times and legs
(mode, minutes, fare, distance, source, `estimated: true`), unscheduled places with a reason code,
hints, notices (for example "holiday data unavailable for this year"), the comparison with *Keep my
order*, and solver statistics, plus the algorithm version and generation time.
Every travel time and fare is an estimate from the sample model and is labelled as such.

**Headers.** API responses carry `Cache-Control: no-store`, a `default-src 'none'` CSP,
`X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
`Cross-Origin-Opener-Policy` and `Cross-Origin-Resource-Policy: same-origin`, and a restrictive
`Permissions-Policy`. No CORS origins are allowed unless configured explicitly.
