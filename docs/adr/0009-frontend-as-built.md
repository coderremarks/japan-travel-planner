# ADR-0009: Dependency-free i18n, TypeScript 5.9, no Cache Components

- Status: Accepted (2026-11-18)
- Amends: ADR-0008 (TypeScript version) and the frontend section of the project plan (next-intl)

## Context

The plan named next-intl for internationalisation and ADR-0008 named TypeScript 6.0. Implementing the
frontend surfaced three facts:

1. The app has exactly two locales and needs a small feature set: interpolation, plurals, and
   locale-aware formatting of yen, durations and dates. The plan also requires that a missing
   Japanese string can never ship.
2. `openapi-typescript` 7.13, which generates the client types from the API contract, declares a
   peer dependency on TypeScript `^5.x`. TypeScript 6 would need `--legacy-peer-deps` and an
   unsupported combination in the toolchain that guards the contract.
3. Every page carries a per-request CSP nonce (`proxy.ts`) and, after sign-in, personal data.
   Next.js 16's Cache Components (and static prerendering in general) would serve cached HTML whose
   nonce no longer matches the response header.

## Decision

- **A small, dependency-free i18n layer** (`src/i18n`): JSON catalogues, keys typed from the English
  catalogue, the Japanese catalogue type-checked against it (a missing or extra key is a compile
  error), plurals via `Intl.PluralRules`, formatting via `Intl.NumberFormat` and `Intl.DateTimeFormat`,
  locale routing in `proxy.ts`. Unit tests check catalogue parity and formatting.
- **TypeScript 5.9.3**, the newest release the contract tooling supports. Revisit when
  `openapi-typescript` accepts TypeScript 6 or 7.
- **Cache Components off; pages render dynamically per request**, as the Next.js CSP guide requires
  for nonce-based policies.

## Consequences

- One dependency fewer in the supply chain and full type safety for message keys; the cost is under
  200 lines of code (configuration, dictionary, formatting, provider) that we own and test.
- Adding a locale means adding a catalogue and a `LOCALES` entry; message formats are simpler than
  ICU MessageFormat (no `select`), which the two current locales do not need.
- TypeScript stays one major version behind the latest; Dependabot will propose the upgrade when the
  peer range allows it.
- No static HTML caching. For this application (authenticated, personal pages) that costs nothing;
  a marketing site would make a different trade-off.
