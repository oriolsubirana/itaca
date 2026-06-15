import { api } from "./client";

export interface IngestedFile {
  id: number;
  name: string;
  type: string; // pdf | csv | unknown
  destination: string | null; // health | finance | null
  status: string; // pending | processed | error
  errorMessage: string | null;
  detail: string | null;
  createdAt: string; // ISO instant
}

/** The inbox: most recent ingested files first. */
export const getInbox = () => api<IngestedFile[]>("/ingest");

/** Re-runs classification + routing for a failed file. */
export const retryIngest = (id: number) =>
  api<IngestedFile>(`/ingest/${id}/retry`, { method: "POST" });

/** Generic multipart intake: any PDF/CSV; the backend classifies and routes it. */
export async function uploadIngest(file: File): Promise<IngestedFile> {
  const form = new FormData();
  form.append("file", file);
  form.append("source", "web");
  const headers = new Headers();
  const token = import.meta.env.VITE_API_TOKEN as string | undefined;
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const res = await fetch("/api/ingest", { method: "POST", body: form, headers });
  if (!res.ok) throw new Error(`Ingest ${res.status}`);
  return (await res.json()) as IngestedFile;
}
