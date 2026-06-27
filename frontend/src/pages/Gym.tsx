import { useState } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
import { connectStrava, getActivities, syncStrava, type ActivityItem, type ActivityType, type SportVolume } from "../api/strava";
import { daysSince, routineLabel, shortDate, weekday } from "../lib/format";

function fmtKg(w: number): string {
  return (w % 1 === 0 ? String(w) : w.toFixed(1)).replace(".", ",");
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

function fmt1(n: number): string {
  return n.toFixed(1).replace(".", ",");
}
function fmtDuration(s: number): string {
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return h > 0 ? `${h}h${String(m).padStart(2, "0")}` : `${m}:${String(sec).padStart(2, "0")}`;
}
const ACT_LABEL: Record<ActivityType, string> = { bike: "Bici", run: "Correr", hike: "Hike", gym: "Gimnasio", other: "Actividad" };

// Spanish names for the long-tail Strava sport_types that fall outside our buckets.
const SPORT_ES: Record<string, string> = {
  Pilates: "Pilates", Yoga: "Yoga", Swim: "Natación", Walk: "Caminar", Elliptical: "Elíptica",
  StairStepper: "Escaladora", Rowing: "Remo", Kayaking: "Kayak", Canoeing: "Canoa", Surfing: "Surf",
  Skateboard: "Skate", Soccer: "Fútbol", Tennis: "Tenis", Golf: "Golf", RockClimbing: "Escalada",
};
// "WeightTraining" -> "Weight Training", left untouched if already a single word.
const deCamel = (s: string): string => s.replace(/([a-z])([A-Z])/g, "$1 $2");

// Known buckets keep their Spanish label; everything else is named by its real Strava sport.
// A gym activity linked to a logged strength session shows its routine ("Gimnasio · Push").
function actLabel(a: ActivityItem): string {
  if (a.type === "gym") return a.routine ? `Gimnasio · ${routineLabel(a.routine)}` : ACT_LABEL.gym;
  if (a.type !== "other") return ACT_LABEL[a.type];
  if (!a.sport) return ACT_LABEL.other;
  return SPORT_ES[a.sport] ?? deCamel(a.sport);
}

function ActIcon({ type }: { type: ActivityType }) {
  const common = { viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 1.5, className: "size-[22px] shrink-0 text-ink" } as const;
  if (type === "bike") {
    return (
      <svg {...common}>
        <circle cx="6" cy="17" r="3.2" />
        <circle cx="18" cy="17" r="3.2" />
        <path d="M6 17l4-7h5l3 7M9 7h3" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  if (type === "run") {
    return (
      <svg {...common}>
        <circle cx="15" cy="5" r="1.6" />
        <path d="M13 9l-3 2 2 3-2 5M13 9l3 1 2-1M12 14l3 1 1 4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  if (type === "gym") {
    return (
      <svg {...common}>
        <path d="M4 10v4M7 8v8M17 8v8M20 10v4M7 12h10" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  if (type === "hike") {
    return (
      <svg {...common}>
        <path d="M3 19l5-9 4 5 3-5 6 9z" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  return (
    <svg {...common}>
      <circle cx="12" cy="12" r="7.5" />
    </svg>
  );
}

function activityLine(a: ActivityItem): string {
  const parts: string[] = [];
  if (a.distanceKm != null && a.distanceKm > 0) parts.push(`${fmt1(a.distanceKm)} km`);
  if (a.durationS != null) parts.push(fmtDuration(a.durationS));
  if (a.elevationM != null && a.elevationM > 0) parts.push(`${Math.round(a.elevationM)} m D+`);
  if (a.avgHr != null) parts.push(`${Math.round(a.avgHr)} ppm`);
  return parts.join(" · ");
}

const VOL_SPORTS: { key: string; label: string }[] = [
  { key: "bike", label: "Bici" },
  { key: "run", label: "Correr" },
  { key: "hike", label: "Hike" },
  { key: "gym", label: "Gym" },
];
const fmtNum = (n: number): string => String(n).replace(".", ",");

// Per-sport weekly volume: sport chips, year-to-date total, and clickable weeks
// that reveal the selected week's value and breakdown.
function VolumenSemanal({ volume }: { volume: Record<string, SportVolume> }) {
  // Default to the first sport that has any volume, so a non-cyclist doesn't open on an empty "Bici" chart.
  const [sport, setSport] = useState(
    () => (VOL_SPORTS.find(({ key }) => volume[key]?.weeks.some((w) => w.value > 0)) ?? VOL_SPORTS[0]).key,
  );
  // Default to the current (last) week. All sports share the same 8-week window,
  // so the selected index stays valid when switching sport — no reset needed.
  const [wi, setWi] = useState(() => (volume["bike"]?.weeks.length ?? 1) - 1);
  const v: SportVolume | undefined = volume[sport];

  if (!v || v.weeks.length === 0) return null;
  const weeks = v.weeks;
  const selIdx = Math.min(wi, weeks.length - 1);
  const sel = weeks[selIdx];
  const max = Math.max(1, ...weeks.map((w) => w.value));

  return (
    <div>
      <div className="flex gap-1.5">
        {VOL_SPORTS.map(({ key, label }) => {
          const active = key === sport;
          return (
            <button
              key={key}
              onClick={() => setSport(key)}
              className={`h-9 flex-1 rounded-full text-[13px] transition-colors ${active ? "bg-ink text-paper" : "border border-line text-ink-soft hover:text-ink"}`}
            >
              {label}
            </button>
          );
        })}
      </div>

      <div className="mt-4 flex items-end justify-between gap-3">
        <div className="min-w-0">
          <div className="text-[26px] font-semibold leading-none tabular-nums text-ink">
            {fmtNum(sel.value)}
            <span className="ml-1.5 text-[14px] font-normal text-ink-soft">{v.unit}</span>
          </div>
          <div className="mt-1.5 text-[13px] text-ink-soft">
            {sel.label === "esta" ? "Semana actual" : `Semana del ${sel.label}`}
          </div>
        </div>
        <div className="shrink-0 text-right">
          <div className="whitespace-nowrap text-[11px] uppercase tracking-[0.08em] text-ink-soft">
            YTD {new Date().getFullYear()}
          </div>
          <div className="mt-1 space-y-0.5 text-[13px] tabular-nums text-ink">
            {v.ytd.distance && <div className="whitespace-nowrap">{v.ytd.distance}</div>}
            {v.ytd.elevation && <div className="whitespace-nowrap">{v.ytd.elevation}</div>}
            <div className="whitespace-nowrap">{v.ytd.time}</div>
          </div>
        </div>
      </div>

      <div className="mt-3 flex h-16 items-end gap-2">
        {weeks.map((w, i) => (
          <button
            key={i}
            onClick={() => setWi(i)}
            aria-label={`Semana ${w.label}`}
            className="group flex h-full flex-1 items-end justify-center"
          >
            <div
              className={`w-full max-w-[24px] rounded-sm transition-colors ${i === selIdx ? "bg-ink" : "bg-line group-hover:bg-ink-soft/40"}`}
              style={{ height: `${Math.max(3, (w.value / max) * 100)}%` }}
            />
          </button>
        ))}
      </div>
      <div className="mt-1.5 flex gap-2">
        {weeks.map((w, i) => (
          <button
            key={i}
            onClick={() => setWi(i)}
            className={`flex-1 text-center text-[10px] transition-colors ${i === selIdx ? "text-ink" : "text-ink-soft hover:text-ink"}`}
          >
            {w.label}
          </button>
        ))}
      </div>

      <div className="mt-3 border-t border-line pt-3">
        <p className="text-[13px] text-ink">{sel.sub}</p>
      </div>
    </div>
  );
}

function Actividades() {
  const queryClient = useQueryClient();
  const view = useQuery({ queryKey: ["activities"], queryFn: getActivities });
  const [showAll, setShowAll] = useState(false);
  const [open, setOpen] = useState<ActivityItem | null>(null);
  const sync = useMutation({
    mutationFn: syncStrava,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["activities"] }),
  });

  const v = view.data;

  if (v && !v.connected) {
    return (
      <section>
        <SecLabel>Actividades</SecLabel>
        <div className="rounded-md border border-line px-5 py-7 text-center">
          <p className="mb-4 text-sm text-ink-soft">
            Conecta Strava para ver tus rutas de bici, carrera y hike (importadas de Strava).
          </p>
          <button onClick={() => connectStrava()} className="h-11 rounded-full bg-ink px-5 text-sm font-medium text-paper">
            Conectar Strava
          </button>
        </div>
      </section>
    );
  }
  if (!v) return null;

  const summary: string[] = [];
  if (v.weekBikeKm > 0) summary.push(`Bici ${Math.round(v.weekBikeKm)} km`);
  if (v.weekRunKm > 0) summary.push(`Correr ${Math.round(v.weekRunKm)} km`);
  if (v.weekHikes > 0) summary.push(`${v.weekHikes} hike${v.weekHikes > 1 ? "s" : ""}`);
  if (v.weekMovingTimeS > 0) summary.push(fmtDuration(v.weekMovingTimeS));
  const visible = showAll ? v.activities : v.activities.slice(0, 4);

  return (
    <section>
      <SecLabel>
        <span className="flex w-full items-end justify-between">
          Actividades · Strava
          <button
            onClick={() => sync.mutate()}
            disabled={sync.isPending}
            className="text-[12px] normal-case tracking-normal text-ink-soft underline underline-offset-2 disabled:opacity-40"
          >
            {sync.isPending ? "Sincronizando…" : "↻ Sincronizar"}
          </button>
        </span>
      </SecLabel>
      <p className="text-[15px] leading-relaxed text-ink">
        <span className="text-ink-soft">Esta semana · </span>
        {summary.length ? summary.join(" · ") : "Sin actividad"}
      </p>
      {v.activities.length > 0 && (
        <div className="mt-5">
          <VolumenSemanal volume={v.volume} />
        </div>
      )}
      <div className="mt-6">
        {v.activities.length === 0 && (
          <p className="text-sm text-ink-soft">Sin actividades aún · sincroniza para traerlas.</p>
        )}
        {visible.map((a) => (
          <button
            key={a.id}
            onClick={() => setOpen(a)}
            className="flex min-h-11 w-full items-center gap-3 border-b border-line py-3 text-left last:border-b-0"
          >
            <ActIcon type={a.type} />
            <span className="min-w-0 flex-1">
              <span className="block text-[15px] text-ink">
                <span className="capitalize">{weekday(a.date)}</span> {shortDate(a.date)} · {actLabel(a)}
              </span>
              <span className="mt-0.5 block truncate text-[13px] tabular-nums text-ink-soft">{activityLine(a)}</span>
            </span>
            <IChevRight />
          </button>
        ))}
      </div>
      {v.activities.length > 4 && (
        <button onClick={() => setShowAll((x) => !x)} className="mt-3 text-[13px] text-ink-soft">
          {showAll ? "Mostrar menos" : `Mostrar todas (${v.activities.length})`}
        </button>
      )}
      {open && <ActivityDetailModal activity={open} onClose={() => setOpen(null)} />}
    </section>
  );
}

function ActivityDetailModal({ activity, onClose }: { activity: ActivityItem; onClose: () => void }) {
  const a = activity;
  const stats: [string, string][] = [];
  if (a.distanceKm != null && a.distanceKm > 0) stats.push(["Distancia", `${fmt1(a.distanceKm)} km`]);
  if (a.durationS != null) stats.push(["Duración", fmtDuration(a.durationS)]);
  if (a.elevationM != null && a.elevationM > 0) stats.push(["Desnivel", `${Math.round(a.elevationM)} m`]);
  if (a.avgHr != null) stats.push(["FC media", `${Math.round(a.avgHr)} ppm`]);
  if (a.avgSpeedKmh != null && a.avgSpeedKmh > 0) stats.push(["Velocidad", `${fmt1(a.avgSpeedKmh)} km/h`]);
  if (a.calories != null && a.calories > 0) stats.push(["Calorías", `${a.calories} kcal`]);
  return (
    <Modal title={`${actLabel(a)} · ${shortDate(a.date)}`} onClose={onClose}>
      {a.name && <p className="mb-4 text-sm text-ink-soft">{a.name}</p>}
      <div className="grid grid-cols-2 gap-x-6">
        {stats.map(([k, val]) => (
          <div key={k} className="flex items-baseline justify-between border-b border-line py-3">
            <span className="text-[13px] text-ink-soft">{k}</span>
            <span className="text-base tabular-nums text-ink">{val}</span>
          </div>
        ))}
      </div>
      <p className="mt-4 text-xs text-ink-soft">Importado de Strava · solo lectura.</p>
    </Modal>
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
          className="flex min-h-11 w-full items-start justify-between gap-3 border-b border-line py-3 text-left last:border-b-0"
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
      <p className={`mb-1 text-xs uppercase tracking-wide ${session.completed ? "text-ink" : "text-ink-soft"}`}>
        {session.completed ? "Completado" : "A medias"}
      </p>
      {(() => {
        const parts = [
          d?.durationS != null ? fmtDuration(d.durationS) : null,
          d?.avgHr != null ? `${d.avgHr} ppm` : null,
          d?.calories != null && d.calories > 0 ? `${d.calories} kcal` : null,
        ].filter(Boolean);
        return parts.length ? (
          <p className="mb-4 text-[13px] tabular-nums text-ink-soft">Strava · {parts.join(" · ")}</p>
        ) : (
          <div className="mb-4" />
        );
      })()}
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
  if (pts.length < 2) return null;
  const W = 320;
  const H = 124;
  const pl = 6;
  const pr = 40;
  const pt = 18;
  const pb = 22;
  const vals = pts.map((p) => p.weight);
  let mn = Math.min(...vals);
  let mx = Math.max(...vals);
  const pad = (mx - mn) * 0.28 || 2;
  mn -= pad;
  mx += pad;
  const x = (i: number) => pl + i * ((W - pl - pr) / (pts.length - 1));
  const y = (v: number) => pt + (1 - (v - mn) / (mx - mn)) * (H - pt - pb);
  const line = pts.map((p, i) => `${x(i).toFixed(1)},${y(p.weight).toFixed(1)}`).join(" ");
  const li = pts.length - 1;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="mt-3 w-full select-none" style={{ overflow: "visible" }}>
      <polyline points={line} fill="none" stroke="var(--color-ink)" strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round" />
      {pts.map((p, i) => (
        <circle
          key={i}
          cx={x(i)}
          cy={y(p.weight)}
          r={i === li ? 3.6 : 2.2}
          fill={i === li ? "var(--color-ink)" : "var(--color-paper)"}
          stroke="var(--color-ink)"
          strokeWidth="1.4"
        />
      ))}
      <text x={x(li)} y={y(pts[li].weight) - 9} textAnchor="middle" fill="var(--color-ink)" style={{ fontSize: 11, fontWeight: 600 }}>
        {fmtKg(pts[li].weight)}
      </text>
      <text x={x(0)} y={H - 5} textAnchor="start" fill="var(--color-ink-soft)" style={{ fontSize: 10 }}>
        {shortDate(pts[0].date)}
      </text>
      <text x={x(li)} y={H - 5} textAnchor="end" fill="#6b6963" style={{ fontSize: 10 }}>
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
