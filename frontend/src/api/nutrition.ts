import { api } from "./client";

export interface Meal {
  id: number;
  date: string; // YYYY-MM-DD
  mealType: string; // breakfast | lunch | dinner | snack
  description: string;
  onPlan: boolean | null;
  calories: number | null;
  notes: string | null;
}

/** Claude's read of a meal photo — a proposal the user reviews before saving. */
export interface MealAnalysis {
  description: string;
  calories: number | null;
  mealType: string;
  onPlan: boolean;
}

export interface MealsSummary {
  meals: Meal[];
  total: number;
  onPlan: number;
}

export interface LogMealBody {
  date?: string;
  mealType: string;
  description: string;
  onPlan?: boolean | null;
  calories?: number | null;
  notes?: string | null;
}

export const getMeals = (days = 14) => api<MealsSummary>(`/nutrition/meals?days=${days}`);

/** Uploads a meal photo for Claude to analyse into a reviewable proposal (not saved yet). */
export async function analyzeMealPhoto(file: File): Promise<MealAnalysis> {
  const form = new FormData();
  form.append("file", file);
  const headers = new Headers();
  const token = import.meta.env.VITE_API_TOKEN as string | undefined;
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const res = await fetch("/api/nutrition/meals/photo", { method: "POST", body: form, headers });
  if (!res.ok) throw new Error(`Photo ${res.status}`);
  return (await res.json()) as MealAnalysis;
}

export const logMeal = (body: LogMealBody) =>
  api<Meal>("/nutrition/meals", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

export const deleteMeal = (id: number) => api<void>(`/nutrition/meals/${id}`, { method: "DELETE" });

/** Meal types with their Spanish labels (single source for the picker and the list). */
export const MEAL_TYPES: { code: string; label: string }[] = [
  { code: "breakfast", label: "Desayuno" },
  { code: "lunch", label: "Comida" },
  { code: "dinner", label: "Cena" },
  { code: "snack", label: "Snack" },
];

export const MEAL_LABELS: Record<string, string> = Object.fromEntries(MEAL_TYPES.map((m) => [m.code, m.label]));
