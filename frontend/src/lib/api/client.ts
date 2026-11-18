import createClient, { type Middleware } from "openapi-fetch";
import type { components, paths } from "./schema";

export type Problem = components["schemas"]["Problem"];

/** An API failure with the server's stable error code (RFC 9457 `code`). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly violations: { field: string; code: string }[] = [],
  ) {
    super(code);
    this.name = "ApiError";
  }

  /** Field-level violation codes keyed by field name, e.g. `{ endDate: "END_BEFORE_START" }`. */
  fieldErrors(): Record<string, string> {
    return Object.fromEntries(this.violations.map((v) => [v.field, v.code]));
  }
}

export const CSRF_COOKIE = "XSRF-TOKEN";
export const CSRF_HEADER = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export function readCookie(name: string, cookieHeader: string = document.cookie): string | undefined {
  for (const part of cookieHeader.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) {
      return decodeURIComponent(rest.join("="));
    }
  }
  return undefined;
}

/**
 * Double-submit CSRF: copies the XSRF-TOKEN cookie into the X-XSRF-TOKEN header on every
 * state-changing request, fetching the cookie first if the browser does not have one yet. A
 * malicious site can make the browser send the cookie, but cannot read it to set the header.
 */
export const csrfMiddleware: Middleware = {
  async onRequest({ request }) {
    if (SAFE_METHODS.has(request.method)) {
      return request;
    }
    let token = readCookie(CSRF_COOKIE);
    if (!token) {
      await fetch("/api/v1/auth/csrf", { credentials: "same-origin" });
      token = readCookie(CSRF_COOKIE);
    }
    if (token) {
      request.headers.set(CSRF_HEADER, token);
    }
    return request;
  },
};

export const api = createClient<paths>({ baseUrl: "", credentials: "same-origin" });
api.use(csrfMiddleware);

/**
 * Turns an openapi-fetch result into data or a typed {@link ApiError}. Responses without a problem
 * body (e.g. a proxy error page) still become an ApiError with a generic code.
 */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.response.ok) {
    return result.data as T;
  }
  const problem = result.error as Partial<Problem> | undefined;
  throw new ApiError(
    result.response.status,
    typeof problem?.code === "string" ? problem.code : "generic",
    Array.isArray(problem?.errors) ? problem.errors : [],
  );
}
