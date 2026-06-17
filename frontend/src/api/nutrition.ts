import { api } from "./client";

export interface Meal {
  id: number;
  date: string; // YYYY-MM-DD
  mealType: string; // breakfast | lunch | dinner | snack
  description: string;
  onPlan: boolean | null;
  notes: string | null;
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
  notes?: string | null;
}

export const getMeals = (days = 14) => api<MealsSummary>(`/nutrition/meals?days=${days}`);

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
