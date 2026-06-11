import Link from "next/link";
import { PageHeader } from "@/components/ui";
import { getServerSupabase } from "@/lib/supabase";
import { EUR_TO_CHF } from "@/lib/anthropic";
import FinanzasView from "@/components/FinanzasView";
import type { Account } from "@/lib/types";

export const dynamic = "force-dynamic";

const toCHF = (saldo: number, moneda: string) => (moneda === "EUR" ? saldo * EUR_TO_CHF : saldo);

export default async function FinanzasPage() {
  let accounts: Account[] = [];
  let netWorth: { fecha: string; total: number }[] = [];
  let porCategoria: { categoria: string; total: number }[] = [];
  let saldos: { id: string; nombre: string; moneda: string; saldo: number | null; fecha: string | null }[] = [];
  let tasaAhorro: number | null = null;
  let error = false;

  try {
    const sb = getServerSupabase();
    const [acc, snaps, tx] = await Promise.all([
      sb.from("accounts").select("*"),
      sb.from("balance_snapshots").select("account_id, fecha, saldo").order("fecha", { ascending: true }),
      sb.from("transactions").select("fecha, importe, categoria").gte("fecha", new Date(Date.now() - 30 * 864e5).toISOString().slice(0, 10)),
    ]);
    accounts = (acc.data as Account[]) ?? [];
    const monedaDe = new Map(accounts.map((a) => [a.id, a.moneda]));

    // Último saldo por cuenta
    const latest = new Map<string, { saldo: number; fecha: string }>();
    for (const s of snaps.data ?? []) latest.set(s.account_id, { saldo: s.saldo, fecha: s.fecha });
    saldos = accounts.map((a) => ({
      id: a.id,
      nombre: a.nombre,
      moneda: a.moneda,
      saldo: latest.get(a.id)?.saldo ?? null,
      fecha: latest.get(a.id)?.fecha ?? null,
    }));

    // Evolución del patrimonio neto (carry-forward por cuenta, en CHF)
    const running = new Map<string, number>();
    const byDate = new Map<string, number>();
    for (const s of snaps.data ?? []) {
      running.set(s.account_id, toCHF(s.saldo, monedaDe.get(s.account_id) ?? "CHF"));
      let total = 0;
      for (const v of running.values()) total += v;
      byDate.set(s.fecha, Math.round(total));
    }
    netWorth = Array.from(byDate.entries()).map(([fecha, total]) => ({ fecha: fecha.slice(0, 7), total }));

    // Gasto por categoría (30 días) + tasa de ahorro
    const cat = new Map<string, number>();
    let ingresos = 0;
    let gastos = 0;
    for (const t of tx.data ?? []) {
      if (t.importe < 0) {
        gastos += Math.abs(t.importe);
        cat.set(t.categoria ?? "otros", (cat.get(t.categoria ?? "otros") ?? 0) + Math.abs(t.importe));
      } else ingresos += t.importe;
    }
    porCategoria = Array.from(cat.entries())
      .map(([categoria, total]) => ({ categoria, total: Math.round(total) }))
      .sort((a, b) => b.total - a.total);
    tasaAhorro = ingresos > 0 ? Math.round(((ingresos - gastos) / ingresos) * 100) : null;
  } catch {
    error = true;
  }

  return (
    <>
      <PageHeader
        title="Finanzas"
        subtitle="Patrimonio · CHF"
        action={
          <Link href="/finanzas/importar" className="tap rounded-full bg-accent px-3 text-sm text-accent-fg grid place-items-center">
            + CSV
          </Link>
        }
      />
      <div className="px-4 py-4 pb-6">
        {error ? (
          <p className="text-sm text-danger">Configura Supabase para ver tus finanzas.</p>
        ) : (
          <FinanzasView
            netWorth={netWorth}
            porCategoria={porCategoria}
            saldos={saldos}
            tasaAhorro={tasaAhorro}
          />
        )}
      </div>
    </>
  );
}
