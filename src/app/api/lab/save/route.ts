import { NextResponse } from "next/server";
import { getServerSupabase } from "@/lib/supabase";
import type { RawResult } from "@/lib/lab";

export const runtime = "nodejs";

// POST /api/lab/save  { fecha, laboratorio, resultados[] }
export async function POST(req: Request) {
  const { fecha, laboratorio, resultados } = (await req.json()) as {
    fecha: string;
    laboratorio: string | null;
    resultados: RawResult[];
  };
  if (!fecha) return NextResponse.json({ error: "Falta la fecha" }, { status: 400 });

  const sb = getServerSupabase();
  const { data: report, error } = await sb
    .from("lab_reports")
    .insert({ fecha, laboratorio })
    .select("id")
    .single();
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });

  const rows = (resultados ?? [])
    .filter((r) => r.analito)
    .map((r) => ({
      lab_report_id: report!.id,
      analito: r.analito,
      valor: r.valor,
      unidad: r.unidad,
      ref_min: r.ref_min,
      ref_max: r.ref_max,
    }));
  if (rows.length) await sb.from("lab_results").insert(rows);

  return NextResponse.json({ ok: true, report_id: report!.id, insertados: rows.length });
}
