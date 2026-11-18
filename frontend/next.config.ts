import type { NextConfig } from "next";

/**
 * The browser only ever talks to this origin: `/api/*` is proxied to the Spring Boot API
 * (ADR-0004), so cookies are first-party and no CORS is needed. The Content-Security-Policy is set
 * per request in `src/proxy.ts` because it carries a fresh nonce; the static headers live here.
 */
const apiOrigin = process.env.API_ORIGIN ?? "http://localhost:8080";

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  // OpenStreetMap's tile usage policy asks for a Referer; send the origin only, never the path.
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=(), usb=()" },
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
];

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  reactStrictMode: true,
  // Cache Components is intentionally off: every page carries a per-request CSP nonce and personal
  // data, so it must be rendered dynamically (see the Next.js CSP guide).
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiOrigin}/api/:path*` }];
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
