import { api } from "./client";

export interface TrainingSummary {
  lastWorkoutDate: string | null;
  lastWorkoutRoutine: string | null;
  nextRoutine: string;
  todayActivityKcal: number;
}

export const getTrainingSummary = () => api<TrainingSummary>("/training/summary");

export interface SessionSummary {
  id: number;
  date: string;
  routine: string;
  completed: boolean;
  summary: string;
}

export interface SessionExercise {
  name: string;
  sets: string;
}

export interface SessionDetail {
  id: number;
  date: string;
  routine: string;
  completed: boolean;
  exercises: SessionExercise[];
  durationS: number | null;
  avgHr: number | null;
  calories: number | null;
}

export interface ExerciseRef {
  id: number;
  name: string;
}

export interface ProgressionPoint {
  date: string;
  weight: number;
  reps: number;
}

export interface ExerciseProgression {
  id: number;
  name: string;
  target: string;
  points: ProgressionPoint[];
  lastWeight: number | null;
  lastReps: number | null;
  suggestedWeight: number | null;
}

export const getSessions = () => api<SessionSummary[]>("/training/sessions");

export const getSessionDetail = (id: number) =>
  api<SessionDetail>(`/training/sessions/${id}`);

export const getExercises = () => api<ExerciseRef[]>("/training/exercises");

export const getExerciseProgression = (id: number) =>
  api<ExerciseProgression>(`/training/exercises/${id}/progression`);
