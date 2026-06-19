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

export interface Recovery {
  level: "alta" | "media" | "baja";
  title: string;
  sub: string;
  trainingNote: string;
  dotClass: string; // Tailwind bg-* for the status dot
}

/**
 * A simple client-side recovery read for the Home glance: last night's HRV vs the 7-day
 * average, plus sleep duration and resting HR. The reasoned, conversational version lives
 * in the chat (which sees the same query_wellness data). Null when there's no Garmin data.
 */
export function recoveryState(s: WellnessSummary | undefined): Recovery | null {
  const d = s?.days?.[0];
  if (!d || (d.hrvAvgMs == null && d.sleepMinutes == null && d.restingHr == null)) return null;
  let score = 0;
  if (d.hrvAvgMs != null && s!.avgHrvMs != null) {
    if (d.hrvAvgMs >= s!.avgHrvMs) score += 1;
    else if (d.hrvAvgMs < s!.avgHrvMs * 0.85) score -= 1;
  }
  if (d.sleepMinutes != null) {
    if (d.sleepMinutes >= 420) score += 1;
    else if (d.sleepMinutes < 360) score -= 1;
  }
  if (d.restingHr != null && s!.avgRestingHr != null && d.restingHr > s!.avgRestingHr + 3) score -= 1;

  if (score >= 1)
    return {
      level: "alta",
      title: "Bien recuperado",
      sub: "Buen momento para empujar.",
      trainingNote: "Recuperado · progresa con normalidad",
      dotClass: "bg-income",
    };
  if (score <= -1)
    return {
      level: "baja",
      title: "Recuperación baja",
      sub: "Prioriza descanso, proteína e hidratación.",
      trainingNote: "Baja la intensidad o descansa hoy",
      dotClass: "bg-clinical",
    };
  return {
    level: "media",
    title: "Recuperación normal",
    sub: "Un día estándar.",
    trainingNote: "Mantén tu plan",
    dotClass: "bg-ink/40",
  };
}
