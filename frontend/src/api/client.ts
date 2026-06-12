/**
 * Minimal API client. Types are generated from the backend OpenAPI spec
 * with `npm run generate:api` (src/api/schema.d.ts) once endpoints exist.
 */
const API_TOKEN = import.meta.env.VITE_API_TOKEN as string | undefined;

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (API_TOKEN) headers.set("Authorization", `Bearer ${API_TOKEN}`);

  const response = await fetch(`/api${path}`, { ...init, headers });
  if (!response.ok) {
    throw new Error(`API ${response.status}: ${path}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
