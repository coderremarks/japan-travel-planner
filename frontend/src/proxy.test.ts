import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { contentSecurityPolicy, servedOverHttps } from "./proxy";

describe("contentSecurityPolicy", () => {
  it("allows only nonce-bearing scripts in production", () => {
    const csp = contentSecurityPolicy("abc123", { development: false, https: true });
    expect(csp).toContain("script-src 'self' 'nonce-abc123' 'strict-dynamic'");
    expect(csp).not.toMatch(/script-src[^;]*unsafe-inline/);
    expect(csp).not.toContain("unsafe-eval");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("upgrade-insecure-requests");
  });

  it("does not upgrade requests on a plain-HTTP origin", () => {
    const csp = contentSecurityPolicy("abc123", { development: false, https: false });
    expect(csp).not.toContain("upgrade-insecure-requests");
    expect(csp).toContain("'nonce-abc123'");
  });

  it("relaxes only what the dev server needs", () => {
    const csp = contentSecurityPolicy("abc123", { development: true, https: false });
    expect(csp).toContain("'unsafe-eval'");
    expect(csp).toContain("connect-src 'self' ws:");
    expect(csp).not.toContain("upgrade-insecure-requests");
  });
});

describe("servedOverHttps", () => {
  it("recognises a direct HTTPS request", () => {
    expect(servedOverHttps(new NextRequest("https://tabiji.example/en"))).toBe(true);
  });

  it("recognises HTTPS terminated by a proxy in front", () => {
    const request = new NextRequest("http://web:3000/en", { headers: { "x-forwarded-proto": "https, http" } });
    expect(servedOverHttps(request)).toBe(true);
  });

  it("treats plain HTTP as plain HTTP", () => {
    const request = new NextRequest("http://localhost:3000/en", { headers: { "x-forwarded-proto": "http" } });
    expect(servedOverHttps(request)).toBe(false);
  });
});
