import type Anthropic from "@anthropic-ai/sdk";
import { getServerSupabase } from "./supabase";
import { EUR_TO_CHF } from "./anthropic";

// Contexto disponible para los ejecutores (sesión de chat activa).
export interface ToolContext {
  sessionId: string;
}

// ── Definiciones de herramientas (esquema para la Anthropic Messages API) ──
export const TOOLS: Anthropic.Tool[] = [
  // Lectura
  {
    name: "query_workouts",
    description:
      "Consulta entrenamientos. Sin parámetros: últimas sesiones. Con 'routine': última sesión completada de esa rutina con sus series. Con 'exercise': progresión de carga de ese ejercicio.",
    input_schema: {
      type: "object",
      properties: {
        routine: { type: "string", description: "Nombre de la rutina (Push/Pull/Leg)" },
        exercise: { type: "string", description: "Nombre del ejercicio" },
        limit: { type: "integer", description: "Nº de registros (por defecto 5)" },
      },
    },
  },
  {
    name: "query_health",
    description:
      "Consulta el diario de salud (EII): entradas recientes (bristol, dolor, urgencia, sangre, deposiciones, estrés) y brotes.",
    input_schema: {
      type: "object",
      properties: {
        days: { type: "integer", description: "Días hacia atrás (por defecto 30)" },
      },
    },
  },
  {
    name: "query_lab_results",
    description:
      "Consulta resultados de analíticas. Con 'analito': evolución de ese analito con su rango de referencia. Sin parámetros: últimos resultados.",
    input_schema: {
      type: "object",
      properties: {
        analito: { type: "string", description: "Nombre del analito (p. ej. Calprotectina fecal, PCR)" },
        limit: { type: "integer", description: "Nº de resultados (por defecto 12)" },
      },
    },
  },
  {
    name: "query_finances",
    description:
      "Consulta finanzas: patrimonio neto agregado en CHF, saldos por cuenta, transacciones recientes y gasto por categoría.",
    input_schema: { type: "object", properties: {} },
  },
  // Escritura
  {
    name: "start_workout",
    description:
      "Inicia una sesión de entrenamiento de la rutina indicada. Devuelve el plan: ejercicios en orden con el peso y reps de la última vez.",
    input_schema: {
      type: "object",
      properties: { routine: { type: "string", description: "Push, Pull o Leg" } },
      required: ["routine"],
    },
  },
  {
    name: "log_set",
    description:
      "Registra una serie en la sesión de entrenamiento activa. Confirma siempre lo registrado.",
    input_schema: {
      type: "object",
      properties: {
        exercise: { type: "string", description: "Nombre del ejercicio" },
        peso: { type: "number", description: "Peso en kg" },
        reps: { type: "integer", description: "Repeticiones realizadas" },
        rpe: { type: "number", description: "RPE 1-10 (opcional)" },
      },
      required: ["exercise", "peso", "reps"],
    },
  },
  {
    name: "end_workout",
    description:
      "Cierra la sesión de entrenamiento activa y devuelve un resumen comparado con la sesión anterior de la misma rutina.",
    input_schema: { type: "object", properties: {} },
  },
  {
    name: "log_diary_entry",
    description:
      "Registra o actualiza la entrada de diario de salud (EII) de un día. Una entrada por fecha.",
    input_schema: {
      type: "object",
      properties: {
        fecha: { type: "string", description: "YYYY-MM-DD (por defecto hoy)" },
        bristol: { type: "integer", description: "Escala de Bristol 1-7" },
        dolor: { type: "integer", description: "Dolor 0-10" },
        urgencia: { type: "integer", description: "Urgencia 0-10" },
        sangre: { type: "boolean", description: "Sangre en heces" },
        num_deposiciones: { type: "integer", description: "Nº de deposiciones" },
        estres: { type: "integer", description: "Estrés 0-10" },
        notas: { type: "string" },
        meals: {
          type: "array",
          description: "Comidas del día",
          items: {
            type: "object",
            properties: {
              descripcion: { type: "string" },
              tags: {
                type: "array",
                items: { type: "string" },
                description: "lácteos, gluten, alcohol, picante, fodmap_alto",
              },
            },
            required: ["descripcion"],
          },
        },
      },
    },
  },
  {
    name: "log_flare",
    description: "Registra un brote (flare) de la EII.",
    input_schema: {
      type: "object",
      properties: {
        fecha_inicio: { type: "string", description: "YYYY-MM-DD (por defecto hoy)" },
        fecha_fin: { type: "string", description: "YYYY-MM-DD (opcional)" },
        severidad: { type: "integer", description: "0-10" },
        notas: { type: "string" },
      },
    },
  },
];

const today = () => new Date().toISOString().slice(0, 10);

// ── Resolución de ejercicio por nombre (tolerante) ──
async function resolveExercise(nombre: string) {
  const sb = getServerSupabase();
  const { data } = await sb
    .from("exercises")
    .select("id, nombre, grupo_muscular")
    .ilike("nombre", `%${nombre.trim()}%`)
    .limit(1);
  return data?.[0] ?? null;
}

// Última serie registrada de un ejercicio (en sesiones completadas anteriores)
async function lastSetFor(exerciseId: string, excludeWorkoutId?: string) {
  const sb = getServerSupabase();
  let q = sb
    .from("sets")
    .select("peso, reps, created_at, workouts!inner(fecha, completado)")
    .eq("exercise_id", exerciseId)
    .eq("workouts.completado", true)
    .order("created_at", { ascending: false })
    .limit(1);
  if (excludeWorkoutId) q = q.neq("workout_id", excludeWorkoutId);
  const { data } = await q;
  return data?.[0] ?? null;
}

// ── Dispatcher ──
export async function executeTool(
  name: string,
  input: Record<string, unknown>,
  ctx: ToolContext,
): Promise<string> {
  const sb = getServerSupabase();
  try {
    switch (name) {
      // ── Lectura ──────────────────────────────────────────────────────────
      case "query_workouts": {
        const limit = (input.limit as number) ?? 5;
        if (input.exercise) {
          const ex = await resolveExercise(input.exercise as string);
          if (!ex) return `No encuentro el ejercicio "${input.exercise}".`;
          const { data } = await sb
            .from("sets")
            .select("peso, reps, rpe, created_at, workouts!inner(fecha)")
            .eq("exercise_id", ex.id)
            .order("created_at", { ascending: false })
            .limit(limit * 4);
          return JSON.stringify({ ejercicio: ex.nombre, progresion: data });
        }
        if (input.routine) {
          const { data: rt } = await sb
            .from("routines")
            .select("id")
            .ilike("nombre", `${input.routine}`)
            .limit(1);
          const routineId = rt?.[0]?.id;
          const { data: w } = await sb
            .from("workouts")
            .select("id, fecha, completado, notas, sets(peso, reps, orden, exercise_id, exercises(nombre))")
            .eq("routine_id", routineId)
            .eq("completado", true)
            .order("fecha", { ascending: false })
            .limit(1);
          return JSON.stringify({ rutina: input.routine, ultima_sesion: w?.[0] ?? null });
        }
        const { data } = await sb
          .from("workouts")
          .select("id, fecha, completado, routines(nombre), sets(peso, reps, exercises(nombre))")
          .order("fecha", { ascending: false })
          .limit(limit);
        return JSON.stringify({ sesiones: data });
      }

      case "query_health": {
        const days = (input.days as number) ?? 30;
        const from = new Date(Date.now() - days * 864e5).toISOString().slice(0, 10);
        const { data: entries } = await sb
          .from("diary_entries")
          .select("*, meals(descripcion, tags)")
          .gte("fecha", from)
          .order("fecha", { ascending: false });
        const { data: flares } = await sb
          .from("flares")
          .select("*")
          .order("fecha_inicio", { ascending: false })
          .limit(10);
        return JSON.stringify({ desde: from, diario: entries, brotes: flares });
      }

      case "query_lab_results": {
        const limit = (input.limit as number) ?? 12;
        if (input.analito) {
          const { data } = await sb
            .from("lab_results")
            .select("analito, valor, unidad, ref_min, ref_max, lab_reports!inner(fecha, laboratorio)")
            .ilike("analito", `%${input.analito}%`)
            .order("lab_reports(fecha)", { ascending: false })
            .limit(limit);
          return JSON.stringify({ analito: input.analito, resultados: data });
        }
        const { data: report } = await sb
          .from("lab_reports")
          .select("id, fecha, laboratorio, lab_results(analito, valor, unidad, ref_min, ref_max)")
          .order("fecha", { ascending: false })
          .limit(1);
        return JSON.stringify({ ultimo_informe: report?.[0] ?? null });
      }

      case "query_finances": {
        const { data: accounts } = await sb.from("accounts").select("*");
        let patrimonioCHF = 0;
        const cuentas = [];
        for (const acc of accounts ?? []) {
          const { data: snap } = await sb
            .from("balance_snapshots")
            .select("saldo, fecha")
            .eq("account_id", acc.id)
            .order("fecha", { ascending: false })
            .limit(1);
          const saldo = snap?.[0]?.saldo ?? 0;
          const enCHF = acc.moneda === "EUR" ? saldo * EUR_TO_CHF : saldo;
          patrimonioCHF += enCHF;
          cuentas.push({ nombre: acc.nombre, moneda: acc.moneda, saldo, fecha: snap?.[0]?.fecha ?? null });
        }
        const from = new Date(Date.now() - 30 * 864e5).toISOString().slice(0, 10);
        const { data: tx } = await sb
          .from("transactions")
          .select("fecha, importe, descripcion, categoria")
          .gte("fecha", from)
          .order("fecha", { ascending: false })
          .limit(50);
        const porCategoria: Record<string, number> = {};
        for (const t of tx ?? []) {
          if (t.importe < 0) porCategoria[t.categoria ?? "otros"] = (porCategoria[t.categoria ?? "otros"] ?? 0) + Math.abs(t.importe);
        }
        return JSON.stringify({
          patrimonio_neto_chf: Math.round(patrimonioCHF * 100) / 100,
          cuentas,
          gasto_por_categoria_30d: porCategoria,
          transacciones_recientes: tx,
        });
      }

      // ── Escritura ────────────────────────────────────────────────────────
      case "start_workout": {
        const { data: rt } = await sb
          .from("routines")
          .select("id, nombre, exercise_ids")
          .ilike("nombre", `${input.routine}`)
          .limit(1);
        const routine = rt?.[0];
        if (!routine) return `No encuentro la rutina "${input.routine}".`;
        const { data: w } = await sb
          .from("workouts")
          .insert({ routine_id: routine.id, completado: false })
          .select("id")
          .single();
        await sb
          .from("chat_sessions")
          .update({ modo: "entreno", workout_id: w!.id })
          .eq("id", ctx.sessionId);
        // Plan en orden con referencia anterior
        const plan = [];
        for (const exId of routine.exercise_ids as string[]) {
          const { data: ex } = await sb.from("exercises").select("nombre").eq("id", exId).single();
          const last = await lastSetFor(exId, w!.id);
          plan.push({
            ejercicio: ex?.nombre,
            ultima_vez: last ? `${last.peso}kg×${last.reps}` : "sin registro previo",
          });
        }
        return JSON.stringify({ ok: true, rutina: routine.nombre, workout_id: w!.id, plan });
      }

      case "log_set": {
        const { data: sess } = await sb
          .from("chat_sessions")
          .select("workout_id")
          .eq("id", ctx.sessionId)
          .single();
        const workoutId = sess?.workout_id;
        if (!workoutId)
          return "No hay una sesión de entrenamiento activa. Pide a Oriol que indique qué rutina empieza (start_workout).";
        const ex = await resolveExercise(input.exercise as string);
        if (!ex) return `No encuentro el ejercicio "${input.exercise}".`;
        const { count } = await sb
          .from("sets")
          .select("*", { count: "exact", head: true })
          .eq("workout_id", workoutId);
        const { data: inserted } = await sb
          .from("sets")
          .insert({
            workout_id: workoutId,
            exercise_id: ex.id,
            peso: input.peso as number,
            reps: input.reps as number,
            rpe: (input.rpe as number) ?? null,
            orden: count ?? 0,
          })
          .select("peso, reps, rpe")
          .single();
        return JSON.stringify({ ok: true, registrado: { ejercicio: ex.nombre, ...inserted } });
      }

      case "end_workout": {
        const { data: sess } = await sb
          .from("chat_sessions")
          .select("workout_id")
          .eq("id", ctx.sessionId)
          .single();
        const workoutId = sess?.workout_id;
        if (!workoutId) return "No hay una sesión activa para cerrar.";
        const { data: w } = await sb
          .from("workouts")
          .update({ completado: true })
          .eq("id", workoutId)
          .select("id, routine_id, fecha, sets(peso, reps, orden, exercises(nombre))")
          .single();
        // Sesión anterior de la misma rutina
        const { data: prev } = await sb
          .from("workouts")
          .select("fecha, sets(peso, reps, exercises(nombre))")
          .eq("routine_id", w!.routine_id)
          .eq("completado", true)
          .neq("id", workoutId)
          .order("fecha", { ascending: false })
          .limit(1);
        await sb.from("chat_sessions").update({ workout_id: null }).eq("id", ctx.sessionId);
        return JSON.stringify({ ok: true, sesion_actual: w, sesion_anterior: prev?.[0] ?? null });
      }

      case "log_diary_entry": {
        const fecha = (input.fecha as string) ?? today();
        const fields: Record<string, unknown> = { fecha };
        for (const k of ["bristol", "dolor", "urgencia", "sangre", "num_deposiciones", "estres", "notas"]) {
          if (input[k] !== undefined) fields[k] = input[k];
        }
        const { data: entry } = await sb
          .from("diary_entries")
          .upsert(fields, { onConflict: "fecha" })
          .select("id")
          .single();
        const meals = (input.meals as Array<{ descripcion: string; tags?: string[] }>) ?? [];
        for (const m of meals) {
          await sb.from("meals").insert({
            diary_entry_id: entry!.id,
            descripcion: m.descripcion,
            tags: m.tags ?? [],
          });
        }
        return JSON.stringify({ ok: true, fecha, registrado: fields, comidas: meals.length });
      }

      case "log_flare": {
        const { data } = await sb
          .from("flares")
          .insert({
            fecha_inicio: (input.fecha_inicio as string) ?? today(),
            fecha_fin: (input.fecha_fin as string) ?? null,
            severidad: (input.severidad as number) ?? null,
            notas: (input.notas as string) ?? null,
          })
          .select("*")
          .single();
        return JSON.stringify({ ok: true, brote: data });
      }

      default:
        return `Herramienta desconocida: ${name}`;
    }
  } catch (err) {
    return `Error ejecutando ${name}: ${(err as Error).message}`;
  }
}
