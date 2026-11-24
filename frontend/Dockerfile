# Container image of the Next.js web app (standalone output).
#
#   docker build -t tabiji-web --build-arg API_ORIGIN=http://api:8080 ./frontend
#
# Lint, type checks and tests run in CI (npm run verify) before an image is built. Base images are
# pinned by digest; Dependabot proposes updates.

FROM node:26.13.1-alpine3.24@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS base
ENV NEXT_TELEMETRY_DISABLED=1

# ---- Dependencies: exactly the lockfile, install scripts disabled (.npmrc) ---------------------------
FROM base AS deps
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

# ---- Build ------------------------------------------------------------------------------------------
FROM base AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Rewrites are compiled into the build output, so the API origin is fixed when the image is built.
# Behind the edge proxy (compose.yaml) the browser's /api calls go straight to the API anyway.
ARG API_ORIGIN=http://api:8080
RUN API_ORIGIN="${API_ORIGIN}" npm run build

# ---- Runtime ----------------------------------------------------------------------------------------
FROM base AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    HOSTNAME=0.0.0.0 \
    PORT=3000

# Only the traced server bundle and static assets; no sources, no dev dependencies. Files stay
# owned by root; the server may write only to its cache directory.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
RUN mkdir -p .next/cache && chown node:node .next/cache

USER node
EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:3000/en || exit 1

CMD ["node", "server.js"]
