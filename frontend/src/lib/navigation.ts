import type { Locale } from "@/i18n/config";

/**
 * Validates a post-login redirect target. Only same-origin, locale-prefixed paths are allowed:
 * absolute URLs, protocol-relative `//evil.example` and backslash tricks are rejected, so the
 * login page cannot be abused as an open redirect.
 */
export function safeRedirectPath(candidate: string | null | undefined, locale: Locale): string {
  const fallback = `/${locale}/itineraries`;
  if (!candidate || !candidate.startsWith(`/${locale}/`) || candidate.startsWith("//") || candidate.includes("\\")) {
    return fallback;
  }
  try {
    const url = new URL(candidate, "https://tabiji.invalid");
    return url.origin === "https://tabiji.invalid" ? `${url.pathname}${url.search}` : fallback;
  } catch {
    return fallback;
  }
}

/** Same path in another language: `/en/itineraries/42` → `/ja/itineraries/42`. */
export function switchLocalePath(pathname: string, target: Locale): string {
  const segments = pathname.split("/");
  segments[1] = target;
  return segments.join("/") || `/${target}`;
}
