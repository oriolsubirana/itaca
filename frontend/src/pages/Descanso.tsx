import { useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { getWellness, sleepLabel, type WellnessDay } from "../api/wellness";
import { MES } from "../lib/format";

const HRV_STATUS: Record<string, string> = {
  BALANCED: "Equilibrada",
  LOW: "Baja",
  UNBALANCED: "Inestable",
  POOR: "Mala",
};

const PHASES: { key: string; get: (d: WellnessDay) => number | null; cls: string }[] = [
  { key: "Profundo", get: (d) => d.deepMinutes, cls: "bg-ink" },
  { key: "Ligero", get: (d) => d.lightMinutes, cls: "bg-ink/35" },
  { key: "REM", get: (d) => d.remMinutes, cls: "bg-ink/60" },
  { key: "Despierto", get: (d) => d.awakeMinutes, cls: "bg-line" },
];

const num = (n: number | null | undefined) => (n != null ? n.toLocaleString("de-DE") : "—");
const dayDate = (iso: string) => {
  const d = new Date(`${iso}T00:00:00`);
  return `${d.getDate()} ${MES[d.getMonth()]}`;
};
const weekday = (iso: string) =>
  ["dom", "lun", "mar", "mié", "jue", "vie", "sáb"][new Date(`${iso}T00:00:00`).getDay()];

export function Descanso() {
  const navigate = useNavigate();
  const wellness = useQuery({ queryKey: ["wellness-full"], queryFn: () => getWellness(14) });

  const data = wellness.data;
  const last = data?.days?.[0];

  return (
    <div>
      <header className="-mt-1 flex items-center justify-between gap-2 border-b border-line pb-3">
        <div className="flex items-center gap-2">
          <button
            onClick={() => void navigate({ to: "/" })}
            aria-label="Volver"
            className="-ml-2 flex size-9 items-center justify-center rounded-full text-ink hover:bg-line/50"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="size-5">
              <path d="M15 6l-6 6 6 6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">Descanso</h1>
        </div>
        <button
          onClick={() =>
            void navigate({ to: "/chat", search: { seed: "Según mi descanso de hoy, ¿cómo estoy y qué entreno y comida me recomiendas?" } })
          }
          className="rounded-full border border-line px-3.5 py-1.5 text-[13px] text-ink-soft hover:text-ink"
        >
          Preguntar a Ítaca
        </button>
      </header>

      {!last ? (
        <p className="pt-12 text-center text-sm text-ink-soft">
          Aún no hay datos de Garmin. Configura el sync para ver tu descanso.
        </p>
      ) : (
        <div className="space-y-9 pt-5">
          <Anoche d={last} />
          <div className="border-t border-line" />
          <Recuperacion d={last} />
          <div className="border-t border-line" />
          <Tendencia days={data!.days} avg={data!} />
          {data!.days.length > 1 && (
            <>
              <div className="border-t border-line" />
              <Previos days={data!.days.slice(1)} />
            </>
          )}
          <p className="text-[11.5px] leading-relaxed text-ink-soft">
            Datos de Garmin. Ítaca los registra y muestra; no son diagnóstico ni consejo médico.
          </p>
        </div>
      )}
    </div>
  );
}

function SecLabel({ children }: { children: string }) {
  return <h2 className="mb-4 text-xs uppercase tracking-[0.13em] text-ink-soft">{children}</h2>;
}

function Anoche({ d }: { d: WellnessDay }) {
  const phases = PHASES.map((p) => ({ ...p, min: p.get(d) })).filter((p) => p.min != null) as {
    key: string;
    cls: string;
    min: number;
  }[];
  const total = phases.reduce((s, p) => s + p.min, 0) || 1;
  const bedMin = d.sleepMinutes != null ? d.sleepMinutes + (d.awakeMinutes ?? 0) : null;

  return (
    <section>
      <SecLabel>Anoche</SecLabel>
      <div className="flex items-end justify-between">
        <span className="whitespace-nowrap text-[40px] font-semibold leading-none tracking-[-0.02em] tabular-nums text-ink">
          {sleepLabel(d.sleepMinutes)}
        </span>
        {d.sleepScore != null && (
          <div className="text-right">
            <div className="text-[26px] font-semibold leading-none tabular-nums text-ink">{d.sleepScore}</div>
            <div className="mt-1 text-[11px] uppercase tracking-[0.08em] text-ink-soft">Sleep score</div>
          </div>
        )}
      </div>
      {bedMin != null && (
        <div className="mt-2.5 text-[13px] text-ink-soft">Tiempo dormido · de {sleepLabel(bedMin)} en cama</div>
      )}

      {phases.length > 0 && (
        <>
          <div className="mt-5 flex h-3 gap-px overflow-hidden rounded-full">
            {phases.map((p) => (
              <div key={p.key} className={p.cls} style={{ width: `${(p.min / total) * 100}%` }} />
            ))}
          </div>
          <div className="mt-4 grid grid-cols-2 gap-x-6 gap-y-2.5">
            {phases.map((p) => (
              <div key={p.key} className="flex items-center justify-between">
                <span className="flex items-center gap-2 text-[13px] text-ink">
                  <span
                    className={`size-[8px] shrink-0 rounded-[2px] ${p.cls === "bg-line" ? "border border-ink-soft/40" : p.cls}`}
                  />
                  {p.key}
                </span>
                <span className="text-[13px] tabular-nums text-ink-soft">{p.min} min</span>
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

function Metric({ value, unit, label, sub }: { value: string; unit?: string; label: string; sub?: string }) {
  return (
    <div className="rounded-[8px] border border-line px-4 py-3.5">
      <div className="flex items-baseline gap-1">
        <span className="text-[24px] font-semibold leading-none tabular-nums text-ink">{value}</span>
        {unit && <span className="text-[12px] text-ink-soft">{unit}</span>}
      </div>
      <div className="mt-2 text-[11px] uppercase tracking-[0.06em] text-ink-soft">{label}</div>
      {sub && <div className="mt-0.5 text-[12px] text-ink/70">{sub}</div>}
    </div>
  );
}

function Recuperacion({ d }: { d: WellnessDay }) {
  const bb = d.bodyBatteryHigh != null || d.bodyBatteryLow != null ? `${num(d.bodyBatteryHigh)}→${num(d.bodyBatteryLow)}` : "—";
  return (
    <section>
      <SecLabel>HRV y recuperación</SecLabel>
      <div className="grid grid-cols-2 gap-2.5">
        <Metric
          value={num(d.hrvAvgMs)}
          unit={d.hrvAvgMs != null ? "ms" : ""}
          label="HRV nocturna"
          sub={d.hrvStatus ? (HRV_STATUS[d.hrvStatus] ?? d.hrvStatus) : undefined}
        />
        <Metric value={num(d.restingHr)} unit={d.restingHr != null ? "ppm" : ""} label="FC en reposo" />
        <Metric value={num(d.stressAvg)} label="Estrés medio" sub="0–100" />
        <Metric value={bb} label="Body Battery" sub="máx → mín" />
        <Metric
          value={d.respirationAvg != null ? d.respirationAvg.toLocaleString("de-DE", { minimumFractionDigits: 1 }) : "—"}
          unit={d.respirationAvg != null ? "resp/min" : ""}
          label="Respiración"
        />
        <Metric value={num(d.spo2Avg)} unit={d.spo2Avg != null ? "%" : ""} label="SpO₂ medio" />
        <Metric value={num(d.steps)} label="Pasos" />
        <Metric value={num(d.activeCalories)} unit={d.activeCalories != null ? "kcal" : ""} label="Calorías activas" />
      </div>
    </section>
  );
}

function Spark({ values }: { values: number[] }) {
  const w = 96;
  const h = 36;
  let mn = Math.min(...values);
  let mx = Math.max(...values);
  const pad = (mx - mn) * 0.25 || 1;
  mn -= pad;
  mx += pad;
  const X = (i: number) => 2 + i * ((w - 4) / (values.length - 1));
  const Y = (v: number) => 3 + (1 - (v - mn) / (mx - mn)) * (h - 6);
  const line = values.map((v, i) => `${X(i).toFixed(1)},${Y(v).toFixed(1)}`).join(" ");
  const area = `2,${h - 1} ${line} ${(w - 2).toFixed(1)},${h - 1}`;
  const last = values.length - 1;
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} className="shrink-0">
      <polygon points={area} className="fill-ink" opacity="0.05" />
      <polyline points={line} fill="none" className="stroke-ink" strokeWidth="1.4" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={X(last)} cy={Y(values[last])} r="2.6" className="fill-ink stroke-paper" strokeWidth="1.2" />
    </svg>
  );
}

function TrendRow({ label, sub, values }: { label: string; sub: string; values: number[] }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-line py-3.5">
      <div className="min-w-0">
        <div className="text-[14px] text-ink">{label}</div>
        <div className="mt-0.5 text-[12px] text-ink-soft">media · {sub}</div>
      </div>
      {values.length >= 2 ? <Spark values={values} /> : <span className="text-[12px] text-ink-soft">—</span>}
    </div>
  );
}

function Tendencia({
  days,
  avg,
}: {
  days: WellnessDay[];
  avg: { avgSleepMinutes: number | null; avgHrvMs: number | null; avgRestingHr: number | null };
}) {
  const chrono = [...days].reverse();
  const series = (get: (d: WellnessDay) => number | null) => chrono.map(get).filter((v): v is number => v != null);
  return (
    <section>
      <SecLabel>Tendencia · 7 días</SecLabel>
      <TrendRow
        label="Sueño"
        sub={avg.avgSleepMinutes != null ? sleepLabel(avg.avgSleepMinutes) : "—"}
        values={series((d) => (d.sleepMinutes != null ? Math.round((d.sleepMinutes / 60) * 10) / 10 : null))}
      />
      <TrendRow label="HRV" sub={avg.avgHrvMs != null ? `${avg.avgHrvMs} ms` : "—"} values={series((d) => d.hrvAvgMs)} />
      <TrendRow
        label="FC en reposo"
        sub={avg.avgRestingHr != null ? `${avg.avgRestingHr} ppm` : "—"}
        values={series((d) => d.restingHr)}
      />
    </section>
  );
}

function Previos({ days }: { days: WellnessDay[] }) {
  return (
    <section>
      <SecLabel>Días anteriores</SecLabel>
      <div className="grid grid-cols-[1fr_auto_auto_auto] gap-x-5 border-b border-line pb-2 text-[11px] uppercase tracking-[0.06em] text-ink-soft">
        <span>Fecha</span>
        <span className="text-right">Sueño</span>
        <span className="text-right">HRV</span>
        <span className="text-right">FC rep.</span>
      </div>
      {days.map((p) => (
        <div key={p.date} className="grid grid-cols-[1fr_auto_auto_auto] items-center gap-x-5 border-b border-line py-3">
          <span className="text-[14px] text-ink">
            <span className="capitalize">{weekday(p.date)}.</span> {dayDate(p.date)}
          </span>
          <span className="text-right text-[14px] tabular-nums text-ink">{sleepLabel(p.sleepMinutes)}</span>
          <span className="text-right text-[14px] tabular-nums text-ink">{p.hrvAvgMs != null ? `${p.hrvAvgMs} ms` : "—"}</span>
          <span className="text-right text-[14px] tabular-nums text-ink">{num(p.restingHr)}</span>
        </div>
      ))}
    </section>
  );
}
