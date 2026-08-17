import { useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { getTriathlonPlan, type PhaseView, type PlanProgress, type TriathlonPlanView } from "../api/plan";

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
          {data.phase && <Fase phase={data.phase} />}
          {data.phase && <div className="border-t border-line" />}
          <Progreso p={data.progress} />
          <div className="border-t border-line" />
          <SemanaTipo data={data} />
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

function SemanaTipo({ data }: { data: TriathlonPlanView }) {
  return (
    <section>
      <SecLabel>Semana tipo</SecLabel>
      <div className="space-y-2.5">
        {data.weeklyTemplate.map((d) => (
          <div key={d.day} className="flex gap-3 text-[13.5px] leading-snug">
            <span className="w-[4.5rem] shrink-0 text-ink-soft">{d.day}</span>
            <span>
              <span className="text-ink">{d.session}</span>
              <span className="text-ink-soft"> · {d.focus}</span>
            </span>
          </div>
        ))}
      </div>
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
