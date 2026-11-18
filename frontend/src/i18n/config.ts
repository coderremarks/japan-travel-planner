export const LOCALES = ["en", "ja"] as const;
export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "en";
/** Remembers an explicit language choice so it wins over Accept-Language on the next visit. */
export const LOCALE_COOKIE = "TABIJI_LOCALE";

export function isLocale(value: string | undefined | null): value is Locale {
  return value === "en" || value === "ja";
}

/**
 * Picks the supported locale with the highest quality value in an Accept-Language header
 * (RFC 9110 §12.5.4), e.g. `ja-JP,ja;q=0.9,en;q=0.8` → `ja`. Dependency-free on purpose: two
 * locales do not justify a negotiation library.
 */
export function negotiateLocale(header: string | null, fallback: Locale): Locale {
  if (!header) {
    return fallback;
  }
  const ranked = header
    .split(",")
    .map((part, index) => {
      const [tag, ...params] = part.trim().split(";");
      const qParam = params.map((p) => p.trim()).find((p) => p.startsWith("q="));
      const quality = qParam ? Number.parseFloat(qParam.slice(2)) : 1;
      return { language: tag.toLowerCase().split("-")[0], quality: Number.isFinite(quality) ? quality : 0, index };
    })
    .filter((entry) => entry.quality > 0)
    .sort((a, b) => b.quality - a.quality || a.index - b.index);
  for (const entry of ranked) {
    if (isLocale(entry.language)) {
      return entry.language;
    }
  }
  return fallback;
}
