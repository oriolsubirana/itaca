"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

interface Row {
  analito: string;
  valor: number | null;
  unidad: string | null;
  ref_min: number | null;
  ref_max: number | null;
}

export default function AnaliticaPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [fecha, setFecha] = useState("");
  const [lab, setLab] = useState("");
  const [rows, setRows] = useState<Row[] | null>(null);
  const [saving, setSaving] = useState(false);

  async function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setLoading(true);
    setError("");
    try {
      const fd = new FormData();
      fd.append("file", file);
      const res = await fetch("/api/lab/extract", { method: "POST", body: fd });
      if (!res.ok) throw new Error((await res.json()).error ?? "Error");
      const d = await res.json();
      setFecha(d.fecha ?? "");
      setLab(d.laboratorio ?? "");
      setRows(d.resultados ?? []);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }

  function update(i: number, patch: Partial<Row>) {
    setRows((r) => r!.map((row, idx) => (idx === i ? { ...row, ...patch } : row)));
  }

  async function save() {
    setSaving(true);
    try {
      const res = await fetch("/api/lab/save", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ fecha, laboratorio: lab, resultados: rows }),
      });
      if (!res.ok) throw new Error((await res.json()).error ?? "Error");
      router.push("/salud");
    } catch (err) {
      setError((err as Error).message);
      setSaving(false);
    }
  }

  const num = (v: string) => (v === "" ? null : Number(v));

  return (
    <div className="px-4 py-4 pb-6">
      <header className="mb-4 flex items-center gap-3" style={{ paddingTop: "max(1rem, env(safe-area-inset-top))" }}>
        <button onClick={() => router.back()} className="tap text-muted" aria-label="Volver">
          ←
        </button>
        <h1 className="text-xl font-medium tracking-tight">Subir analítica</h1>
      </header>

      {!rows && (
        <label className="block cursor-pointer rounded-2xl border border-dashed border-line bg-surface p-8 text-center">
          <input type="file" accept="application/pdf" className="hidden" onChange={onFile} />
          <p className="text-sm text-muted">
            {loading ? "Leyendo el PDF con Claude…" : "Toca para elegir el PDF de tu analítica"}
          </p>
        </label>
      )}

      {error && <p className="mt-3 text-sm text-danger">{error}</p>}

      {rows && (
        <div className="space-y-4">
          <p className="text-sm text-muted">Revisa y corrige antes de guardar.</p>
          <div className="grid grid-cols-2 gap-2">
            <label className="text-sm">
              <span className="text-muted">Fecha</span>
              <input
                type="date"
                value={fecha}
                onChange={(e) => setFecha(e.target.value)}
                className="tap mt-1 w-full rounded-lg border border-line bg-surface px-2"
              />
            </label>
            <label className="text-sm">
              <span className="text-muted">Laboratorio</span>
              <input
                value={lab}
                onChange={(e) => setLab(e.target.value)}
                className="tap mt-1 w-full rounded-lg border border-line bg-surface px-2"
              />
            </label>
          </div>

          <div className="space-y-2">
            {rows.map((r, i) => (
              <div key={i} className="rounded-xl border border-line bg-surface p-3">
                <div className="flex items-center gap-2">
                  <input
                    value={r.analito}
                    onChange={(e) => update(i, { analito: e.target.value })}
                    className="flex-1 rounded-lg border border-line bg-background px-2 py-1.5 text-sm font-medium"
                  />
                  <button onClick={() => setRows(rows.filter((_, idx) => idx !== i))} className="tap text-danger" aria-label="Eliminar">
                    ×
                  </button>
                </div>
                <div className="mt-2 grid grid-cols-4 gap-2 text-sm">
                  <input
                    inputMode="decimal"
                    placeholder="valor"
                    defaultValue={r.valor ?? ""}
                    onChange={(e) => update(i, { valor: num(e.target.value) })}
                    className="rounded-lg border border-line bg-background px-2 py-1.5"
                  />
                  <input
                    placeholder="unidad"
                    defaultValue={r.unidad ?? ""}
                    onChange={(e) => update(i, { unidad: e.target.value || null })}
                    className="rounded-lg border border-line bg-background px-2 py-1.5"
                  />
                  <input
                    inputMode="decimal"
                    placeholder="mín"
                    defaultValue={r.ref_min ?? ""}
                    onChange={(e) => update(i, { ref_min: num(e.target.value) })}
                    className="rounded-lg border border-line bg-background px-2 py-1.5"
                  />
                  <input
                    inputMode="decimal"
                    placeholder="máx"
                    defaultValue={r.ref_max ?? ""}
                    onChange={(e) => update(i, { ref_max: num(e.target.value) })}
                    className="rounded-lg border border-line bg-background px-2 py-1.5"
                  />
                </div>
              </div>
            ))}
          </div>

          <button
            onClick={save}
            disabled={saving || !fecha}
            className="tap w-full rounded-full bg-accent py-3 font-medium text-accent-fg disabled:opacity-40"
          >
            {saving ? "Guardando…" : `Guardar ${rows.length} resultados`}
          </button>
        </div>
      )}
    </div>
  );
}
