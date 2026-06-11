"use client";

import { useMemo, useState } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceArea,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Card } from "./ui";

interface LabPoint {
  analito: string;
  valor: number | null;
  unidad: string | null;
  ref_min: number | null;
  ref_max: number | null;
  fecha: string;
}
interface Flare {
  fecha_inicio: string;
  fecha_fin: string | null;
  severidad: number | null;
}

export default function SaludView({
  labResults,
  diary,
  flares,
}: {
  labResults: LabPoint[];
  diary: Record<string, unknown>[];
  flares: Flare[];
}) {
  const analitos = useMemo(
    () => Array.from(new Set(labResults.map((r) => r.analito))).sort(),
    [labResults],
  );
  const [sel, setSel] = useState(
    analitos.find((a) => a.toLowerCase().includes("calprotectina")) ?? analitos[0] ?? "",
  );

  const serie = useMemo(() => {
    return labResults
      .filter((r) => r.analito === sel && r.valor != null)
      .map((r) => ({ ...r, fechaCorta: r.fecha.slice(0, 7) }))
      .sort((a, b) => a.fecha.localeCompare(b.fecha));
  }, [labResults, sel]);

  const ref = serie[0];

  return (
    <div className="space-y-4">
      {/* Gráfica por analito con rango de referencia y brotes superpuestos */}
      {analitos.length > 0 ? (
        <Card>
          <div className="mb-2 flex items-center justify-between gap-2">
            <p className="text-xs uppercase tracking-wide text-muted">Analítica</p>
            <select
              value={sel}
              onChange={(e) => setSel(e.target.value)}
              className="tap max-w-[60%] rounded-lg border border-line bg-background px-2 text-sm"
            >
              {analitos.map((a) => (
                <option key={a}>{a}</option>
              ))}
            </select>
          </div>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={serie} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
              <CartesianGrid stroke="var(--color-line)" vertical={false} />
              {/* Rango de referencia sombreado */}
              {ref?.ref_min != null && ref?.ref_max != null && (
                <ReferenceArea
                  y1={ref.ref_min}
                  y2={ref.ref_max}
                  fill="var(--color-accent)"
                  fillOpacity={0.1}
                  stroke="none"
                />
              )}
              <XAxis dataKey="fechaCorta" tick={{ fontSize: 11, fill: "var(--color-muted)" }} />
              <YAxis tick={{ fontSize: 11, fill: "var(--color-muted)" }} width={42} />
              <Tooltip
                contentStyle={{ borderRadius: 12, border: "1px solid var(--color-line)", fontSize: 13 }}
                formatter={(v) => [`${v} ${ref?.unidad ?? ""}`, sel]}
              />
              <Line type="monotone" dataKey="valor" stroke="var(--color-accent)" strokeWidth={2} dot={{ r: 3 }} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
          {ref?.ref_min != null || ref?.ref_max != null ? (
            <p className="mt-1 text-xs text-muted">
              Referencia: {ref?.ref_min ?? "—"}–{ref?.ref_max ?? "—"} {ref?.unidad}
            </p>
          ) : null}
        </Card>
      ) : (
        <Card>
          <p className="text-sm text-muted">
            Aún no hay analíticas. Pulsa &ldquo;+ Analítica&rdquo; para subir un PDF.
          </p>
        </Card>
      )}

      {/* Brotes */}
      {flares.length > 0 && (
        <Card>
          <p className="mb-2 text-xs uppercase tracking-wide text-muted">Brotes</p>
          <ul className="space-y-1 text-sm">
            {flares.map((f, i) => (
              <li key={i} className="flex justify-between">
                <span>
                  {f.fecha_inicio} → {f.fecha_fin ?? "en curso"}
                </span>
                {f.severidad != null && <span className="text-muted">sev. {f.severidad}</span>}
              </li>
            ))}
          </ul>
        </Card>
      )}

      {/* Diario reciente */}
      <div>
        <p className="mb-2 px-1 text-xs uppercase tracking-wide text-muted">Diario reciente</p>
        {diary.length === 0 ? (
          <Card>
            <p className="text-sm text-muted">
              Sin registros. Desde el chat: &ldquo;hoy bristol 4, sin dolor, 2 deposiciones&rdquo;.
            </p>
          </Card>
        ) : (
          <div className="space-y-2">
            {diary.map((d, i) => (
              <Card key={i}>
                <div className="flex items-center justify-between">
                  <p className="font-medium">{d.fecha as string}</p>
                  <div className="flex gap-2 text-sm text-muted">
                    {d.bristol != null && <span>Bristol {d.bristol as number}</span>}
                    {d.num_deposiciones != null && <span>· {d.num_deposiciones as number} dep.</span>}
                    {(d.sangre as boolean) && <span className="text-danger">· sangre</span>}
                  </div>
                </div>
                <div className="mt-1 flex flex-wrap gap-2 text-xs text-muted">
                  {d.dolor != null && <span>dolor {d.dolor as number}/10</span>}
                  {d.urgencia != null && <span>urgencia {d.urgencia as number}/10</span>}
                  {d.estres != null && <span>estrés {d.estres as number}/10</span>}
                </div>
                {d.notas ? <p className="mt-1 text-sm">{d.notas as string}</p> : null}
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
