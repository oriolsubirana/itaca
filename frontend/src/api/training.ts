import { api } from "./client";

export interface TrainingSummary {
  lastWorkoutDate: string | null;
  lastWorkoutRoutine: string | null;
  nextRoutine: string;
}

export const getTrainingSummary = () => api<TrainingSummary>("/training/summary");
