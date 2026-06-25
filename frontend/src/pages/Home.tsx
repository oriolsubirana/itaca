import { useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { Sparkline } from "../components/Sparkline";
import { getEntry, getFlares, SEVERITY_LABELS } from "../api/health";
import { getMeasurementSeries } from "../api/labs";
import { getTrainingSummary } from "../api/training";
import { getFinanceOverview } from "../api/finance";
import { getMeals } from "../api/nutrition";
import { dailyKcalTarget, getProfile, targetsFromProfile } from "../api/profile";
import { getWellness, recoveryState, sleepLabel } from "../api/wellness";
import { getTasks } from "../api/tasks";
import { balance, daysSince, routineLabel, shortDate, today } from "../lib/format";

function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? "Buenos días" : h < 21 ? "Buenas tardes" : "Buenas noches";
}

function fullDate(): string {
  return new Date().toLocaleDateString("es-ES", { weekday: "long", day: "numeric", month: "short" });
}

const SUGGESTIONS: { label: string; seed: string; workout?: boolean }[] = [
  { label: "¿Cómo estoy hoy?", seed: "¿Cómo estoy hoy según mi descanso y qué me recomiendas?" },
  { label: "Registrar el día", seed: "Quiero registrar el día de hoy" },
  { label: "Empezar entrenamiento", seed: "Empiezo entreno", workout: true },
  { label: "¿Qué como hoy?", seed: "¿Qué como hoy?" },
  { label: "Resumen de la semana", seed: "Hazme un resumen de la semana" },
];

export function Home() {
  const navigate = useNavigate();
  const openChat = (seed?: string, workout?: boolean) =>
    void navigate({ to: "/chat", search: { seed, workout: workout || undefined } });

  return (
    <div className="-mt-1 space-y-8">
      <header className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">{greeting()}, Oriol</h1>
          <p className="mt-1 text-sm capitalize text-ink-soft">{fullDate()}</p>
        </div>
        <div className="mt-0.5 flex shrink-0 items-center gap-1.5">
          <button
            onClick={() => void navigate({ to: "/tareas" })}
            aria-label="Tareas"
            className="flex size-10 items-center justify-center rounded-full border border-line text-ink-soft transition-colors hover:text-ink"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} className="size-5">
              <path d="M4 6.5l2 2 3-3.5M4 17.5l2 2 3-3.5M12 7h8M12 18h8" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
          <button
            onClick={() => void navigate({ to: "/entradas" })}
            aria-label="Entradas"
            className="flex size-10 items-center justify-center rounded-full border border-line text-ink-soft transition-colors hover:text-ink"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} className="size-5">
              <path
                d="M4 13h4l2 3h4l2-3h4M4 13l2.5-7h11L20 13v5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18z"
                strokeLinejoin="round"
              />
            </svg>
          </button>
          <button
            onClick={() => void navigate({ to: "/perfil" })}
            aria-label="Perfil"
            className="flex size-10 items-center justify-center rounded-full border border-line text-ink-soft transition-colors hover:text-ink"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} className="size-5">
              <circle cx="12" cy="8.5" r="3.5" />
              <path d="M5 20c0-3.5 3.1-5.5 7-5.5s7 2 7 5.5" strokeLinecap="round" />
            </svg>
          </button>
        </div>
      </header>

      <ChatHero onOpen={openChat} />

      <Briefing onOpen={openChat} />

      <TareasBlock />

      <div className="space-y-8">
        <SaludBlock onOpen={openChat} />
        <div className="border-t border-line" />
        <DescansoBlock />
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

function SecHead({ title, onClick }: { title: string; onClick?: () => void }) {
  if (!onClick) return <h2 className="mb-3 text-xs uppercase tracking-[0.13em] text-ink-soft">{title}</h2>;
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

// ── Hoy: the proactive briefing (recovery → training → nutrition) ────────────

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-[15px] tabular-nums text-ink">{value}</div>
      <div className="mt-0.5 text-[11px] uppercase tracking-[0.07em] text-ink-soft">{label}</div>
    </div>
  );
}

function Briefing({ onOpen }: { onOpen: (seed?: string, workout?: boolean) => void }) {
  const navigate = useNavigate();
  const wellness = useQuery({ queryKey: ["wellness"], queryFn: () => getWellness(14) });
  const training = useQuery({ queryKey: ["training-summary"], queryFn: getTrainingSummary });
  const profile = useQuery({ queryKey: ["profile"], queryFn: getProfile });
  const flares = useQuery({ queryKey: ["flares"], queryFn: getFlares });
  const meals = useQuery({ queryKey: ["meals"], queryFn: () => getMeals(14) });

  const rec = recoveryState(wellness.data);
  const d = wellness.data?.days?.[0];
  const s = training.data;

  const targets = targetsFromProfile(profile.data);
  const target = dailyKcalTarget(targets, s?.todayActivityKcal ?? 0, flares.data?.active != null);
  const consumed = (meals.data?.meals ?? [])
    .filter((m) => m.date === today())
    .reduce((sum, m) => sum + (m.calories ?? 0), 0);
  const pct = target ? Math.min(100, (consumed / target) * 100) : 0;

  return (
    <section>
      <SecHead title="Hoy" />
      <div className="overflow-hidden rounded-2xl border border-line">
        {rec && d && (
          <div className="px-4 pb-4 pt-4">
            <div className="flex items-center gap-2.5">
              <span className={`size-[9px] shrink-0 rounded-full ${rec.dotClass}`} />
              <span className="text-[19px] font-medium tracking-tight text-ink">{rec.title}</span>
            </div>
            <div className="ml-[19px] mt-1 text-[14px] text-ink-soft">{rec.sub}</div>
            <div className="ml-[19px] mt-4 flex gap-7">
              <Metric label="Sueño" value={sleepLabel(d.sleepMinutes)} />
              <Metric label="HRV" value={d.hrvAvgMs != null ? `${d.hrvAvgMs} ms` : "—"} />
              {(d.bodyBatteryHigh != null || d.bodyBatteryLow != null) && (
                <Metric label="Body Battery" value={`${d.bodyBatteryHigh ?? "—"}→${d.bodyBatteryLow ?? "—"}`} />
              )}
            </div>
          </div>
        )}

        <button
          onClick={() => onOpen("Empiezo entreno", true)}
          className={`flex w-full items-center justify-between gap-3 px-4 py-4 text-left transition-colors hover:bg-line/20 ${rec ? "border-t border-line" : ""}`}
        >
          <div className="min-w-0">
            <div className="text-[11px] uppercase tracking-[0.08em] text-ink-soft">Entreno de hoy</div>
            <div className="mt-1 text-[19px] font-medium text-ink">{s?.nextRoutine ? routineLabel(s.nextRoutine) : "—"}</div>
            <div className="mt-1 text-[13px] leading-relaxed text-ink-soft">
              {rec?.trainingNote}
              {rec && s?.lastWorkoutDate && s.lastWorkoutRoutine ? " · " : ""}
              {s?.lastWorkoutDate && s.lastWorkoutRoutine
                ? `última ${routineLabel(s.lastWorkoutRoutine)} hace ${daysSince(s.lastWorkoutDate)} días`
                : ""}
            </div>
          </div>
          <span className="flex h-11 shrink-0 items-center rounded-full bg-ink px-5 text-sm font-medium text-paper">
            Entrenar
          </span>
        </button>

        <div className="border-t border-line px-4 py-4">
          <div className="flex items-end justify-between gap-3">
            <div>
              <div className="text-[11px] uppercase tracking-[0.08em] text-ink-soft">Objetivo de hoy</div>
              {target != null ? (
                <div className="mt-1.5 text-2xl font-semibold leading-none tabular-nums text-ink">
                  {target.toLocaleString("de-DE")}
                  <span className="ml-1.5 text-[13px] font-normal text-ink-soft">kcal</span>
                </div>
              ) : (
                <button
                  onClick={() => void navigate({ to: "/perfil" })}
                  className="mt-1.5 text-[14px] text-ink-soft hover:text-ink"
                >
                  Completa tu perfil →
                </button>
              )}
            </div>
            <button
              onClick={() => onOpen("Quiero registrar una comida")}
              className="h-10 shrink-0 rounded-full border border-line px-4 text-[13px] font-medium text-ink hover:bg-line/40"
            >
              Registrar comida
            </button>
          </div>
          {target != null && (
            <>
              <div className="mt-4 h-[5px] overflow-hidden rounded-full bg-line">
                <div className="h-full rounded-full bg-ink" style={{ width: `${pct}%` }} />
              </div>
              <div className="mt-2 text-[13px] tabular-nums text-ink-soft">
                Consumido {consumed.toLocaleString("de-DE")} · restante{" "}
                {Math.max(0, target - consumed).toLocaleString("de-DE")} kcal
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  );
}

function TareasBlock() {
  const navigate = useNavigate();
  const tasks = useQuery({ queryKey: ["tasks"], queryFn: () => getTasks(false) });
  const open = tasks.data?.open ?? [];
  if (open.length === 0) return null;

  const preview = open.slice(0, 3);
  const rest = open.length - preview.length;
  const overdue = tasks.data?.overdueCount ?? 0;

  return (
    <section>
      <SecHead title="Tareas" onClick={() => void navigate({ to: "/tareas" })} />
      <ul className="space-y-2.5">
        {preview.map((t) => (
          <li key={t.id} className="flex items-center gap-2.5">
            <span className={`size-[7px] shrink-0 rounded-full ${t.overdue ? "bg-clinical" : "bg-ink/30"}`} />
            <span className="min-w-0 flex-1 truncate text-[15px] text-ink">{t.title}</span>
            {t.dueDate && (
              <span className={`shrink-0 text-[12px] ${t.overdue ? "text-clinical" : "text-ink-soft"}`}>
                {t.overdue ? `vencía ${shortDate(t.dueDate)}` : shortDate(t.dueDate)}
              </span>
            )}
          </li>
        ))}
      </ul>
      {(rest > 0 || overdue > 0) && (
        <p className="mt-2.5 text-[12px] text-ink-soft">
          {rest > 0 && `+${rest} más`}
          {rest > 0 && overdue > 0 && " · "}
          {overdue > 0 && <span className="text-clinical">{overdue} vencida{overdue === 1 ? "" : "s"}</span>}
        </p>
      )}
    </section>
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
          <Sparkline values={points.map((p) => p.value)} refMin={last.refMin} refMax={last.refMax} />
        </div>
      )}
    </section>
  );
}

function DescansoBlock() {
  const navigate = useNavigate();
  const wellness = useQuery({ queryKey: ["wellness"], queryFn: () => getWellness(14) });
  const last = wellness.data?.days?.[0];
  if (!last || (last.sleepMinutes == null && last.hrvAvgMs == null && last.restingHr == null)) return null;
  return (
    <>
      <section>
        <SecHead title="Descanso" onClick={() => void navigate({ to: "/descanso" })} />
        <div>
          <div className="text-[11px] uppercase tracking-wide text-ink-soft">Anoche</div>
          <div className="mt-1.5 flex items-baseline gap-2">
            <span className="text-2xl font-medium leading-none tabular-nums text-ink">{sleepLabel(last.sleepMinutes)}</span>
            {last.sleepScore != null && <span className="text-[13px] text-ink-soft">score {last.sleepScore}</span>}
          </div>
          <div className="mt-2 text-[13px] text-ink-soft">
            {last.hrvAvgMs != null && `HRV ${last.hrvAvgMs} ms${last.hrvStatus ? ` · ${last.hrvStatus}` : ""}`}
            {last.hrvAvgMs != null && last.restingHr != null && " · "}
            {last.restingHr != null && `FC reposo ${last.restingHr}`}
          </div>
        </div>
      </section>
      <div className="border-t border-line" />
    </>
  );
}

function FinanzasBlock() {
  const navigate = useNavigate();
  const overview = useQuery({ queryKey: ["finance-overview"], queryFn: getFinanceOverview });
  const accounts = overview.data?.accounts ?? [];
  const totalIn = (currency: string) =>
    accounts.filter((a) => a.currency === currency).reduce((sum, a) => sum + a.balance, 0);
  const hasChf = accounts.some((a) => a.currency === "CHF");
  const hasEur = accounts.some((a) => a.currency === "EUR");

  return (
    <section>
      <SecHead title="Finanzas" onClick={() => void navigate({ to: "/finanzas" })} />
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-[11px] uppercase tracking-wide text-ink-soft">Patrimonio</div>
          <div className="mt-1.5 text-2xl font-semibold leading-none tabular-nums text-ink">
            {hasChf ? balance(totalIn("CHF")) : "—"}
            <span className="ml-1.5 text-sm font-normal text-ink-soft">CHF</span>
          </div>
        </div>
        {hasEur && (
          <div className="text-right">
            <div className="text-base tabular-nums text-ink">
              {balance(totalIn("EUR"))} <span className="text-xs text-ink-soft">EUR</span>
            </div>
            <div className="mt-0.5 text-[11px] text-ink-soft">patrimonio</div>
          </div>
        )}
      </div>
    </section>
  );
}
