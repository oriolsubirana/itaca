import { NextResponse } from "next/server";
import { getServerSupabase } from "@/lib/supabase";

export const runtime = "nodejs";

// POST /api/finance/balance  { accountId, fecha?, saldo }  → upsert snapshot del día
export async function POST(req: Request) {
  const { accountId, fecha, saldo } = (await req.json()) as {
    accountId: string;
    fecha?: string;
    saldo: number;
  };
  if (!accountId || saldo == null) {
    return NextResponse.json({ error: "Faltan datos" }, { status: 400 });
  }
  const sb = getServerSupabase();
  const { error } = await sb.from("balance_snapshots").upsert(
    { account_id: accountId, fecha: fecha ?? new Date().toISOString().slice(0, 10), saldo },
    { onConflict: "account_id,fecha" },
  );
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  return NextResponse.json({ ok: true });
}
