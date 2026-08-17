/**
 * Minimal API client. Types are generated from the backend OpenAPI spec
 * with `npm run generate:api` (src/api/schema.d.ts) once endpoints exist.
 *
 * Sends the static bearer token when present (machine/dev), and always includes
 * credentials so the Google session cookie flows for the human PWA path.
 */
const API_TOKEN = import.meta.env.VITE_API_TOKEN as string | undefined;

/** Carries the HTTP status so callers (e.g. the auth gate) can react to a 401. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    path: string,
  ) {
    super(`API ${status}: ${path}`);
    this.name = "ApiError";
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (API_TOKEN) headers.set("Authorization", `Bearer ${API_TOKEN}`);

  const response = await fetch(`/api${path}`, { ...init, headers, credentials: "include" });
  if (!response.ok) {
    throw new ApiError(response.status, path);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** Fetches a binary response (e.g. a PDF) with the same auth as `api`. */
export async function apiBlob(path: string): Promise<Blob> {
  const headers = new Headers();
  if (API_TOKEN) headers.set("Authorization", `Bearer ${API_TOKEN}`);
  const response = await fetch(`/api${path}`, { headers, credentials: "include" });
  if (!response.ok) throw new ApiError(response.status, path);
  return response.blob();
}
