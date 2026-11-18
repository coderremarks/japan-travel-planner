import { expect, expectNoSeriousA11yViolations, register, test } from "./fixtures";

test.describe("trip planning", () => {
  test("a traveller registers, builds a Tokyo trip and optimizes it", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveURL(/\/en$/);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Plan a smarter trip through Japan");
    await expectNoSeriousA11yViolations(page);

    await register(page, "en");
    await expect(page.getByText("No trips yet")).toBeVisible();
    await expectNoSeriousA11yViolations(page);

    await page.getByRole("button", { name: "New trip" }).first().click();
    await expect(page).toHaveURL(/\/en\/itineraries\/[0-9a-f-]{36}$/);
    await expect(page.getByRole("heading", { name: "Trip settings" })).toBeVisible();

    const search = page.getByLabel("Search by name");
    for (const name of ["Senso-ji Temple", "Ueno Park", "Tokyo National Museum"]) {
      await search.fill(name);
      await page.getByRole("button", { name: `Add: ${name}` }).click();
    }
    const stops = page.getByRole("list", { name: "Places" });
    await expect(stops.getByRole("listitem")).toHaveCount(3);
    await page.getByLabel("Must-visit: Tokyo National Museum").check();
    await page.getByRole("button", { name: "Move up: Tokyo National Museum" }).click();
    await expect(stops.getByRole("listitem").nth(1)).toContainText("Tokyo National Museum");

    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByText("Saved", { exact: true })).toBeVisible();

    await page.getByRole("button", { name: /Optimize/ }).click();
    await expect(page.getByText("Everything fits")).toBeVisible();
    await expect(page.getByText("Day 1 · ", { exact: false })).toBeVisible();
    await expect(page.getByText("Tokyo National Museum").last()).toBeVisible();
    await expect(page.getByText(/Travel times and fares are estimates/)).toBeVisible();
    await expect(page.getByText("estimate").first()).toBeVisible();
    await expect(page.getByRole("link", { name: /Calendar \(\.ics\)/ })).toHaveAttribute(
      "href",
      /\/calendar\.ics\?lang=en$/,
    );
    await expectNoSeriousA11yViolations(page);

    // The calendar export is a real file produced by the API through the same-origin proxy.
    const ics = await page.request.get(
      (await page.getByRole("link", { name: /Calendar \(\.ics\)/ }).getAttribute("href")) ?? "",
    );
    expect(ics.status()).toBe(200);
    expect(await ics.text()).toContain("BEGIN:VCALENDAR");

    // Changing the trip marks the stored plan as out of date.
    await page.getByLabel("Budget (yen)").fill("20000");
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByText(/Your trip changed since this plan was made/)).toBeVisible();
  });

  test("the whole interface works in Japanese", async ({ page }) => {
    await page.goto("/ja");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("日本の旅を、もっと賢く計画しよう");
    await register(page, "ja", "テスト旅行者");
    await expect(page.getByText("まだ旅行がありません")).toBeVisible();
    await page.getByRole("button", { name: "新しい旅行" }).first().click();
    await expect(page.getByRole("heading", { name: "旅行の設定" })).toBeVisible();
    await page.getByLabel("名前で検索").fill("浅草寺");
    await page.getByRole("button", { name: "追加: 浅草寺" }).click();
    await page.getByRole("button", { name: "変更を保存" }).click();
    await page.getByRole("button", { name: /最適化する/ }).click();
    await expect(page.getByText("すべての場所が入りました")).toBeVisible();
    await expect(page.getByText("1日目", { exact: false })).toBeVisible();
    await expectNoSeriousA11yViolations(page);
  });

  test("a traveller duplicates a trip and deletes the original", async ({ page }) => {
    await register(page, "en");
    await page.getByRole("button", { name: "New trip" }).first().click();
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("My Japan trip");

    await page.goto("/en/itineraries");
    const trips = page.getByRole("article");
    await expect(trips).toHaveCount(1);

    await page.getByRole("button", { name: "Duplicate: My Japan trip", exact: true }).click();
    await expect(trips).toHaveCount(2);
    await expect(page.getByRole("link", { name: "My Japan trip (copy)", exact: true })).toBeVisible();
    await expectNoSeriousA11yViolations(page);

    page.once("dialog", (dialog) => void dialog.accept());
    await page.getByRole("button", { name: "Delete: My Japan trip", exact: true }).click();
    await expect(trips).toHaveCount(1);
    await expect(trips.getByRole("heading")).toHaveText("My Japan trip (copy)");
  });

  test("signed-out visitors are sent to sign in and back", async ({ page }) => {
    await page.goto("/en/itineraries");
    await expect(page).toHaveURL(/\/en\/login\?next=%2Fen%2Fitineraries$/);
    await expect(page.getByText("Please sign in to continue.")).toBeVisible();
    await expectNoSeriousA11yViolations(page);
  });

  test("pages are served with a nonce-based Content-Security-Policy", async ({ page }) => {
    const response = await page.goto("/en");
    const csp = response?.headers()["content-security-policy"] ?? "";
    expect(csp).toMatch(/script-src 'self' 'nonce-[A-Za-z0-9+/=]+' 'strict-dynamic'/);
    expect(csp).toContain("frame-ancestors 'none'");
    expect(response?.headers()["x-frame-options"]).toBe("DENY");
    expect(response?.headers()["x-powered-by"]).toBeUndefined();
    const nonce = /'nonce-([^']+)'/.exec(csp)?.[1];
    const scriptNonces = await page
      .locator("script[nonce]")
      .evaluateAll((nodes) => nodes.map((node) => (node as HTMLScriptElement).nonce));
    expect(scriptNonces.length).toBeGreaterThan(0);
    expect(scriptNonces.every((value) => value === nonce)).toBe(true);
  });
});
