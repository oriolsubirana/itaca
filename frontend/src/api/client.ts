/**
 * Cliente API mínimo. Los tipos se generan desde el OpenAPI del backend
 * con `npm run generate:api` (src/api/schema.d.ts) cuando haya endpoints.
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
  return (await response.json()) as T;
}
