# ADR-0004: Same-origin API through the Next.js proxy

- Status: Accepted (2026-11-18)

## Context

The UI (Next.js, port 3000) and the API (Spring Boot, port 8080) are different origins. Calling the API
directly from the browser requires credentialed CORS and cross-origin cookie handling, and it exposes the
API publicly.

## Decision

The browser only calls `/api/**` on its own origin; Next.js rewrites those requests to the API
(`API_ORIGIN`). The API keeps a strict CORS policy (no origins allowed unless explicitly configured) as
defence in depth.

## Consequences

- First-party cookies, no preflight requests, a simpler CSRF story, and the API can live on a private
  network.
- The API sees the proxy as the TCP peer; the client IP comes from `X-Forwarded-For`, which is trusted
  only from configured internal proxies. Deployments must not expose the API directly.
