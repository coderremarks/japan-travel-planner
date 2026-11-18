import { describe, expect, it } from "vitest";
import { contentSecurityPolicy } from "./proxy";

describe("contentSecurityPolicy", () => {
  it("allows only nonce-bearing scripts in production", () => {
    const csp = contentSecurityPolicy("abc123", false);
    expect(csp).toContain("script-src 'self' 'nonce-abc123' 'strict-dynamic'");
    expect(csp).not.toMatch(/script-src[^;]*unsafe-inline/);
    expect(csp).not.toContain("unsafe-eval");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("upgrade-insecure-requests");
  });

  it("relaxes only what the dev server needs", () => {
    const csp = contentSecurityPolicy("abc123", true);
    expect(csp).toContain("'unsafe-eval'");
    expect(csp).not.toContain("upgrade-insecure-requests");
  });
});
