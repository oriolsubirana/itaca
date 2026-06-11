"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card, Stat } from "./ui";

interface Saldo {
  id: string;
  nombre: string;
  moneda: string;
  saldo: number | null;
  fecha: string | null;
}

export default function FinanzasView({
  netWorth,
  porCategoria,
  saldos,
  tasaAhorro,
}: {
  netWorth: { fecha: string; total: number }[];
  porCategoria: { categoria: string; total: number }[];
  saldos: Saldo[];
  tasaAhorro: number | null;
}) {
  const router = useRouter();
  const total = netWorth.at(-1)?.total ?? null;
  const prev = netWorth.at(-2)?.total ?? null;
  const variacion = total != null && prev != null ? total - prev : null;
  const maxCat = Math.max(1, ...porCategoria.map((c) => c.total));
  const [editing, setEditing] = useState<string | null>(null);

  async function saveBalance(accountId: string, value: string) {
    const saldo = Number(value);
    if (Number.isNaN(saldo)) return;
    await fetch("/api/finance/balance", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ accountId, saldo }),
    });
    setEditing(null);
    router.refresh();
  }

  return (
    <div className="space-y-4">
      <Card>
        <div className="flex items-center justify-between">
          <Stat
            label="Patrimonio neto"
            value={total != null ? `${total.toLocaleString("de-CH")} CHF` : "—"}
            hint={
              variacion != null
                ? `${variacion >= 0 ? "+" : ""}${variacion.toLocaleString("de-CH")} CHF vs. mes anterior`
                : "Añade saldos para empezar"
            }
          />
          {tasaAhorro != null && <Stat label="Tasa de ahorro" value={`${tasaAhorro}%`} hint="últimos 30 días" />}
        </div>
        {netWorth.length > 1 && (
          <div className="mt-3">
            <ResponsiveContainer width="100%" height={140}>
              <AreaChart data={netWorth} margin={{ top: 4, right: 4, bottom: 0, left: -16 }}>
                <defs>
                  <linearGradient id="nw" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-accent)" stopOpacity={0.3} />
                    <stop offset="100%" stopColor="var(--color-accent)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="var(--color-line)" vertical={false} />
                <XAxis dataKey="fecha" tick={{ fontSize: 11, fill: "var(--color-muted)" }} />
                <YAxis tick={{ fontSize: 11, fill: "var(--color-muted)" }} width={44} />
                <Tooltip
                  contentStyle={{ borderRadius: 12, border: "1px solid var(--color-line)", fontSize: 13 }}
                  formatter={(v) => [`${Number(v).toLocaleString("de-CH")} CHF`, "Patrimonio"]}
                />
                <Area type="monotone" dataKey="total" stroke="var(--color-accent)" strokeWidth={2} fill="url(#nw)" isAnimationActive={false} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>

      {/* Saldos por cuenta (editable) */}
      <Card>
        <p className="mb-2 text-xs uppercase tracking-wide text-muted">Cuentas</p>
        <ul className="divide-y divide-line">
          {saldos.map((s) => (
            <li key={s.id} className="flex items-center justify-between py-2.5">
              <div>
                <p className="font-medium">{s.nombre}</p>
                <p className="text-xs text-muted">
                  {s.moneda}
                  {s.fecha ? ` · ${s.fecha}` : ""}
                </p>
              </div>
              {editing === s.id ? (
                <input
                  autoFocus
                  inputMode="decimal"
                  defaultValue={s.saldo ?? ""}
                  onBlur={(e) => saveBalance(s.id, e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && saveBalance(s.id, (e.target as HTMLInputElement).value)}
                  className="tap w-28 rounded-lg border border-accent bg-background px-2 text-right tabular-nums"
                />
              ) : (
                <button onClick={() => setEditing(s.id)} className="tap text-right tabular-nums">
                  {s.saldo != null ? s.saldo.toLocaleString("de-CH") : "—"}
                  <span className="ml-1 text-xs text-muted">{s.moneda}</span>
                </button>
              )}
            </li>
          ))}
        </ul>
        <p className="mt-1 text-xs text-muted">Toca un saldo para actualizarlo.</p>
      </Card>

      {/* Gasto por categoría */}
      {porCategoria.length > 0 && (
        <Card>
          <p className="mb-3 text-xs uppercase tracking-wide text-muted">Gasto por categoría · 30 días</p>
          <ul className="space-y-2">
            {porCategoria.map((c) => (
              <li key={c.categoria}>
                <div className="mb-1 flex justify-between text-sm">
                  <span className="capitalize">{c.categoria}</span>
                  <span className="tabular-nums text-muted">{c.total.toLocaleString("de-CH")}</span>
                </div>
                <div className="h-2 rounded-full bg-line">
                  <div className="h-2 rounded-full bg-accent" style={{ width: `${(c.total / maxCat) * 100}%` }} />
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
