import { NextResponse, type NextRequest } from "next/server";
import { DEFAULT_LOCALE, LOCALE_COOKIE, isLocale, negotiateLocale } from "@/i18n/config";

/**
 * Runs before every page request:
 *  1. Locale routing — `/` and other unprefixed paths redirect to `/en/...` or `/ja/...`, chosen
 *     from the user's saved choice (cookie) or the browser's Accept-Language.
 *  2. Content-Security-Policy with a fresh nonce per request: only scripts carrying the nonce (and
 *     what they load, via 'strict-dynamic') may run, which neutralises injected scripts (XSS).
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const firstSegment = pathname.split("/")[1] ?? "";

  if (!isLocale(firstSegment)) {
    const saved = request.cookies.get(LOCALE_COOKIE)?.value;
    const locale = isLocale(saved) ? saved : negotiateLocale(request.headers.get("accept-language"), DEFAULT_LOCALE);
    const url = request.nextUrl.clone();
    url.pathname = `/${locale}${pathname === "/" ? "" : pathname}`;
    url.search = search;
    return NextResponse.redirect(url);
  }

  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const csp = contentSecurityPolicy(nonce, {
    development: process.env.NODE_ENV === "development",
    https: servedOverHttps(request),
  });
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);
  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);
  return response;
}

/**
 * True when the browser reached us over HTTPS, directly or through a TLS-terminating proxy. A
 * spoofed header can only change the policy of the spoofer's own response, and only by the
 * `upgrade-insecure-requests` directive, which is not a security boundary (HSTS at the edge is).
 */
export function servedOverHttps(request: Pick<NextRequest, "nextUrl" | "headers">): boolean {
  const forwarded = request.headers.get("x-forwarded-proto")?.split(",")[0]?.trim().toLowerCase();
  return request.nextUrl.protocol === "https:" || forwarded === "https";
}

export function contentSecurityPolicy(nonce: string, { development, https }: { development: boolean; https: boolean }) {
  const tiles = "https://tile.openstreetmap.org";
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${development ? " 'unsafe-eval'" : ""}`,
    // Inline styles are needed by the map library and React style props; they cannot execute code.
    "style-src 'self' 'unsafe-inline'",
    `img-src 'self' data: blob: ${tiles}`,
    "font-src 'self' data:",
    `connect-src 'self'${development ? " ws:" : ""}`,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    // Only on HTTPS pages: on a plain-HTTP origin (e.g. the local compose stack) it would rewrite
    // the page's own same-origin requests to https:// and break them.
    ...(https && !development ? ["upgrade-insecure-requests"] : []),
  ].join("; ");
}

export const config = {
  matcher: [
    {
      source: "/((?!api|_next/static|_next/image|favicon.ico|.*\\.[a-z0-9]+$).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
