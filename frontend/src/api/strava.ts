import { api } from "./client";

export type ActivityType = "bike" | "run" | "hike" | "other";

export interface ActivityItem {
  id: number;
  type: ActivityType;
  date: string;
  name: string | null;
  distanceKm: number | null;
  durationS: number | null;
  elevationM: number | null;
  avgHr: number | null;
  avgSpeedKmh: number | null;
}

export interface BikeWeek {
  label: string;
  km: number;
}

export interface ActivitiesView {
  connected: boolean;
  activities: ActivityItem[];
  weekBikeKm: number;
  weekRunKm: number;
  weekHikes: number;
  weekMovingTimeS: number;
  bikeWeekly: BikeWeek[];
}

export const getActivities = () => api<ActivitiesView>("/training/activities");

export const syncStrava = () =>
  api<{ imported: number; total: number }>("/strava/sync", { method: "POST" });

/** Full-page navigation: the backend redirects to Strava's OAuth consent. */
export function connectStrava(): void {
  window.location.href = "/api/strava/connect";
}
