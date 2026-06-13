import { useState } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from "@headlessui/react";
import { Modal } from "../components/Modal";
import {
  getExerciseProgression,
  getExercises,
  getSessionDetail,
  getSessions,
  getTrainingSummary,
  type ExerciseProgression,
  type SessionSummary,
} from "../api/training";

const MES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
const DIA = ["dom", "lun", "mar", "mié", "jue", "vie", "sáb"];

function shortDate(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  return `${d.getDate()} ${MES[d.getMonth()]}`;
}
function weekday(iso: string): string {
  return DIA[new Date(`${iso}T00:00:00`).getDay()];
}
function daysSince(iso: string): number {
  return Math.max(0, Math.round((Date.now() - new Date(`${iso}T00:00:00`).getTime()) / 86400000));
}
function fmtKg(w: number): string {
  return (w % 1 === 0 ? String(w) : w.toFixed(1)).replace(".", ",");
}
function routineLabel(name: string): string {
  return name === "Leg" ? "Pierna" : name;
}

function SecLabel({ children }: { children: ReactNode }) {
  return <h2 className="mb-3 text-xs uppercase tracking-[0.13em] text-ink-soft">{children}</h2>;
}

export function Gym() {
  return (
    <div>
      <header className="mb-2 border-b border-line pb-3">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Entreno</h1>
      </header>
      <div className="space-y-9 pt-5">
        <Proxima />
        <div className="border-t border-line" />
        <Actividades />
        <div className="border-t border-line" />
        <Historial />
        <div className="border-t border-line" />
        <Progresion />
      </div>
    </div>
  );
}

function Proxima() {
  const navigate = useNavigate();
  const summary = useQuery({ queryKey: ["training-summary"], queryFn: getTrainingSummary });
  const s = summary.data;
  return (
    <section>
      <SecLabel>Próxima sesión</SecLabel>
      <div className="flex items-end justify-between gap-3">
        <div>
          <div className="text-[11px] uppercase tracking-wide text-ink-soft">Toca</div>
          <div className="mt-1.5 text-[32px] font-semibold leading-none text-ink">
            {s ? routineLabel(s.nextRoutine) : "—"}
          </div>
          {s?.lastWorkoutDate && s.lastWorkoutRoutine && (
            <div className="mt-2.5 text-[13px] text-ink-soft">
              Última · {routineLabel(s.lastWorkoutRoutine)} · hace {daysSince(s.lastWorkoutDate)} días
            </div>
          )}
        </div>
        <button
          onClick={() => void navigate({ to: "/chat", search: { seed: "Empiezo entreno", workout: true } })}
          className="h-12 shrink-0 rounded-full bg-ink px-6 text-[15px] font-medium text-paper hover:bg-ink/90"
        >
          Entrenar
        </button>
      </div>
      <p className="mt-4 text-xs leading-relaxed text-ink-soft">
        Rotación Push · Pull · Pierna · series de trabajo 3×6-8 · descanso 90 s
      </p>
    </section>
  );
}

// Strava activities arrive in a later slice; until then this is a connect placeholder.
function Actividades() {
  return (
    <section>
      <SecLabel>Actividades · Garmin</SecLabel>
      <div className="rounded-md border border-line px-5 py-7 text-center">
        <p className="text-sm text-ink-soft">
          Aquí verás tus rutas de bici, carrera y hike importadas de Strava.
        </p>
        <p className="mt-1 text-[12.5px] text-ink-soft/80">Conexión con Strava · próximamente</p>
      </div>
    </section>
  );
}

const COLLAPSED = 5;

function ICheck() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} className="size-4 text-ink/70">
      <path d="M5 12.5l4.5 4.5L19 7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
function IChevRight() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} className="size-4 text-ink-soft">
      <path d="M9 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function Historial() {
  const sessions = useQuery({ queryKey: ["training-sessions"], queryFn: getSessions });
  const [showAll, setShowAll] = useState(false);
  const [open, setOpen] = useState<SessionSummary | null>(null);
  const list = sessions.data ?? [];
  const visible = showAll ? list : list.slice(0, COLLAPSED);

  return (
    <section>
      <SecLabel>Historial de fuerza</SecLabel>
      {list.length === 0 && <p className="text-sm text-ink-soft">Sin sesiones todavía.</p>}
      {visible.map((s) => (
        <button
          key={s.id}
          onClick={() => setOpen(s)}
          className="flex min-h-11 w-full items-start justify-between gap-3 border-b border-line py-3 text-left"
        >
          <span className="min-w-0 pr-3">
            <span className="block text-[15px] text-ink">
              <span className="capitalize">{weekday(s.date)}</span> {shortDate(s.date)} · {routineLabel(s.routine)}
            </span>
            <span className="mt-0.5 block truncate text-[13px] text-ink-soft">{s.summary}</span>
          </span>
          <span className="flex shrink-0 items-center gap-2.5 pt-0.5">
            {s.completed ? (
              <ICheck />
            ) : (
              <span className="text-[11px] uppercase tracking-wide text-ink-soft">a medias</span>
            )}
            <IChevRight />
          </span>
        </button>
      ))}
      {list.length > COLLAPSED && (
        <button onClick={() => setShowAll((v) => !v)} className="mt-3 text-[13px] text-ink-soft">
          {showAll ? "Mostrar menos" : `Mostrar todos (${list.length})`}
        </button>
      )}
      {open && <SessionDetailModal session={open} onClose={() => setOpen(null)} />}
    </section>
  );
}

function SessionDetailModal({ session, onClose }: { session: SessionSummary; onClose: () => void }) {
  const detail = useQuery({
    queryKey: ["training-session", session.id],
    queryFn: () => getSessionDetail(session.id),
  });
  const d = detail.data;
  return (
    <Modal title={`${shortDate(session.date)} · ${routineLabel(session.routine)}`} onClose={onClose}>
      <p className={`mb-4 text-xs uppercase tracking-wide ${session.completed ? "text-ink" : "text-ink-soft"}`}>
        {session.completed ? "Completado" : "A medias"}
      </p>
      {d?.exercises.length ? (
        d.exercises.map((e) => (
          <div key={e.name} className="border-b border-line py-3 last:border-b-0">
            <div className="text-[15px] text-ink">{e.name}</div>
            <div className="mt-0.5 text-[13px] tabular-nums text-ink-soft">{e.sets}</div>
          </div>
        ))
      ) : (
        <p className="text-sm text-ink-soft">{session.summary}</p>
      )}
    </Modal>
  );
}

function GymLine({ progression }: { progression: ExerciseProgression }) {
  const pts = progression.points;
  if (pts.length === 0) return null;
  const W = 320;
  const H = 124;
  const pl = 6;
  const pr = 40;
  const pt = 18;
  const pb = 22;
  const single = pts.length === 1;
  const vals = pts.map((p) => p.weight);
  let mn = Math.min(...vals);
  let mx = Math.max(...vals);
  const pad = (mx - mn) * 0.28 || 2;
  mn -= pad;
  mx += pad;
  const x = (i: number) => (single ? W / 2 : pl + i * ((W - pl - pr) / (pts.length - 1)));
  const y = (v: number) => pt + (1 - (v - mn) / (mx - mn)) * (H - pt - pb);
  const line = pts.map((p, i) => `${x(i).toFixed(1)},${y(p.weight).toFixed(1)}`).join(" ");
  const li = pts.length - 1;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="mt-3 w-full select-none" style={{ overflow: "visible" }}>
      {!single && (
        <polyline points={line} fill="none" stroke="#1c1c1a" strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round" />
      )}
      {pts.map((p, i) => (
        <circle
          key={i}
          cx={x(i)}
          cy={y(p.weight)}
          r={i === li ? 3.6 : 2.2}
          fill={i === li ? "#1c1c1a" : "#faf9f7"}
          stroke="#1c1c1a"
          strokeWidth="1.4"
        />
      ))}
      <text x={x(li)} y={y(pts[li].weight) - 9} textAnchor="middle" fill="#1c1c1a" style={{ fontSize: 11, fontWeight: 600 }}>
        {fmtKg(pts[li].weight)}
      </text>
      {!single && (
        <text x={x(0)} y={H - 5} textAnchor="start" fill="#6b6963" style={{ fontSize: 10 }}>
          {shortDate(pts[0].date)}
        </text>
      )}
      <text x={x(li)} y={H - 5} textAnchor={single ? "middle" : "end"} fill="#6b6963" style={{ fontSize: 10 }}>
        {shortDate(pts[li].date)}
      </text>
    </svg>
  );
}

function Progresion() {
  const exercises = useQuery({ queryKey: ["training-exercises"], queryFn: getExercises });
  const [selected, setSelected] = useState<number | null>(null);
  const id = selected ?? exercises.data?.[0]?.id ?? null;
  const prog = useQuery({
    queryKey: ["training-progression", id],
    queryFn: () => getExerciseProgression(id!),
    enabled: id !== null,
  });

  if (!exercises.data?.length) return null;
  const selectedName = exercises.data.find((e) => e.id === id)?.name ?? "—";
  const p = prog.data;

  return (
    <section>
      <SecLabel>Progresión por ejercicio</SecLabel>
      <Listbox value={id ?? 0} onChange={setSelected}>
        <ListboxButton className="group flex min-h-12 w-full items-center justify-between gap-2 rounded-lg border border-line bg-paper px-4 text-left text-[15px] text-ink outline-none transition-colors data-[open]:border-ink-soft">
          <span className="truncate">{selectedName}</span>
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={1.5}
            className="size-4 shrink-0 text-ink-soft transition-transform group-data-[open]:rotate-180"
          >
            <path d="M6 9l6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </ListboxButton>
        <ListboxOptions
          anchor="bottom"
          transition
          className="z-50 max-h-72 w-[var(--button-width)] overflow-auto rounded-lg border border-line bg-paper p-1 shadow-lg [--anchor-gap:4px] outline-none data-[closed]:opacity-0 data-[closed]:transition data-[enter]:duration-100 data-[leave]:duration-75"
        >
          {exercises.data.map((e) => (
            <ListboxOption
              key={e.id}
              value={e.id}
              className="group flex cursor-pointer items-center justify-between gap-2 rounded-md px-4 py-2.5 text-[15px] text-ink-soft data-[focus]:bg-line data-[selected]:text-ink"
            >
              <span className="truncate">{e.name}</span>
              <span className="size-1.5 rounded-full bg-ink opacity-0 group-data-[selected]:opacity-100" />
            </ListboxOption>
          ))}
        </ListboxOptions>
      </Listbox>

      {p && p.lastWeight != null && (
        <>
          <div className="mt-4 flex items-end justify-between gap-3">
            <div>
              <div className="text-3xl font-semibold leading-none tabular-nums text-ink">
                {fmtKg(p.lastWeight)}
                <span className="ml-1.5 text-[15px] font-normal text-ink-soft">kg</span>
              </div>
              <div className="mt-2 text-[13px] text-ink-soft">
                Última serie · {fmtKg(p.lastWeight)}×{p.lastReps} · objetivo {p.target}
              </div>
            </div>
            {p.suggestedWeight != null && (
              <div className="text-right text-xs leading-snug">
                <div className="uppercase tracking-wide text-ink-soft">Sugerido</div>
                <div className="text-ink">{fmtKg(p.suggestedWeight)} kg</div>
              </div>
            )}
          </div>
          <GymLine progression={p} />
        </>
      )}
      {p && p.lastWeight == null && (
        <p className="mt-4 text-sm text-ink-soft">Sin series registradas de este ejercicio.</p>
      )}
    </section>
  );
}
