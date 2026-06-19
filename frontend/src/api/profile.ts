import { api } from "./client";

export interface Profile {
  weightKg: number | null;
  heightCm: number | null;
  birthDate: string | null; // ISO
  sex: string | null; // male | female
  activityLevel: string | null;
  goal: string | null;
  age: number | null;
  bmr: number | null;
  tdee: number | null;
  baseTarget: number | null; // tdee + goal adjustment
}

export interface ProfileBody {
  weightKg?: number | null;
  heightCm?: number | null;
  birthDate?: string | null;
  sex?: string | null;
  activityLevel?: string | null;
  goal?: string | null;
}

export const getProfile = () => api<Profile>("/profile");

export const saveProfile = (body: ProfileBody) =>
  api<Profile>("/profile", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

export const SEXES = [
  { code: "male", label: "Hombre" },
  { code: "female", label: "Mujer" },
];
export const ACTIVITY_LEVELS = [
  { code: "sedentary", label: "Sedentario" },
  { code: "light", label: "Ligero" },
  { code: "moderate", label: "Moderado" },
  { code: "active", label: "Activo" },
  { code: "very_active", label: "Muy activo" },
];
export const GOALS = [
  { code: "lose", label: "Perder grasa" },
  { code: "maintain", label: "Mantener" },
  { code: "gain", label: "Ganar músculo" },
];

const ACTIVITY_FACTOR: Record<string, number> = {
  sedentary: 1.2,
  light: 1.375,
  moderate: 1.55,
  active: 1.725,
  very_active: 1.9,
};
const GOAL_ADJ: Record<string, number> = { lose: -500, maintain: 0, gain: 300 };

export function ageFromIso(iso: string | null): number | null {
  if (!iso) return null;
  const b = new Date(`${iso}T00:00:00`);
  const t = new Date();
  let a = t.getFullYear() - b.getFullYear();
  if (t.getMonth() < b.getMonth() || (t.getMonth() === b.getMonth() && t.getDate() < b.getDate())) a--;
  return a >= 0 ? a : null;
}

export interface Targets {
  bmr: number;
  tdee: number;
  baseTarget: number;
}

/**
 * Live Mifflin-St Jeor calc, mirroring the backend, so the numbers update as you edit.
 * The backend recomputes on save and is the source of truth.
 */
export function computeTargets(f: {
  weightKg: number | null;
  heightCm: number | null;
  age: number | null;
  sex: string | null;
  activityLevel: string | null;
  goal: string | null;
}): Targets | null {
  const { weightKg, heightCm, age, sex, activityLevel, goal } = f;
  if (weightKg == null || heightCm == null || age == null || !sex || !activityLevel || !goal) return null;
  if (weightKg <= 0 || heightCm <= 0 || age <= 0) return null;
  const bmr =
    sex === "male"
      ? 10 * weightKg + 6.25 * heightCm - 5 * age + 5
      : 10 * weightKg + 6.25 * heightCm - 5 * age - 161;
  const tdee = bmr * (ACTIVITY_FACTOR[activityLevel] ?? 1.2);
  const baseTarget = tdee + (GOAL_ADJ[goal] ?? 0);
  return { bmr: Math.round(bmr), tdee: Math.round(tdee), baseTarget: Math.round(baseTarget) };
}

/**
 * The saved profile's precomputed targets ({bmr, tdee, baseTarget}), or null until the backend
 * has enough data to compute them. Use this for read-only views (Home, Comidas); the live editor
 * (Perfil) uses computeTargets to recompute as you type.
 */
export function targetsFromProfile(p: Profile | undefined): Targets | null {
  return p && p.tdee != null && p.baseTarget != null ? { bmr: p.bmr ?? 0, tdee: p.tdee, baseTarget: p.baseTarget } : null;
}

/**
 * The day's calorie target: base (TDEE adjusted to the goal) + calories burned today,
 * with the goal deficit neutralised during an active flare (eat at maintenance).
 */
export function dailyKcalTarget(targets: Targets | null, todayActivityKcal: number, flareActive: boolean): number | null {
  if (!targets) return null;
  const base = flareActive ? targets.tdee : targets.baseTarget;
  return base + todayActivityKcal;
}
