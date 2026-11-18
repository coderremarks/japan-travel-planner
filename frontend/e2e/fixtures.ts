import AxeBuilder from "@axe-core/playwright";
import { test as base, expect, type Page } from "@playwright/test";

/** Every test starts with map tiles blocked: no traffic to OpenStreetMap from CI. */
export const test = base.extend({
  page: async ({ page }, use) => {
    await page.route("https://tile.openstreetmap.org/**", (route) => route.abort());
    await use(page);
  },
});

export { expect };

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.test`;
}

/** A passphrase used only for throwaway accounts created by the tests. */
export const TEST_PASSWORD = "e2e correct horse battery staple";

/** WCAG 2.1 A/AA checks; serious and critical findings fail the test. */
export async function expectNoSeriousA11yViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    // Third-party map tiles and controls are outside our control; everything else is checked.
    .exclude(".leaflet-container")
    .analyze();
  const serious = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
  expect(serious.map((v) => `${v.id}: ${v.help} (${v.nodes.length})`)).toEqual([]);
}

export async function register(page: Page, locale: "en" | "ja", name = "E2E Traveller") {
  const email = uniqueEmail("e2e");
  await page.goto(`/${locale}/register`);
  await page.locator("#displayName").fill(name);
  await page.locator("#email").fill(email);
  await page.locator("#password").fill(TEST_PASSWORD);
  await page.locator("form button[type=submit]").click();
  await expect(page).toHaveURL(/\/itineraries$/);
  return email;
}
