/**
 * The one way the SPA talks to the Spring Boot API: same-origin, JSON, the
 * double-submit CSRF header on every mutation, and RFC 7807 problems turned
 * into ApiError.
 */

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly errors?: Record<string, string>,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

function readCookie(name: string) {
  const match = document.cookie
    .split("; ")
    .find((part) => part.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

let csrfRequest: Promise<unknown> | null = null;

/** The XSRF-TOKEN cookie, fetched once if the browser does not have it yet. */
async function csrfToken() {
  let token = readCookie(CSRF_COOKIE);
  if (!token) {
    csrfRequest ??= fetch("/api/auth/csrf", { credentials: "same-origin" }).finally(() => {
      csrfRequest = null;
    });
    await csrfRequest;
    token = readCookie(CSRF_COOKIE);
  }
  return token ?? "";
}

let onUnauthenticated: () => void = () => {};

/** Called when the session has expired; the router sends the user to /login. */
export function setUnauthenticatedHandler(handler: () => void) {
  onUnauthenticated = handler;
}

type Options = { method?: string; json?: unknown; form?: FormData };

export async function api<T = unknown>(path: string, options: Options = {}): Promise<T> {
  const method = options.method ?? (options.json !== undefined || options.form ? "POST" : "GET");
  const headers: Record<string, string> = { Accept: "application/json" };
  let body: BodyInit | undefined;
  if (options.json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.json);
  } else if (options.form) {
    body = options.form;
  }
  if (method !== "GET") headers[CSRF_HEADER] = await csrfToken();

  const response = await fetch(path, { method, headers, body, credentials: "same-origin" });
  if (response.status === 401 && !path.startsWith("/api/auth/")) onUnauthenticated();
  if (!response.ok) throw await problem(response);
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function problem(response: Response) {
  try {
    const body = await response.json();
    return new ApiError(
      response.status,
      body.code ?? "error",
      body.detail ?? body.title ?? response.statusText,
      body.errors,
    );
  } catch {
    return new ApiError(response.status, "error", response.statusText || "The request failed.");
  }
}

/** A message fit to show: the server's text plus the fields that failed validation. */
export function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    const fields = error.errors ? Object.keys(error.errors) : [];
    return fields.length ? `${error.message} (${fields.join(", ")})` : error.message;
  }
  return error instanceof Error ? error.message : "Something went wrong.";
}

/**
 * A form as JSON. Blank fields become null, so optional values fall back to
 * their defaults server-side and "no selection" needs no special value.
 */
export function formJson(formData: FormData) {
  const values: Record<string, string | null> = {};
  for (const [key, value] of formData.entries()) {
    if (typeof value !== "string") continue;
    values[key] = value.trim() === "" ? null : value;
  }
  return values;
}
