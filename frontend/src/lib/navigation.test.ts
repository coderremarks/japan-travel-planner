import { describe, expect, it } from "vitest";
import { safeRedirectPath, switchLocalePath } from "./navigation";

describe("safeRedirectPath (open-redirect protection)", () => {
  it("keeps same-origin, locale-prefixed paths", () => {
    expect(safeRedirectPath("/en/itineraries/42?x=1", "en")).toBe("/en/itineraries/42?x=1");
  });

  it.each([
    "https://evil.example/en/itineraries",
    "//evil.example/en/",
    "/\\evil.example",
    "/en\\..\\..\\evil",
    "javascript:alert(1)",
    "/ja/itineraries",
    "",
    null,
  ])("rejects %s", (candidate) => {
    expect(safeRedirectPath(candidate, "en")).toBe("/en/itineraries");
  });
});

describe("switchLocalePath", () => {
  it("swaps only the locale segment", () => {
    expect(switchLocalePath("/en/itineraries/42", "ja")).toBe("/ja/itineraries/42");
    expect(switchLocalePath("/ja", "en")).toBe("/en");
  });
});
