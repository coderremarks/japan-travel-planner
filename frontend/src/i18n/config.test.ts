import { describe, expect, it } from "vitest";
import { negotiateLocale } from "./config";

describe("negotiateLocale", () => {
  it("prefers the highest quality supported language", () => {
    expect(negotiateLocale("ja-JP,ja;q=0.9,en;q=0.8", "en")).toBe("ja");
    expect(negotiateLocale("fr-FR,fr;q=0.9,en;q=0.5,ja;q=0.4", "ja")).toBe("en");
  });

  it("respects explicit quality values over order", () => {
    expect(negotiateLocale("en;q=0.3,ja;q=0.9", "en")).toBe("ja");
  });

  it("falls back for missing, unsupported or refused languages", () => {
    expect(negotiateLocale(null, "en")).toBe("en");
    expect(negotiateLocale("de,fr;q=0.8", "ja")).toBe("ja");
    expect(negotiateLocale("ja;q=0,en", "ja")).toBe("en");
    expect(negotiateLocale("garbage;;q=abc", "en")).toBe("en");
  });
});
