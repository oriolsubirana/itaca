import "server-only";
import { getServerSupabase } from "./supabase";
import { EUR_TO_CHF } from "./anthropic";

const ROTATION = ["Push", "Pull", "Leg"];

export async function getTrainingSummary() {
  const sb = getServerSupabase();
  const { data: last } = await sb
    .from("workouts")
    .select("id, fecha, completado, routines(nombre)")
    .eq("completado", true)
    .order("fecha", { ascending: false })
    .limit(1);
  const rel = last?.[0]?.routines as unknown;
  const lastRoutine =
    (Array.isArray(rel) ? rel[0]?.nombre : (rel as { nombre?: string } | null)?.nombre) ?? null;
  const idx = lastRoutine ? ROTATION.indexOf(lastRoutine) : -1;
  const next = ROTATION[(idx + 1) % ROTATION.length];

  // Días con entrenamiento esta semana (lunes→hoy)
  const now = new Date();
  const day = (now.getDay() + 6) % 7; // 0 = lunes
  const monday = new Date(now);
  monday.setDate(now.getDate() - day);
  const { data: week } = await sb
    .from("workouts")
    .select("fecha")
    .gte("fecha", monday.toISOString().slice(0, 10));

  return {
    ultimaRutina: lastRoutine,
    ultimaFecha: last?.[0]?.fecha ?? null,
    proximaRutina: next,
    diasEstaSemana: new Set((week ?? []).map((w) => w.fecha)).size,
  };
}

export async function getNetWorth() {
  const sb = getServerSupabase();
  const { data: accounts } = await sb.from("accounts").select("*");
  let totalCHF = 0;
  let hasData = false;
  for (const acc of accounts ?? []) {
    const { data: snap } = await sb
      .from("balance_snapshots")
      .select("saldo, fecha")
      .eq("account_id", acc.id)
      .order("fecha", { ascending: false })
      .limit(1);
    if (snap?.[0]) {
      hasData = true;
      totalCHF += acc.moneda === "EUR" ? snap[0].saldo * EUR_TO_CHF : snap[0].saldo;
    }
  }
  return { totalCHF: hasData ? Math.round(totalCHF) : null };
}

export async function getRecentDiary(days = 30) {
  const sb = getServerSupabase();
  const from = new Date(Date.now() - days * 864e5).toISOString().slice(0, 10);
  const { data } = await sb
    .from("diary_entries")
    .select("*")
    .gte("fecha", from)
    .order("fecha", { ascending: true });
  return data ?? [];
}
