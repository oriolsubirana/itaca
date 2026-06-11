import { NextResponse } from "next/server";
import { getServerSupabase } from "@/lib/supabase";

export const runtime = "nodejs";

interface Tx {
  fecha: string;
  importe: number;
  descripcion: string;
  categoria: string;
}

// POST /api/finance/save  { accountId, transacciones[] }
export async function POST(req: Request) {
  const { accountId, transacciones } = (await req.json()) as {
    accountId: string;
    transacciones: Tx[];
  };
  if (!accountId) return NextResponse.json({ error: "Falta la cuenta" }, { status: 400 });

  const sb = getServerSupabase();
  const rows = (transacciones ?? []).map((t) => ({
    account_id: accountId,
    fecha: t.fecha,
    importe: t.importe,
    descripcion: t.descripcion,
    categoria: t.categoria,
  }));
  if (rows.length) {
    const { error } = await sb.from("transactions").insert(rows);
    if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  }
  return NextResponse.json({ ok: true, insertados: rows.length });
}
