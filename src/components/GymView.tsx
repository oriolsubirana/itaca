"use client";

import { useMemo, useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card } from "./ui";

interface FlatSet {
  ejercicio: string;
  peso: number;
  reps: number;
  fecha: string;
}
interface WorkoutItem {
  id: string;
  fecha: string;
  completado: boolean;
  rutina: string;
  series: { ejercicio: string; peso: number; reps: number }[];
}

export default function GymView({ sets, workouts }: { sets: FlatSet[]; workouts: WorkoutItem[] }) {
  const exercises = useMemo(() => Array.from(new Set(sets.map((s) => s.ejercicio))).sort(), [sets]);
  const [sel, setSel] = useState(exercises[0] ?? "");

  // Progresión: mejor serie (máx peso) por día para el ejercicio elegido
  const progression = useMemo(() => {
    const byDay = new Map<string, number>();
    for (const s of sets) {
      if (s.ejercicio !== sel) continue;
      byDay.set(s.fecha, Math.max(byDay.get(s.fecha) ?? 0, s.peso));
    }
    return Array.from(byDay.entries())
      .map(([fecha, peso]) => ({ fecha: fecha.slice(5), peso }))
      .sort((a, b) => a.fecha.localeCompare(b.fecha));
  }, [sets, sel]);

  // Frecuencia: sesiones por semana (últimas 8)
  const weekly = useMemo(() => {
    const counts = new Map<string, number>();
    for (const w of workouts) {
      const d = new Date(w.fecha);
      const day = (d.getDay() + 6) % 7;
      const monday = new Date(d);
      monday.setDate(d.getDate() - day);
      const key = monday.toISOString().slice(0, 10);
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    return Array.from(counts.entries()).sort((a, b) => a[0].localeCompare(b[0])).slice(-8);
  }, [workouts]);

  if (sets.length === 0)
    return (
      <Card>
        <p className="text-sm text-muted">
          Aún no hay series registradas. Ve al chat y di &ldquo;empiezo push&rdquo; para empezar.
        </p>
      </Card>
    );

  return (
    <div className="space-y-4">
      {/* Progresión por ejercicio */}
      <Card>
        <div className="mb-2 flex items-center justify-between gap-2">
          <p className="text-xs uppercase tracking-wide text-muted">Progresión de carga</p>
          <select
            value={sel}
            onChange={(e) => setSel(e.target.value)}
            className="tap rounded-lg border border-line bg-background px-2 text-sm"
          >
            {exercises.map((e) => (
              <option key={e}>{e}</option>
            ))}
          </select>
        </div>
        <ResponsiveContainer width="100%" height={180}>
          <LineChart data={progression} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
            <CartesianGrid stroke="var(--color-line)" vertical={false} />
            <XAxis dataKey="fecha" tick={{ fontSize: 11, fill: "var(--color-muted)" }} />
            <YAxis tick={{ fontSize: 11, fill: "var(--color-muted)" }} unit="kg" width={42} />
            <Tooltip
              contentStyle={{ borderRadius: 12, border: "1px solid var(--color-line)", fontSize: 13 }}
              formatter={(v) => [`${v} kg`, "Peso"]}
            />
            <Line type="monotone" dataKey="peso" stroke="var(--color-accent)" strokeWidth={2} dot={{ r: 3 }} isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      </Card>

      {/* Frecuencia semanal */}
      <Card>
        <p className="mb-3 text-xs uppercase tracking-wide text-muted">Frecuencia semanal</p>
        <div className="flex items-end gap-2" style={{ height: 70 }}>
          {weekly.map(([week, n]) => (
            <div key={week} className="flex flex-1 flex-col items-center justify-end gap-1">
              <div className="w-full rounded-t bg-accent" style={{ height: `${Math.min(n, 4) * 22}%` }} />
              <span className="text-[10px] text-muted">{week.slice(5)}</span>
            </div>
          ))}
        </div>
      </Card>

      {/* Historial de sesiones */}
      <div>
        <p className="mb-2 px-1 text-xs uppercase tracking-wide text-muted">Historial</p>
        <div className="space-y-2">
          {workouts.map((w) => (
            <Card key={w.id}>
              <div className="flex items-center justify-between">
                <p className="font-medium">{w.rutina}</p>
                <p className="text-sm text-muted">
                  {w.fecha} {!w.completado && "· en curso"}
                </p>
              </div>
              <ul className="mt-2 space-y-0.5 text-sm text-muted">
                {w.series.map((s, i) => (
                  <li key={i} className="flex justify-between">
                    <span>{s.ejercicio}</span>
                    <span className="tabular-nums text-foreground">
                      {s.peso}kg × {s.reps}
                    </span>
                  </li>
                ))}
              </ul>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}
