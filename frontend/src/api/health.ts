import { api } from "./client";

export interface DiaryEntry {
  date: string;
  bristol: number | null;
  pain: number | null;
  urgency: number | null;
  blood: boolean;
  bowelMovements: number | null;
  stress: number | null;
  notes: string | null;
}

export interface Flare {
  id: number;
  startDate: string;
  endDate: string | null;
  severity: "mild" | "moderate" | "severe";
  notes: string | null;
}

export interface HealthSummary {
  activeFlare: Flare | null;
  recentEntries: DiaryEntry[];
}

export interface FlaresView {
  active: Flare | null;
  recent: Flare[];
}

const json = { "Content-Type": "application/json" };

export const getSummary = (days = 30) =>
  api<HealthSummary>(`/health/summary?days=${days}`);

export const getEntry = (date: string) =>
  api<DiaryEntry>(`/health/diary/${date}`);

export const saveEntry = (date: string, entry: Partial<DiaryEntry>) =>
  api<DiaryEntry>(`/health/diary/${date}`, {
    method: "PUT",
    headers: json,
    body: JSON.stringify(entry),
  });

export const getFlares = () => api<FlaresView>("/health/flares");

export const startFlare = (severity: Flare["severity"]) =>
  api<Flare>("/health/flares/start", {
    method: "POST",
    headers: json,
    body: JSON.stringify({ severity }),
  });

export const endFlare = () =>
  api<Flare>("/health/flares/end", {
    method: "POST",
    headers: json,
    body: JSON.stringify({}),
  });

export const SEVERITY_LABELS: Record<Flare["severity"], string> = {
  mild: "leve",
  moderate: "moderado",
  severe: "grave",
};
