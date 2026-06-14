import { useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { Sparkline } from "../components/Sparkline";
import { getEntry, getFlares, SEVERITY_LABELS } from "../api/health";
import { getMeasurementSeries } from "../api/labs";
import { getTrainingSummary } from "../api/training";
import { getFinanceMonth, getFinanceOverview } from "../api/finance";
import { daysSince, monthName, routineLabel, signed, today } from "../lib/format";

function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? "Buenos días" : h < 21 ? "Buenas tardes" : "Buenas noches";
}

function fullDate(): string {
  return new Date().toLocaleDateString("es-ES", { weekday: "long", day: "numeric", month: "short" });
}

const SUGGESTIONS: { label: string; seed: string; workout?: boolean }[] = [
  { label: "Registrar el día", seed: "Quiero registrar el día de hoy" },
  { label: "Empezar entrenamiento", seed: "Empiezo entreno", workout: true },
  { label: "¿Cómo va mi calprotectina?", seed: "¿Cómo va mi calprotectina fecal?" },
  { label: "Resumen de la semana", seed: "Hazme un resumen de la semana" },
];

export function Home() {
  const navigate = useNavigate();
  const openChat = (seed?: string, workout?: boolean) =>
    void navigate({ to: "/chat", search: { seed, workout: workout || undefined } });

  return (
    <div className="-mt-1 space-y-8">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">{greeting()}, Oriol</h1>
        <p className="mt-1 text-sm capitalize text-ink-soft">{fullDate()}</p>
      </header>

      <ChatHero onOpen={openChat} />

      <div className="space-y-8">
        <SaludBlock onOpen={openChat} />
        <div className="border-t border-line" />
        <EntrenoBlock onOpen={openChat} />
        <div className="border-t border-line" />
        <FinanzasBlock />
      </div>

      <p className="text-[11.5px] leading-relaxed text-ink-soft">
        Ítaca registra y muestra tus datos clínicos. No los interpreta ni ofrece consejo médico.
      </p>
    </div>
  );
}

function ChatHero({ onOpen }: { onOpen: (seed?: string, workout?: boolean) => void }) {
  return (
    <div>
      <button
        onClick={() => onOpen()}
        className="flex min-h-[60px] w-full items-center gap-3 rounded-2xl border border-ink/15 bg-paper pl-5 pr-2.5 text-left transition-colors hover:border-ink/35"
      >
        <span className="flex-1 text-base text-ink-soft">Habla con Ítaca…</span>
        <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-ink text-paper">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} className="size-5">
            <path d="M12 19V5M5 12l7-7 7 7" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
      </button>
      <div className="no-scrollbar -mx-5 mt-3 flex gap-2 overflow-x-auto px-5 pb-1">
        {SUGGESTIONS.map((s) => (
          <button
            key={s.label}
            onClick={() => onOpen(s.seed, s.workout)}
            className="min-h-10 shrink-0 whitespace-nowrap rounded-full border border-line px-3.5 text-[13.5px] text-ink transition-colors hover:bg-line/40"
          >
            {s.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function SecHead({ title, onClick }: { title: string; onClick: () => void }) {
  return (
    <button onClick={onClick} className="group mb-3 flex w-full items-center justify-between">
      <h2 className="text-xs uppercase tracking-[0.13em] text-ink-soft">{title}</h2>
      <span className="flex items-center gap-0.5 text-xs text-ink-soft transition-colors group-hover:text-ink">
        Ver
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} className="size-3.5">
          <path d="M9 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
    </button>
  );
}

function todaySummary(e: {
  bristol: number | null;
  bowelMovements: number | null;
  pain: number | null;
  urgency: number | null;
}): string {
  const parts: string[] = [];
  if (e.bristol != null) parts.push(`Bristol ${e.bristol}`);
  if (e.bowelMovements != null) parts.push(`${e.bowelMovements} dep`);
  if (e.pain != null && e.pain > 0) parts.push(`dolor ${e.pain}`);
  if (e.urgency != null && e.urgency > 0) parts.push(`urgencia ${e.urgency}`);
  return parts.join(" · ");
}

function SaludBlock({ onOpen }: { onOpen: (seed?: string, workout?: boolean) => void }) {
  const navigate = useNavigate();
  const entry = useQuery({ queryKey: ["diary", today()], queryFn: () => getEntry(today()) });
  const flares = useQuery({ queryKey: ["flares"], queryFn: getFlares });
  const calpro = useQuery({
    queryKey: ["analyte-series", "code:fecal_calprotectin"],
    queryFn: () => getMeasurementSeries("code:fecal_calprotectin"),
  });

  const active = flares.data?.active;
  const e = entry.data;
  const summary = e ? todaySummary(e) : "";
  const points = calpro.data?.points ?? [];
  const last = points[points.length - 1];
  const prev = points[points.length - 2];
  const outOfRange =
    last != null &&
    ((last.refMax != null && last.value > last.refMax) || (last.refMin != null && last.value < last.refMin));
  const trend = last && prev ? (last.value > prev.value ? "al alza" : last.value < prev.value ? "a la baja" : "estable") : null;

  return (
    <section>
      <SecHead title="Salud" onClick={() => void navigate({ to: "/salud" })} />

      {active && (
        <div className="mb-3 flex items-center gap-2">
          <span className="size-[7px] shrink-0 rounded-full bg-clinical" />
          <span className="text-sm text-clinical">
            Brote {SEVERITY_LABELS[active.severity]} · {daysSince(active.startDate)} días
          </span>
        </div>
      )}

      {summary ? (
        <p className="text-[15px] leading-relaxed text-ink">
          <span className="text-ink-soft">Hoy · </span>
          {summary}
        </p>
      ) : (
        <div className="flex items-center justify-between gap-3">
          <span className="text-[15px] text-ink-soft">Sin registrar hoy</span>
          <button
            onClick={() => onOpen("Quiero registrar el día de hoy")}
            className="h-10 shrink-0 rounded-full bg-ink px-4 text-sm font-medium text-paper"
          >
            Registrar
          </button>
        </div>
      )}

      {last && (
        <div className="mt-4 flex items-end justify-between gap-3 border-t border-line pt-4">
          <div>
            <div className="text-[11px] uppercase tracking-wide text-ink-soft">{calpro.data?.name}</div>
            <div
              className={`mt-1.5 text-3xl font-semibold leading-none tabular-nums ${outOfRange ? "text-clinical" : "text-ink"}`}
            >
              {last.value}
              <span className="ml-1.5 text-sm font-normal text-ink-soft">{calpro.data?.unit}</span>
            </div>
            <div className={`mt-1.5 text-xs ${outOfRange ? "text-clinical" : "text-ink-soft"}`}>
              {outOfRange ? "Fuera de rango" : "En rango"}
              {last.refMax != null && ` · ref ${last.refMin ?? 0}–${last.refMax}`}
              {trend && ` · ${trend}`}
            </div>
          </div>
          <Sparkline
            values={points.map((p) => p.value)}
            refMin={last.refMin}
            refMax={last.refMax}
          />
        </div>
      )}
    </section>
  );
}

function EntrenoBlock({ onOpen }: { onOpen: (seed?: string, workout?: boolean) => void }) {
  const navigate = useNavigate();
  const summary = useQuery({ queryKey: ["training-summary"], queryFn: getTrainingSummary });
  const s = summary.data;

  return (
    <section>
      <SecHead title="Entrenamiento" onClick={() => void navigate({ to: "/gym" })} />
      <div className="flex items-end justify-between gap-3">
        <div>
          <div className="text-[11px] uppercase tracking-wide text-ink-soft">Toca hoy</div>
          <div className="mt-1.5 text-2xl font-medium leading-none text-ink">
            {s?.nextRoutine ? routineLabel(s.nextRoutine) : "—"}
          </div>
          {s?.lastWorkoutDate && s.lastWorkoutRoutine && (
            <div className="mt-2 text-[13px] text-ink-soft">
              Última sesión · {routineLabel(s.lastWorkoutRoutine)} · hace {daysSince(s.lastWorkoutDate)} días
            </div>
          )}
        </div>
        <button
          onClick={() => onOpen("Empiezo entreno", true)}
          className="h-11 shrink-0 rounded-full bg-ink px-5 text-sm font-medium text-paper hover:bg-ink/90"
        >
          Entrenar
        </button>
      </div>
    </section>
  );
}

function FinanzasBlock() {
  const navigate = useNavigate();
  const overview = useQuery({ queryKey: ["finance-overview"], queryFn: getFinanceOverview });
  const months = overview.data?.monthOrder ?? [];
  const month = months[months.length - 1];
  const chf = useQuery({
    queryKey: ["finance-month", month, "CHF"],
    queryFn: () => getFinanceMonth(month!, "CHF"),
    enabled: !!month,
  });
  const eur = useQuery({
    queryKey: ["finance-month", month, "EUR"],
    queryFn: () => getFinanceMonth(month!, "EUR"),
    enabled: !!month,
  });

  const label = month ? monthName(month) : "";
  const netChf = chf.data ? chf.data.ingresos + chf.data.gastos : null;
  const netEur = eur.data ? eur.data.ingresos + eur.data.gastos : null;

  return (
    <section>
      <SecHead title="Finanzas" onClick={() => void navigate({ to: "/finanzas" })} />
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-[11px] uppercase tracking-wide text-ink-soft">Neto · {label}</div>
          <div
            className={`mt-1.5 text-2xl font-semibold leading-none tabular-nums ${netChf != null && netChf >= 0 ? "text-income" : "text-ink"}`}
          >
            {netChf != null ? signed(netChf) : "—"}
            <span className="ml-1.5 text-sm font-normal text-ink-soft">CHF</span>
          </div>
        </div>
        {netEur != null && (
          <div className="text-right">
            <div className={`text-base tabular-nums ${netEur >= 0 ? "text-income" : "text-ink"}`}>
              {signed(netEur)} <span className="text-xs text-ink-soft">EUR</span>
            </div>
            <div className="mt-0.5 text-[11px] text-ink-soft">neto · {label.toLowerCase()}</div>
          </div>
        )}
      </div>
    </section>
  );
}
