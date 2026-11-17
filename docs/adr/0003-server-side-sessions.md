# ADR-0003: Server-side sessions + CSRF tokens instead of JWT in the browser

- Status: Accepted (2026-11-18)

## Context

The only client is our own browser application, served from the same site. Common alternatives are JWT
access tokens in `localStorage` (readable by any injected script, hard to revoke) or JWTs in cookies
(which then need CSRF protection anyway, plus a revocation story).

## Decision

Use Spring Security's standard server-side session: an opaque `HttpOnly`, `SameSite=Lax`, `Secure`
(outside local development) cookie; session ID rotation on login; 30-minute idle timeout; logout
invalidates the session. CSRF uses Spring Security's SPA support (token in a readable `XSRF-TOKEN`
cookie, echoed in the `X-XSRF-TOKEN` header, masked against BREACH). Passwords use Argon2id through
`DelegatingPasswordEncoder`.

## Consequences

- Instant revocation (logout, future "log out everywhere"); nothing secret is readable by JavaScript.
- Sessions are stateful: horizontal scaling needs Spring Session (JDBC/Redis). Documented as the
  scale-out path.
- Third-party API clients (e.g. a mobile app) would need a token-based flow (OAuth 2.1 / OIDC via Spring
  Authorization Server) — not needed for the MVP.
