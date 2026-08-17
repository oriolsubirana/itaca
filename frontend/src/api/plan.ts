import { api } from "./client";

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
}

export const getTriathlonPlan = () => api<TriathlonPlanView>("/training/plan");
