import { api } from "./client";

export interface WellnessDay {
  date: string; // YYYY-MM-DD
  sleepMinutes: number | null;
  deepMinutes: number | null;
  lightMinutes: number | null;
  remMinutes: number | null;
  awakeMinutes: number | null;
  sleepScore: number | null;
  hrvAvgMs: number | null;
  hrvStatus: string | null;
  restingHr: number | null;
  stressAvg: number | null;
  bodyBatteryHigh: number | null;
  bodyBatteryLow: number | null;
  steps: number | null;
  activeCalories: number | null;
  spo2Avg: number | null;
  respirationAvg: number | null;
}

export interface WellnessSummary {
  days: WellnessDay[];
  avgSleepMinutes: number | null;
  avgHrvMs: number | null;
  avgRestingHr: number | null;
}

export const getWellness = (days = 14) => api<WellnessSummary>(`/wellness?days=${days}`);

/** Minutes -> "7h 22m" / "45m". */
export function sleepLabel(minutes: number | null): string {
  if (minutes == null) return "—";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h}h ${String(m).padStart(2, "0")}m` : `${m}m`;
}
