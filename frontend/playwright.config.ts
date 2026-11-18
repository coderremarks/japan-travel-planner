import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against a running stack (API + web). Locally:
 *   backend:  ./mvnw -pl app -am spring-boot:test-run      (embedded PostgreSQL, port 8080)
 *   frontend: npm run build && npm start -- --port 3100
 * CI starts both from the built artifacts (see .github/workflows/ci.yml).
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3100",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"], locale: "en-US" } }],
});
