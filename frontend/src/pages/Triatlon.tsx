import { useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { Disclosure, DisclosureButton, DisclosurePanel } from "@headlessui/react";
import { Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import {
  DAY_LOAD_LABELS,
  STATUS_LABELS,
  getTrainingLoad,
  getTrainingWeek,
  getTriathlonPlan,
  type PhaseView,
  type PlannedSession,
  type PlanProgress,
  type SessionStatus,
  type TodayPlan,
  type TriathlonPlanView,
} from "../api/plan";
import { getSportsFueling } from "../api/nutrition";
import { getWellness, recoveryState } from "../api/wellness";
import { localToday, shortDate } from "../lib/format";

const num = (n: number, dec = 1) =>
  n.toLocaleString("de-DE", { minimumFractionDigits: 0, maximumFractionDigits: dec });

function SecLabel({ children }: { children: string }) {
  return <h2 className="mb-4 text-xs uppercase tracking-[0.13em] text-ink-soft">{children}</h2>;
}

export function Triatlon() {
  const navigate = useNavigate();
  const plan = useQuery({ queryKey: ["triathlon-plan"], queryFn: getTriathlonPlan });
  const data = plan.data;

  return (
    <div>
      <header className="-mt-1 flex items-center justify-between gap-2 border-b border-line pb-3">
        <div className="flex items-center gap-2">
          <button
            onClick={() => void navigate({ to: "/gym" })}
            aria-label="Volver"
            className="-ml-2 flex size-9 items-center justify-center rounded-full text-ink hover:bg-line/50"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="size-5">
              <path d="M15 6l-6 6 6 6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">Triatlón</h1>
        </div>
        <button
          onClick={() =>
            void navigate({
              to: "/chat",
              search: { seed: "Según el plan del triatlón, ¿en qué punto estoy y qué sesión me toca hoy?" },
            })
          }
          className="rounded-full border border-line px-3.5 py-1.5 text-[13px] text-ink-soft hover:text-ink"
        >
          Preguntar a Ítaca
        </button>
      </header>

      {!data ? (
        <p className="pt-12 text-center text-sm text-ink-soft">Cargando el plan…</p>
      ) : (
        <div className="space-y-9 pt-5">
          <Hero data={data} />
          <div className="border-t border-line" />
          {data.daysToRace >= 0 && <Hoy today={data.today} />}
          {data.daysToRace >= 0 && <div className="border-t border-line" />}
          <Semana />
          <div className="border-t border-line" />
          <Carga />
          <div className="border-t border-line" />
          {data.phase && <Fase phase={data.phase} />}
          {data.phase && <div className="border-t border-line" />}
          <Progreso p={data.progress} />
          <div className="border-t border-line" />
          <Combustible />
          <div className="border-t border-line" />
          <Ritmos data={data} />
          <div className="border-t border-line" />
          <Principios data={data} />
          <p className="text-[11.5px] leading-relaxed text-ink-soft">
            Plan orientativo. Ajusta cargas según sensaciones, sueño y vida real; ante dolor persistente, para y
            consulta.
          </p>
        </div>
      )}
    </div>
  );
}

function Hero({ data }: { data: TriathlonPlanView }) {
  const date = new Date(data.raceDate + "T00:00:00");
  const fecha = date.toLocaleDateString("es-ES", { day: "numeric", month: "long", year: "numeric" });
  return (
    <section>
      <SecLabel>Camino al sub-2h30</SecLabel>
      <div className="flex items-end justify-between gap-4">
        <div>
          <div className="text-[17px] font-medium text-ink">{data.raceName}</div>
          <div className="mt-1 text-[13px] text-ink-soft">
            {fecha} · {data.goal}
          </div>
        </div>
        {data.daysToRace >= 0 && (
          <div className="text-right">
            <div className="whitespace-nowrap text-[40px] font-semibold leading-none tracking-[-0.02em] tabular-nums text-ink">
              {data.daysToRace}
            </div>
            <div className="mt-1 text-[11px] uppercase tracking-[0.08em] text-ink-soft">días</div>
          </div>
        )}
      </div>
    </section>
  );
}

function Fase({ phase }: { phase: PhaseView }) {
  const pct = Math.min(100, Math.round((phase.week / phase.totalWeeks) * 100));
  return (
    <section>
      <SecLabel>Fase actual</SecLabel>
      <div className="flex items-baseline justify-between">
        <span className="text-[17px] font-medium text-ink">{phase.name}</span>
        <span className="text-[13px] tabular-nums text-ink-soft">
          semana {phase.week} de {phase.totalWeeks}
        </span>
      </div>
      <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-line">
        <div className="h-full rounded-full bg-ink" style={{ width: `${pct}%` }} />
      </div>
      <p className="mt-4 text-[13.5px] leading-relaxed text-ink-soft">{phase.objective}</p>
      <ul className="mt-3 space-y-2">
        {phase.guidance.map((g) => (
          <li key={g} className="flex gap-2.5 text-[13.5px] leading-relaxed text-ink">
            <span className="mt-[9px] size-1 shrink-0 rounded-full bg-ink-soft" />
            {g}
          </li>
        ))}
      </ul>
      <p className="mt-4 rounded-lg bg-line/40 px-3.5 py-2.5 text-[13px] leading-relaxed text-ink">
        <span className="font-medium">Hito · </span>
        {phase.milestone}
      </p>
    </section>
  );
}

function Progreso({ p }: { p: PlanProgress }) {
  const sports: { label: string; s: typeof p.swim; vol: string }[] = [
    { label: "Natación", s: p.swim, vol: `${num(p.swim.km)} km` },
    { label: "Carrera", s: p.run, vol: `${num(p.run.km)} km` },
    { label: "Bici", s: p.bike, vol: `${num(p.bike.km, 0)} km` },
  ];
  return (
    <section>
      <SecLabel>Últimas 4 semanas</SecLabel>
      <div className="grid grid-cols-3 gap-3">
        {sports.map(({ label, s, vol }) => (
          <div key={label} className="rounded-xl border border-line p-3.5">
            <div className="text-[11px] uppercase tracking-[0.08em] text-ink-soft">{label}</div>
            <div className="mt-2 text-[22px] font-semibold leading-none tabular-nums text-ink">{s.sessions}</div>
            <div className="mt-1 text-[11.5px] text-ink-soft">sesiones</div>
            <div className="mt-2.5 text-[12.5px] tabular-nums text-ink">{vol}</div>
            {s.pace && <div className="mt-0.5 text-[12px] tabular-nums text-ink-soft">{s.pace}</div>}
          </div>
        ))}
      </div>
      {p.longestRunKm != null && (
        <p className="mt-3 text-[13px] text-ink-soft">
          Carrera más larga: <span className="tabular-nums text-ink">{num(p.longestRunKm)} km</span>
          {p.longestRunPace && <span className="tabular-nums"> a {p.longestRunPace}</span>}
        </p>
      )}
    </section>
  );
}

function Ritmos({ data }: { data: TriathlonPlanView }) {
  return (
    <section>
      <SecLabel>El día de la prueba</SecLabel>
      <div className="space-y-2.5">
        {data.raceTargets.map((t) => (
          <div key={t.sector} className="flex items-baseline justify-between gap-3 text-[13.5px]">
            <span className={t.sector === "TOTAL" ? "font-medium text-ink" : "text-ink"}>{t.sector}</span>
            <span className="text-right tabular-nums text-ink-soft">
              {t.target && <span>{t.target} · </span>}
              <span className={t.sector === "TOTAL" ? "font-medium text-ink" : "text-ink"}>{t.time}</span>
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}

function Principios({ data }: { data: TriathlonPlanView }) {
  return (
    <section>
      <SecLabel>Principios</SecLabel>
      <ol className="space-y-2.5">
        {data.principles.map((p, i) => (
          <li key={p} className="flex gap-3 text-[13.5px] leading-relaxed text-ink">
            <span className="tabular-nums text-ink-soft">{i + 1}</span>
            {p}
          </li>
        ))}
      </ol>
    </section>
  );
}

const STATUS_DOT: Record<SessionStatus, string> = {
  done: "bg-income",
  partial: "bg-warning",
  missed: "bg-clinical",
  pending: "border border-ink bg-transparent",
  upcoming: "bg-line",
};

function SessionRow({ s, showDetail }: { s: PlannedSession; showDetail: boolean }) {
  return (
    <div className="flex gap-3 py-1.5">
      <span className={`mt-[7px] size-2 shrink-0 rounded-full ${STATUS_DOT[s.status]}`} />
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-3">
          <span className="text-[14px] text-ink">{s.title}</span>
          <span className="shrink-0 text-[12.5px] tabular-nums text-ink-soft">
            {STATUS_LABELS[s.status] && <span>{STATUS_LABELS[s.status]} · </span>}
            {s.minutes}′
          </span>
        </div>
        {showDetail && <p className="mt-0.5 text-[13px] leading-relaxed text-ink-soft">{s.detail}</p>}
      </div>
    </div>
  );
}

function Hoy({ today }: { today: TodayPlan }) {
  const navigate = useNavigate();
  const hasGym = today.sessions.some((s) => s.sport === "gym");
  return (
    <section>
      <SecLabel>Hoy</SecLabel>
      <div className="mb-2 text-[17px] font-medium text-ink">{DAY_LOAD_LABELS[today.dayLoad]}</div>
      {today.sessions.length === 0 ? (
        <p className="text-[13.5px] leading-relaxed text-ink-soft">
          Día de descanso: movilidad suave, caminar y dormir bien. Aquí también se entrena.
        </p>
      ) : (
        <div>
          {today.sessions.map((s) => (
            <SessionRow key={s.title} s={s} showDetail />
          ))}
        </div>
      )}
      {today.sessions.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2">
          {hasGym && (
            <button
              onClick={() => void navigate({ to: "/chat", search: { seed: "Empiezo entreno", workout: true } })}
              className="h-11 rounded-full bg-ink px-5 text-[14px] font-medium text-paper hover:bg-ink/90"
            >
              Empezar gym
            </button>
          )}
          <button
            onClick={() =>
              void navigate({
                to: "/chat",
                search: { seed: "Mira mi recuperación y mi carga: ¿hago la sesión de hoy tal cual o la ajusto?" },
              })
            }
            className="h-11 rounded-full border border-line px-5 text-[14px] text-ink hover:bg-line/40"
          >
            ¿La ajusto?
          </button>
        </div>
      )}
    </section>
  );
}

function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T12:00:00`);
  d.setDate(d.getDate() + days);
  return d.toLocaleDateString("en-CA");
}

const KIND_LABELS: Record<string, string> = {
  load: "Carga",
  recovery: "Descarga",
  sharpen: "Afinado",
  taper: "Taper",
  race: "Semana de carrera",
};

function Semana() {
  const [offset, setOffset] = useState(0);
  const date = addDays(localToday(), offset * 7);
  const week = useQuery({ queryKey: ["training-week", date], queryFn: () => getTrainingWeek(date), retry: false });
  const w = week.data;
  const todayIso = localToday();
  return (
    <section>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xs uppercase tracking-[0.13em] text-ink-soft">
          {offset === 0 ? "Esta semana" : offset === 1 ? "La semana que viene" : offset === -1 ? "La semana pasada" : "Semana"}
        </h2>
        <div className="flex gap-1">
          <NavButton label="Semana anterior" onClick={() => setOffset((o) => o - 1)} d="M15 6l-6 6 6 6" />
          {offset !== 0 && (
            <button onClick={() => setOffset(0)} className="h-9 rounded-full px-3 text-[12.5px] text-ink-soft hover:text-ink">
              Hoy
            </button>
          )}
          <NavButton label="Semana siguiente" onClick={() => setOffset((o) => o + 1)} d="M9 6l6 6-6 6" />
        </div>
      </div>
      {week.isError ? (
        <p className="text-sm text-ink-soft">Esta semana queda fuera del plan.</p>
      ) : !w ? (
        <p className="text-sm text-ink-soft">Cargando…</p>
      ) : (
        <>
          <div className="flex items-baseline justify-between gap-3">
            <span className="text-[15px] font-medium text-ink">
              {shortDate(w.monday)} – {shortDate(w.sunday)}
            </span>
            <span className="text-[12.5px] text-ink-soft">
              {w.phaseName} · {KIND_LABELS[w.kind]}
            </span>
          </div>
          <p className="mt-1.5 text-[13px] leading-relaxed text-ink-soft">{w.focus}</p>
          {w.adherence.percent != null && (
            <div className="mt-3">
              <div className="flex items-baseline justify-between text-[12.5px]">
                <span className="text-ink-soft">Cumplimiento hasta hoy</span>
                <span className="tabular-nums text-ink">{w.adherence.percent} %</span>
              </div>
              <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-line">
                <div className="h-full rounded-full bg-ink" style={{ width: `${w.adherence.percent}%` }} />
              </div>
            </div>
          )}
          <div className="mt-4 divide-y divide-line">
            {w.days.map((d) => (
              <div key={d.date} className={`flex gap-3 py-2.5 ${d.date === todayIso ? "-mx-2 rounded-lg bg-line/30 px-2" : ""}`}>
                <div className="w-[3.4rem] shrink-0 pt-1.5">
                  <div className="text-[12.5px] capitalize text-ink">{d.weekday.slice(0, 3)}</div>
                  <div className="text-[11px] tabular-nums text-ink-soft">{shortDate(d.date)}</div>
                </div>
                <div className="min-w-0 flex-1">
                  {d.sessions.length === 0 ? (
                    <div className="py-1.5 text-[13.5px] text-ink-soft">Descanso</div>
                  ) : (
                    d.sessions.map((s) => <SessionRow key={s.title} s={s} showDetail={offset >= 0 && d.date >= todayIso} />)
                  )}
                </div>
              </div>
            ))}
          </div>
          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-[11.5px] text-ink-soft">
            {(["done", "partial", "missed", "pending"] as SessionStatus[]).map((st) => (
              <span key={st} className="flex items-center gap-1.5">
                <span className={`size-2 rounded-full ${STATUS_DOT[st]}`} />
                {STATUS_LABELS[st]}
              </span>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

function NavButton({ label, onClick, d }: { label: string; onClick: () => void; d: string }) {
  return (
    <button
      onClick={onClick}
      aria-label={label}
      className="flex size-11 items-center justify-center rounded-full text-ink hover:bg-line/50"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="size-5">
        <path d={d} strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </button>
  );
}

function Carga() {
  const load = useQuery({ queryKey: ["training-load"], queryFn: () => getTrainingLoad(42) });
  const wellness = useQuery({ queryKey: ["wellness"], queryFn: () => getWellness(14) });
  const rec = recoveryState(wellness.data);
  const l = load.data;
  return (
    <section>
      <SecLabel>Carga y forma</SecLabel>
      {!l ? (
        <p className="text-sm text-ink-soft">Cargando…</p>
      ) : (
        <>
          <div className="flex items-baseline justify-between gap-3">
            <span
              className={`text-[17px] font-medium ${l.status === "overreaching" ? "text-clinical" : "text-ink"}`}
            >
              {l.statusLabel}
            </span>
            {rec && (
              <span className="flex items-center gap-1.5 text-[12.5px] text-ink-soft">
                <span className={`size-2 rounded-full ${rec.dotClass}`} />
                {rec.title}
              </span>
            )}
          </div>
          <p className="mt-1.5 text-[13.5px] leading-relaxed text-ink-soft">{l.advice}</p>
          {l.rampWarning && (
            <p className="mt-2 text-[13px] leading-relaxed text-warning">
              La forma sube {num(l.rampPerWeek)} puntos/semana: demasiado rápido. Frena la progresión esta semana.
            </p>
          )}
          <div className="mt-4 grid grid-cols-3 gap-3">
            <LoadMetric label="Forma física" value={l.today.fitness} />
            <LoadMetric label="Fatiga" value={l.today.fatigue} />
            <LoadMetric label="Frescura" value={l.today.form} signed />
          </div>
          <ResponsiveContainer
            width="100%"
            height={170}
            className="mt-4 [&_.recharts-wrapper]:outline-none [&_svg]:outline-none"
          >
            <LineChart data={l.series} margin={{ top: 8, right: 8, left: -4, bottom: 0 }}>
              <XAxis
                dataKey="date"
                tick={{ fontSize: 11, fill: "var(--color-ink-soft)" }}
                tickFormatter={(iso: string) => shortDate(iso)}
                stroke="var(--color-line)"
                minTickGap={24}
              />
              <YAxis tick={{ fontSize: 11, fill: "var(--color-ink-soft)" }} stroke="var(--color-line)" width={40} />
              <ReferenceLine y={0} stroke="var(--color-line)" />
              <Tooltip
                labelFormatter={(iso) => shortDate(String(iso))}
                contentStyle={{
                  background: "var(--color-paper)",
                  border: "1px solid var(--color-line)",
                  borderRadius: 8,
                  fontSize: 12,
                  color: "var(--color-ink)",
                }}
              />
              <Line type="monotone" dataKey="fitness" name="Forma física" stroke="var(--color-ink)" strokeWidth={1.5} dot={false} />
              <Line
                type="monotone"
                dataKey="fatigue"
                name="Fatiga"
                stroke="var(--color-ink-soft)"
                strokeWidth={1.25}
                strokeDasharray="4 3"
                dot={false}
              />
              <Line type="monotone" dataKey="form" name="Frescura" stroke="var(--color-income)" strokeWidth={1.25} dot={false} />
            </LineChart>
          </ResponsiveContainer>
          <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[11.5px] text-ink-soft">
            <span className="flex items-center gap-1.5">
              <span className="h-px w-4 bg-ink" /> Forma física
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-4 border-t border-dashed border-ink-soft" /> Fatiga
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-px w-4 bg-income" /> Frescura
            </span>
          </div>
          <p className="mt-2 text-[11.5px] leading-relaxed text-ink-soft">{l.note}</p>
        </>
      )}
    </section>
  );
}

function LoadMetric({ label, value, signed }: { label: string; value: number; signed?: boolean }) {
  return (
    <div className="rounded-xl border border-line p-3">
      <div className="text-[20px] font-semibold leading-none tabular-nums text-ink">
        {signed && value > 0 ? "+" : ""}
        {num(value, 0)}
      </div>
      <div className="mt-1.5 text-[11px] uppercase tracking-[0.06em] text-ink-soft">{label}</div>
    </div>
  );
}

function Combustible() {
  const fueling = useQuery({ queryKey: ["fueling", "all"], queryFn: () => getSportsFueling() });
  const f = fueling.data;
  if (!f) return null;
  return (
    <section>
      <SecLabel>Nutrición de entreno</SecLabel>
      <div className="space-y-4">
        {f.sessionFueling.map((b) => (
          <div key={b.label}>
            <div className="text-[14px] font-medium text-ink">{b.label}</div>
            <dl className="mt-1 space-y-0.5 text-[13px] leading-relaxed">
              <div>
                <dt className="inline text-ink-soft">Antes · </dt>
                <dd className="inline text-ink">{b.before}</dd>
              </div>
              <div>
                <dt className="inline text-ink-soft">Durante · </dt>
                <dd className="inline text-ink">{b.during}</dd>
              </div>
              <div>
                <dt className="inline text-ink-soft">Después · </dt>
                <dd className="inline text-ink">{b.after}</dd>
              </div>
            </dl>
          </div>
        ))}
      </div>
      <Disclosure as="div" className="mt-5 rounded-xl border border-line">
        <DisclosureButton className="group flex min-h-11 w-full items-center justify-between gap-2 px-3.5 text-left text-[14px] font-medium text-ink outline-none data-[focus]:bg-line/30">
          Protocolo de la semana de carrera
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            className="size-4 shrink-0 text-ink-soft transition-transform group-data-[open]:rotate-180"
          >
            <path d="M6 9l6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </DisclosureButton>
        <DisclosurePanel className="space-y-3.5 px-3.5 pb-4">
          {f.raceWeek.map((step) => (
            <div key={step.moment}>
              <div className="text-[12px] uppercase tracking-[0.07em] text-ink-soft">{step.moment}</div>
              <ul className="mt-1 space-y-1">
                {step.guidance.map((g) => (
                  <li key={g} className="flex gap-2.5 text-[13.5px] leading-relaxed text-ink">
                    <span className="mt-[9px] size-1 shrink-0 rounded-full bg-ink-soft" />
                    {g}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </DisclosurePanel>
      </Disclosure>
      <p className="mt-4 text-[11.5px] leading-relaxed text-ink-soft">{f.disclaimer}</p>
    </section>
  );
}
