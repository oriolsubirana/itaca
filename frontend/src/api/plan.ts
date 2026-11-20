import { api } from "./client";
import type { DayLoad } from "./training";

export interface PhaseView {
  key: string;
  name: string;
  objective: string;
  week: number;
  totalWeeks: number;
  guidance: string[];
  milestone: string;
}

export interface SportProgress {
  sessions: number;
  km: number;
  hours: number;
  pace: string | null;
}

export interface PlanProgress {
  windowDays: number;
  swim: SportProgress;
  run: SportProgress;
  bike: SportProgress;
  longestRunKm: number | null;
  longestRunPace: string | null;
}

export interface TemplateDay {
  day: string;
  session: string;
  focus: string;
}

export interface RaceTarget {
  sector: string;
  target: string;
  time: string;
  comment: string;
}

export interface TriathlonPlanView {
  raceName: string;
  raceDate: string; // YYYY-MM-DD
  daysToRace: number;
  goal: string;
  phase: PhaseView | null;
  nextPhaseStart: string | null;
  weeklyTemplate: TemplateDay[];
  raceTargets: RaceTarget[];
  principles: string[];
  progress: PlanProgress;
  today: TodayPlan;
}

export type SessionStatus = "done" | "partial" | "missed" | "pending" | "upcoming";

export interface PlannedSession {
  date: string;
  sport: "swim" | "bike" | "run" | "gym" | "race";
  title: string;
  detail: string;
  minutes: number;
  intensity: "easy" | "moderate" | "key" | "long" | "race";
  gymRoutine: string | null;
  distanceKm: number | null;
  status: SessionStatus;
  doneMinutes: number;
}

export interface DayPlan {
  date: string;
  weekday: string;
  dayLoad: DayLoad;
  sessions: PlannedSession[];
}

export interface TrainingWeek {
  monday: string;
  sunday: string;
  phaseKey: string;
  phaseName: string;
  weekInPhase: number;
  weeksToRace: number;
  kind: "load" | "recovery" | "sharpen" | "taper" | "race";
  focus: string;
  totalMinutes: number;
  days: DayPlan[];
  adherence: { percent: number | null; bySport: { sport: string; plannedMinutes: number; doneMinutes: number }[] };
}

export interface TodayPlan {
  date: string;
  dayLoad: DayLoad;
  sessions: PlannedSession[];
  pendingKcal: number;
}

export interface LoadPoint {
  date: string;
  load: number;
  fitness: number;
  fatigue: number;
  form: number;
}

export interface TrainingLoad {
  today: LoadPoint;
  status: "fresh" | "neutral" | "productive" | "overreaching";
  statusLabel: string;
  advice: string;
  rampPerWeek: number;
  rampWarning: boolean;
  series: LoadPoint[];
  note: string;
}

export const getTriathlonPlan = () => api<TriathlonPlanView>("/training/plan");

/** The plan week containing `date` (YYYY-MM-DD; default this week). 404 outside the plan. */
export const getTrainingWeek = (date?: string) =>
  api<TrainingWeek>(`/training/plan/week${date ? `?date=${date}` : ""}`);

export const getTrainingLoad = (days = 42) => api<TrainingLoad>(`/training/load?days=${days}`);

export const SPORT_LABELS: Record<PlannedSession["sport"], string> = {
  swim: "Natación",
  bike: "Bici",
  run: "Carrera",
  gym: "Gym",
  race: "Carrera",
};

export const STATUS_LABELS: Record<SessionStatus, string> = {
  done: "Hecha",
  partial: "Parcial",
  missed: "Saltada",
  pending: "Pendiente",
  upcoming: "",
};

export const DAY_LOAD_LABELS: Record<DayLoad, string> = {
  rest: "Descanso",
  easy: "Día suave",
  key: "Sesión clave",
  long: "Tirada larga",
  race: "Carrera",
};
