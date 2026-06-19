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
  level: "high" | "medium" | "low";
  title: string;
  sub: string;
  trainingNote: string;
  dotClass: string; // Tailwind bg-* for the status dot
}

/**
 * A simple client-side recovery read for the Home glance: last night's HRV vs the rolling
 * average of the requested window, plus sleep duration and resting HR. The reasoned,
 * conversational version lives in the chat (same query_wellness data). Null without Garmin data.
 */
export function recoveryState(s: WellnessSummary | undefined): Recovery | null {
  const d = s?.days?.[0];
  if (!d || (d.hrvAvgMs == null && d.sleepMinutes == null && d.restingHr == null && d.bodyBatteryHigh == null)) return null;
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
  if (d.bodyBatteryHigh != null) {
    if (d.bodyBatteryHigh >= 70) score += 1;
    else if (d.bodyBatteryHigh < 40) score -= 1;
  }

  // Describe the day's signals; the actual recommendation (training, nutrition) is the chat's job.
  if (score >= 1)
    return {
      level: "high",
      title: "Bien recuperado",
      sub: "Tus señales de recuperación están altas.",
      trainingNote: "Recuperación alta",
      dotClass: "bg-income",
    };
  if (score <= -1)
    return {
      level: "low",
      title: "Recuperación baja",
      sub: "Tus señales de recuperación están bajas.",
      trainingNote: "Recuperación baja",
      dotClass: "bg-clinical",
    };
  return {
    level: "medium",
    title: "Recuperación normal",
    sub: "Dentro de tu rango habitual.",
    trainingNote: "Recuperación normal",
    dotClass: "bg-ink/40",
  };
}
