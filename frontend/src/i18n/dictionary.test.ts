import { describe, expect, it } from "vitest";
import en from "./messages/en.json";
import ja from "./messages/ja.json";
import { createTranslator, interpolate } from "./dictionary";

function keys(value: unknown, prefix = ""): string[] {
  return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
    typeof child === "string" ? [`${prefix}${key}`] : keys(child, `${prefix}${key}.`),
  );
}

function at(catalogue: unknown, key: string): string {
  return key.split(".").reduce<unknown>((node, part) => (node as Record<string, unknown>)[part], catalogue) as string;
}

describe("message catalogues", () => {
  it("Japanese and English define exactly the same keys", () => {
    expect(keys(ja).sort()).toEqual(keys(en).sort());
  });

  it("every placeholder used in English also exists in Japanese", () => {
    const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
    for (const key of keys(en)) {
      expect(placeholders(at(ja, key)), key).toEqual(placeholders(at(en, key)));
    }
  });
});

describe("translator", () => {
  it("interpolates named values and leaves unknown placeholders visible", () => {
    expect(interpolate("Day {number} of {total}", { number: 2 })).toBe("Day 2 of {total}");
  });

  it("selects plural forms by locale", () => {
    expect(createTranslator("en").plural("trips.places", 1)).toBe("1 place");
    expect(createTranslator("en").plural("trips.places", 3)).toBe("3 places");
    expect(createTranslator("ja").plural("trips.places", 3)).toBe("3か所");
  });

  it("translates API codes and falls back for unknown ones", () => {
    const { dynamic } = createTranslator("ja");
    expect(dynamic("errors.INVALID_CREDENTIALS", "x")).toBe("メールアドレスまたはパスワードが正しくありません。");
    expect(dynamic("errors.SOMETHING_NEW", "fallback")).toBe("fallback");
  });
});
