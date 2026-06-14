// Shared date/label helpers used across the dashboard pages (Home, Salud, Gym).
// Spanish UI texts per the project convention.

export const MES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
export const DIA = ["dom", "lun", "mar", "mié", "jue", "vie", "sáb"];

/** Today as an ISO date (YYYY-MM-DD). */
export function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/** "13 jun" */
export function shortDate(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  return `${d.getDate()} ${MES[d.getMonth()]}`;
}

/** "13 jun 2026" */
export function shortDateY(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  return `${d.getDate()} ${MES[d.getMonth()]} ${d.getFullYear()}`;
}

/** "lun", "mar"… for the given ISO date. */
export function weekday(iso: string): string {
  return DIA[new Date(`${iso}T00:00:00`).getDay()];
}

/** Whole days between the given ISO date and now (at least 1). */
export function daysSince(iso: string): number {
  return Math.max(1, Math.round((Date.now() - new Date(`${iso}T00:00:00`).getTime()) / 86400000));
}

/** English routine names from the DB shown in Spanish in the UI. */
export function routineLabel(name: string): string {
  return name === "Leg" ? "Pierna" : name;
}
