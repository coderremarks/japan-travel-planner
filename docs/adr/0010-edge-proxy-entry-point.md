# ADR-0010: An edge proxy is the only public entry point

- Status: Accepted (2026-11-18)
- Amends: ADR-0004 (same-origin API through the Next.js proxy)

## Context

ADR-0004 routes the browser's `/api/*` calls through Next.js so that the browser sees a single origin.
The API applies per-IP rate limits and takes the client address from `X-Forwarded-For`, trusting that
header only from private-network peers.

While preparing the container setup we read how Next.js 16 proxies a rewrite
(`next/dist/server/lib/router-utils/proxy-request.js`): the request headers are copied unchanged and
`X-Forwarded-For` is not appended. Consequences:

- If the Next.js server is reachable directly, a client can send any `X-Forwarded-For` value. The
  API sees a trusted (private) peer and believes the forged address, so a client can rotate its
  apparent IP and escape the per-IP limits on login, registration and the API in general.
- If the client sends no such header, all requests appear to come from the Next.js server, so every
  user shares one per-IP bucket.

The per-account login limit and the per-user planning limit are unaffected, because they are not
keyed by IP.

## Decision

- In every deployment, a reverse proxy at the edge is the **only** public entry point. It terminates
  TLS, **overwrites** `X-Forwarded-For` / `-Proto` / `-Host` with what it observed, routes `/api/*`
  directly to the API and everything else to Next.js.
- `compose.yaml` ships such an edge (Caddy, whose default is to ignore forwarded headers from
  untrusted clients), bound to `127.0.0.1`; the API and the web server publish no ports.
- The Next.js rewrite stays as the development convenience it is (`npm run dev` against a local API),
  and the deployment guide states the requirement explicitly.
- Related: the web app adds the CSP directive `upgrade-insecure-requests` only when the page was
  served over HTTPS, so the same build works on the plain-HTTP local stack and behind TLS.

## Consequences

- Per-IP limits see real client addresses in the compose stack and in any deployment that follows
  the guide; the browser still sees a single origin (ADR-0004's benefits remain).
- API calls skip a hop through Node.js in deployment, which removes load from the web server.
- One more component to configure in a deployment. Misconfiguring it (exposing Next.js directly)
  silently weakens rate limiting, which is why the requirement is in the deployment checklist and the
  threat model's residual risks.
