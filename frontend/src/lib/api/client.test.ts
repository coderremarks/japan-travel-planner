import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, csrfMiddleware, readCookie, unwrap } from "./client";

type OnRequest = (options: { request: Request }) => Promise<Request>;
const onRequest = csrfMiddleware.onRequest as unknown as OnRequest;

afterEach(() => {
  vi.restoreAllMocks();
  document.cookie = "XSRF-TOKEN=; max-age=0; path=/";
});

describe("readCookie", () => {
  it("finds and decodes a cookie among others", () => {
    expect(readCookie("XSRF-TOKEN", "a=1; XSRF-TOKEN=abc%3D%3D; b=2")).toBe("abc==");
    expect(readCookie("missing", "a=1")).toBeUndefined();
  });
});

describe("csrfMiddleware", () => {
  it("adds the token header to state-changing requests", async () => {
    document.cookie = "XSRF-TOKEN=token-123; path=/";
    const request = await onRequest({
      request: new Request("http://localhost/api/v1/itineraries", { method: "POST" }),
    });
    expect(request.headers.get("X-XSRF-TOKEN")).toBe("token-123");
  });

  it("does not touch safe requests", async () => {
    document.cookie = "XSRF-TOKEN=token-123; path=/";
    const request = await onRequest({ request: new Request("http://localhost/api/v1/areas") });
    expect(request.headers.get("X-XSRF-TOKEN")).toBeNull();
  });

  it("fetches the CSRF cookie first when the browser has none", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async () => {
      document.cookie = "XSRF-TOKEN=fresh; path=/";
      return new Response(null, { status: 200 });
    });
    const request = await onRequest({
      request: new Request("http://localhost/api/v1/auth/login", { method: "POST" }),
    });
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/auth/csrf", { credentials: "same-origin" });
    expect(request.headers.get("X-XSRF-TOKEN")).toBe("fresh");
  });
});

describe("unwrap", () => {
  it("returns data for successful responses", () => {
    expect(unwrap({ data: { ok: true }, response: new Response(null, { status: 200 }) })).toEqual({ ok: true });
  });

  it("throws a typed ApiError with code and field violations", () => {
    const failing = () =>
      unwrap({
        error: { code: "VALIDATION_FAILED", status: 400, errors: [{ field: "endDate", code: "END_BEFORE_START" }] },
        response: new Response(null, { status: 400 }),
      });
    expect(failing).toThrow(ApiError);
    try {
      failing();
    } catch (error) {
      expect((error as ApiError).fieldErrors()).toEqual({ endDate: "END_BEFORE_START" });
    }
  });

  it("maps bodies without a problem code to a generic error", () => {
    expect(() => unwrap({ error: "<html>bad gateway</html>", response: new Response(null, { status: 502 }) })).toThrow(
      expect.objectContaining({ status: 502, code: "generic" }),
    );
  });
});
