import { api } from "./client";

export type ActivityType = "bike" | "run" | "hike" | "gym" | "other";

export interface ActivityItem {
  id: number;
  type: ActivityType;
  sport: string | null;
  date: string;
  name: string | null;
  distanceKm: number | null;
  durationS: number | null;
  elevationM: number | null;
  avgHr: number | null;
  avgSpeedKmh: number | null;
  calories: number | null;
  routine: string | null;
}

export interface VolumeWeek {
  label: string;
  value: number;
  sub: string;
}

export interface YtdTotals {
  distance: string | null;
  elevation: string | null;
  time: string;
}

export interface SportVolume {
  unit: string;
  ytd: YtdTotals;
  weeks: VolumeWeek[];
}

export interface ActivitiesView {
  connected: boolean;
  activities: ActivityItem[];
  weekBikeKm: number;
  weekRunKm: number;
  weekHikes: number;
  weekMovingTimeS: number;
  volume: Record<string, SportVolume>;
}

export const getActivities = () => api<ActivitiesView>("/training/activities");

export const syncStrava = () =>
  api<{ imported: number; total: number }>("/strava/sync", { method: "POST" });

/** Full-page navigation: the backend redirects to Strava's OAuth consent. */
export function connectStrava(): void {
  window.location.href = "/api/strava/connect";
}
