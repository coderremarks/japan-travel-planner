import { test as base, expect, type Page } from "@playwright/test";
import { TEST_PASSWORD, uniqueEmail } from "./fixtures";

/**
 * Regenerates the README screenshots (docs/screenshots). Not part of the regular suite:
 *   SCREENSHOTS=1 npx playwright test e2e/screenshots.spec.ts
 * Unlike the tests, this loads a handful of real OpenStreetMap tiles (with attribution).
 */
const test = base.extend({});
test.skip(!process.env.SCREENSHOTS, "set SCREENSHOTS=1 to regenerate the README screenshots");
test.use({ viewport: { width: 1440, height: 1000 } });

const OUT = "../docs/screenshots";

async function buildTrip(page: Page, locale: "en" | "ja") {
  await page.goto(`/${locale}/register`);
  await page.locator("#displayName").fill(locale === "ja" ? "山田 花子" : "Alex Traveller");
  await page.locator("#email").fill(uniqueEmail("shots"));
  await page.locator("#password").fill(TEST_PASSWORD);
  await page.locator("form button[type=submit]").click();
  await expect(page).toHaveURL(/\/itineraries$/);
  await page.locator("main button").first().click();
  await expect(page).toHaveURL(/\/itineraries\/[0-9a-f-]{36}$/);
  const search = page.locator("#place-query");
  for (const name of [
    "senso",
    "skytree",
    "tokyo national",
    "ueno park",
    "ameya",
    "akihabara",
    "meiji",
    "shibuya sky",
  ]) {
    await search.fill(name);
    await page
      .locator("main li button:not([disabled])")
      .filter({ hasText: /Add|追加/ })
      .first()
      .click();
  }
  await page
    .locator(
      'input[type="checkbox"][aria-label*="Tokyo National Museum"], input[type="checkbox"][aria-label*="東京国立博物館"]',
    )
    .check();
  await page.locator("#maxVisits").fill("4");
  await page.getByRole("button", { name: /Optimize|最適化する/ }).click();
  await expect(page.getByText(/Everything fits|Some optional|すべての場所|一部の任意/)).toBeVisible({
    timeout: 30_000,
  });
  await page.waitForTimeout(1500); // let map tiles settle
}

test("landing page", async ({ page }) => {
  await page.goto("/en");
  await page.screenshot({ path: `${OUT}/landing-en.png` });
});

test("optimized plan (English)", async ({ page }) => {
  await buildTrip(page, "en");
  await page.screenshot({ path: `${OUT}/planner-en.png`, fullPage: true });
});

test("optimized plan (Japanese) and mobile layout", async ({ page }) => {
  await buildTrip(page, "ja");
  await page.screenshot({ path: `${OUT}/planner-ja.png` });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${OUT}/planner-ja-mobile.png` });
});
