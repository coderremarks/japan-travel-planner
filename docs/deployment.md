# Running and deploying / 起動とデプロイ

Three ways to run the application, from the lightest to the most production-like. None of them needs
a paid service or an API key.

## 1. Local development without Docker

Requirements: **JDK 21** and **Node.js 24**. PostgreSQL does not need to be installed — the
development launcher starts real PostgreSQL 18 binaries (embedded) or a Testcontainers container if
Docker happens to be available.

```bash
# Terminal 1 — API on http://localhost:8080 (profile "local": Swagger UI, demo data, DEBUG logs)
cd backend
./mvnw -pl app -am install -DskipTests    # first time only: builds the optimizer module
./mvnw -pl app spring-boot:test-run

# Terminal 2 — web app on http://localhost:3000 (proxies /api to the API)
cd frontend
npm ci
npm run dev
```

Optional demo account: set `TABIJI_DEMO_PASSWORD` in terminal 1 before starting the API (12–128
characters, not containing "demo"). The API then creates `demo@tabiji.example` with a sample Tokyo
trip. Without it, register a new account in the UI.

Swagger UI: <http://localhost:8080/swagger-ui.html>. On Windows use `mvnw.cmd` instead of `./mvnw`.

## 2. Production-like stack with Docker Compose

```bash
cp .env.example .env        # set POSTGRES_PASSWORD (e.g. openssl rand -hex 24); TABIJI_DEMO_PASSWORD is optional
docker compose up --build --wait
# open http://localhost:3000
docker compose logs -f api  # ECS JSON logs
docker compose down         # add --volumes to delete the database
```

| Service | Image | Role |
| --- | --- | --- |
| `edge` | `caddy:2.11.9-alpine` (digest-pinned) | The only published port, bound to `127.0.0.1:3000`. Routes `/api/*` to the API and everything else to the web app; replaces client-supplied `X-Forwarded-*` headers. |
| `web` | built from `frontend/Dockerfile` | Next.js standalone server, non-root, read-only root filesystem |
| `api` | built from `backend/Dockerfile` | Spring Boot on a JRE, non-root, read-only root filesystem, `prod` profile |
| `db` | `postgres:18.7-alpine3.24` (digest-pinned) | Data in the `db-data` volume, on an internal network without internet access |

The stack serves **plain HTTP on localhost**. Browsers only keep `Secure` / `__Host-` cookies over
HTTPS (some make an exception for localhost), so `compose.yaml` switches the API to the relaxed session
cookie of the local profile. That override is for this local stack only; see §3.

> **Status:** the Dockerfiles and `compose.yaml` could not be executed on the development machine
> (no Docker). The packaged jar was run with the production profile and the standalone web build was
> run and tested end to end, both outside containers. The CI job `e2e` builds both images and runs the
> Playwright suite against this exact stack once the repository is pushed.

## 3. Production deployment checklist

The application is a single API instance plus a web server behind a reverse proxy. The following are
**requirements**, not suggestions:

**TLS and the edge**
- Terminate TLS at the edge (load balancer or reverse proxy) and redirect HTTP to HTTPS.
- Set `Strict-Transport-Security: max-age=31536000; includeSubDomains` at the edge.
- The edge must **overwrite** `X-Forwarded-For` and `X-Forwarded-Proto` with what it observed
  (Caddy does this by default; for nginx use `proxy_set_header X-Forwarded-For $remote_addr;`).
  Next.js forwards those headers unchanged, and the API uses `X-Forwarded-For` for per-IP rate
  limits, so a web server reachable directly from the internet would let clients choose the IP the
  limits see (ADR-0010).
- Expose only the edge. The API and the web server must not be reachable from the internet.
- The API trusts forwarded headers only from private-network peers (Tomcat `RemoteIpValve` defaults:
  10/8, 172.16/12, 192.168/16, 127/8 …). If the edge has a different address, set
  `server.tomcat.remoteip.internal-proxies` to exactly that address.

**API configuration**
- Keep the default session cookie (`__Host-TABIJI_SESSION`, `Secure`). Do not copy the compose
  overrides `SERVER_SERVLET_SESSION_COOKIE_NAME` / `_SECURE`.
- Run with `SPRING_PROFILES_ACTIVE=prod` (the image default) for JSON logs.
- Leave `app.demo.enabled` at its default `false`; never set `TABIJI_DEMO_PASSWORD` in production.
- Provide secrets as environment variables or through the platform's secret store, never in files in
  the image or the repository.
- API documentation stays disabled (the default); actuator exposes only `health` and `info`.

**Database**
- Use a dedicated PostgreSQL role for the application. Flyway migrates on start-up, which needs DDL
  rights; for a stricter setup run migrations in a deployment step with a migration role and give the
  runtime role only `SELECT/INSERT/UPDATE/DELETE`.
- Enable TLS to the database if it is on another host (`?sslmode=verify-full` in `DATABASE_URL`).
- Back up the database; itineraries are personal data (when and where someone travels).

**Operations**
- Health probes: `/actuator/health/liveness` and `/actuator/health/readiness` (port 8080).
- Logs: ECS JSON on stdout, including `requestId`; the `security` logger carries security events.
- Graceful shutdown is enabled; the JVM uses at most 75 % of the container's memory.

**Scaling beyond one API instance** (not needed for the demo): sessions and rate-limit buckets are
in memory. Before running several instances, move sessions to Spring Session JDBC and the buckets to a
shared store, otherwise users are logged out on failover and limits multiply by the instance count.

## Configuration reference

API (Spring Boot; any property can also be set as an environment variable using relaxed binding,
e.g. `app.rate-limit.plan-per-user-per-minute` → `APP_RATELIMIT_PLANPERUSERPERMINUTE`):

| Variable | Default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/tabiji` | JDBC URL |
| `DATABASE_USERNAME` | `tabiji` | Database role |
| `DATABASE_PASSWORD` | *(empty)* | Database password — always set it |
| `DATABASE_POOL_SIZE` | `10` | Hikari pool size |
| `PORT` | `8080` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | `prod` in the image | `prod`, `local` or `test` |
| `APP_DEMO_ENABLED` | `false` (`true` in `local`) | Create the demo account at start-up |
| `TABIJI_DEMO_PASSWORD` | *(empty)* | Demo account password; demo data is skipped when empty |
| `APP_SECURITY_CORSALLOWEDORIGINS` | *(none)* | Comma-separated origins, only for a deliberately separate frontend host |
| `APP_PLANNING_MAXSUGGESTIONS` | `12` | Suggested places per plan (0–50) |
| `APP_RATELIMIT_*` | see [api.md](api.md#conventions) | Rate-limit budgets |

Web (Next.js):

| Variable | When | Purpose |
| --- | --- | --- |
| `API_ORIGIN` | **build time** | Target of the `/api` rewrite (rewrites are compiled into the build); `http://api:8080` in the image |
| `PORT`, `HOSTNAME` | run time | Listen address (`3000`, `0.0.0.0` in the image) |
| `NEXT_TELEMETRY_DISABLED` | build and run time | `1` disables Next.js telemetry (set in the image and in CI) |

Compose (`.env`): `POSTGRES_PASSWORD` (required), `TABIJI_DEMO_PASSWORD` (optional), `TABIJI_PORT`
(optional, default 3000).
