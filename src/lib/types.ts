// Tipos del dominio de Ítaca (espejo del esquema en /supabase/migrations).

export type Moneda = "CHF" | "EUR";

// ── Gym ──
export interface Exercise {
  id: string;
  nombre: string;
  grupo_muscular: string | null;
}

export interface Routine {
  id: string;
  nombre: string;
  exercise_ids: string[];
}

export interface Workout {
  id: string;
  fecha: string;
  routine_id: string | null;
  notas: string | null;
  completado: boolean;
  created_at: string;
}

export interface SetRow {
  id: string;
  workout_id: string;
  exercise_id: string;
  peso: number;
  reps: number;
  orden: number;
  rpe: number | null;
}

// ── Salud ──
export interface DiaryEntry {
  id: string;
  fecha: string;
  bristol: number | null;
  dolor: number | null;
  urgencia: number | null;
  sangre: boolean | null;
  num_deposiciones: number | null;
  estres: number | null;
  notas: string | null;
}

export interface Meal {
  id: string;
  diary_entry_id: string;
  descripcion: string;
  tags: string[];
}

export interface Flare {
  id: string;
  fecha_inicio: string;
  fecha_fin: string | null;
  severidad: number | null;
  notas: string | null;
}

export interface LabReport {
  id: string;
  fecha: string;
  laboratorio: string | null;
}

export interface LabResult {
  id: string;
  lab_report_id: string;
  analito: string;
  valor: number | null;
  unidad: string | null;
  ref_min: number | null;
  ref_max: number | null;
}

export interface LabAnalyte {
  id: string;
  canonico: string;
  unidad: string | null;
  ref_min: number | null;
  ref_max: number | null;
  sinonimos: string[];
}

// ── Finanzas ──
export interface Account {
  id: string;
  nombre: string;
  tipo: string | null;
  moneda: Moneda;
}

export interface BalanceSnapshot {
  id: string;
  account_id: string;
  fecha: string;
  saldo: number;
}

export interface Transaction {
  id: string;
  account_id: string;
  fecha: string;
  importe: number;
  descripcion: string | null;
  categoria: string | null;
}

// ── Chat ──
export type ChatModo = "general" | "entreno";

export interface ChatSession {
  id: string;
  modo: ChatModo;
  workout_id: string | null;
  titulo: string | null;
  activa: boolean;
  created_at: string;
  ended_at: string | null;
}

// Bloques de contenido (subconjunto de la Anthropic Messages API)
export type ContentBlock =
  | { type: "text"; text: string }
  | { type: "tool_use"; id: string; name: string; input: Record<string, unknown> }
  | { type: "tool_result"; tool_use_id: string; content: string; is_error?: boolean };

export interface ChatMessageRow {
  id: string;
  session_id: string;
  role: "user" | "assistant";
  content: ContentBlock[];
  created_at: string;
}
