"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Account } from "@/lib/types";

interface Tx {
  fecha: string;
  importe: number;
  descripcion: string;
  categoria: string;
}

export default function ImportCSV({ accounts }: { accounts: Account[] }) {
  const router = useRouter();
  const [accountId, setAccountId] = useState(accounts[0]?.id ?? "");
  const [csv, setCsv] = useState("");
  const [rows, setRows] = useState<Tx[] | null>(null);
  const [banco, setBanco] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (f) setCsv(await f.text());
  }

  async function detect() {
    if (!csv.trim()) return;
    setLoading(true);
    setError("");
    try {
      const res = await fetch("/api/finance/import", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ csv }),
      });
      if (!res.ok) throw new Error((await res.json()).error ?? "Error");
      const d = await res.json();
      setBanco(d.banco);
      setRows(d.transacciones ?? []);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }

  async function save() {
    setSaving(true);
    try {
      const res = await fetch("/api/finance/save", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ accountId, transacciones: rows }),
      });
      if (!res.ok) throw new Error((await res.json()).error ?? "Error");
      router.push("/finanzas");
    } catch (err) {
      setError((err as Error).message);
      setSaving(false);
    }
  }

  return (
    <div className="px-4 py-4 pb-6">
      <header className="mb-4 flex items-center gap-3" style={{ paddingTop: "max(1rem, env(safe-area-inset-top))" }}>
        <button onClick={() => router.back()} className="tap text-muted" aria-label="Volver">
          ←
        </button>
        <h1 className="text-xl font-medium tracking-tight">Importar CSV</h1>
      </header>

      {!rows && (
        <div className="space-y-3">
          <label className="block text-sm">
            <span className="text-muted">Cuenta</span>
            <select
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
              className="tap mt-1 w-full rounded-lg border border-line bg-surface px-2"
            >
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.nombre} ({a.moneda})
                </option>
              ))}
            </select>
          </label>

          <label className="block cursor-pointer rounded-2xl border border-dashed border-line bg-surface p-4 text-center text-sm text-muted">
            <input type="file" accept=".csv,text/csv" className="hidden" onChange={onFile} />
            Elegir archivo CSV
          </label>

          <textarea
            value={csv}
            onChange={(e) => setCsv(e.target.value)}
            placeholder="…o pega aquí el contenido del extracto"
            rows={6}
            className="w-full rounded-xl border border-line bg-surface p-3 text-sm"
          />

          <button
            onClick={detect}
            disabled={loading || !csv.trim() || !accountId}
            className="tap w-full rounded-full bg-accent py-3 font-medium text-accent-fg disabled:opacity-40"
          >
            {loading ? "Claude está leyendo el extracto…" : "Detectar y categorizar"}
          </button>
        </div>
      )}

      {error && <p className="mt-3 text-sm text-danger">{error}</p>}

      {rows && (
        <div className="space-y-3">
          <p className="text-sm text-muted">
            {banco ? `${banco} · ` : ""}
            {rows.length} movimientos. Revisa antes de guardar.
          </p>
          <div className="space-y-2">
            {rows.map((t, i) => (
              <div key={i} className="rounded-xl border border-line bg-surface p-3 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted">{t.fecha}</span>
                  <span className={`tabular-nums ${t.importe < 0 ? "text-danger" : "text-ok"}`}>
                    {t.importe.toLocaleString("de-CH")}
                  </span>
                </div>
                <p className="mt-0.5">{t.descripcion}</p>
                <input
                  value={t.categoria}
                  onChange={(e) =>
                    setRows(rows.map((r, idx) => (idx === i ? { ...r, categoria: e.target.value } : r)))
                  }
                  className="mt-1.5 w-full rounded-lg border border-line bg-background px-2 py-1 text-xs capitalize"
                />
              </div>
            ))}
          </div>
          <button
            onClick={save}
            disabled={saving}
            className="tap w-full rounded-full bg-accent py-3 font-medium text-accent-fg disabled:opacity-40"
          >
            {saving ? "Guardando…" : `Guardar ${rows.length} movimientos`}
          </button>
        </div>
      )}
    </div>
  );
}
