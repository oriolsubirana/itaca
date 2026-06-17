import { useState } from "react";
import type { ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Listbox,
  ListboxButton,
  ListboxOption,
  ListboxOptions,
  Tab,
  TabGroup,
  TabList,
  TabPanel,
  TabPanels,
} from "@headlessui/react";
import { AnalyteChart } from "../components/AnalyteChart";
import { LabReports } from "../components/LabReports";
import { MedicalDocuments } from "../components/MedicalDocuments";
import { Modal } from "../components/Modal";
import { daysSince, shortDate, shortDateY, today, weekday } from "../lib/format";
import {
  deleteEntry,
  deleteFlare,
  getEntry,
  getFlares,
  getSummary,
  saveEntry,
  startFlare,
  updateFlare,
  SEVERITY_LABELS,
  type DiaryEntry,
  type Flare,
} from "../api/health";
import { deleteMeal, getMeals, logMeal, MEAL_LABELS, MEAL_TYPES, type Meal } from "../api/nutrition";

function cap(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function IChevRight({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} className={`size-4 ${className}`}>
      <path d="M9 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function SectionLabel({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-3 flex items-end justify-between">
      <h2 className="text-xs uppercase tracking-[0.12em] text-ink-soft">{children}</h2>
      {action}
    </div>
  );
}

export function Salud() {
  const headerDate = `${weekday(today())}. ${shortDate(today())}`;
  return (
    <div>
      <header className="mb-3 flex items-baseline justify-between">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Salud</h1>
        <span className="text-sm text-ink-soft">{headerDate}</span>
      </header>
      <TabGroup>
        <TabList className="flex gap-6 border-b border-line">
          {["Diario", "Comidas", "Analíticas y documentos"].map((label) => (
            <Tab
              key={label}
              className="-mb-px border-b-2 border-transparent pb-2.5 text-sm text-ink-soft outline-none transition-colors data-[selected]:border-ink data-[selected]:text-ink"
            >
              {label}
            </Tab>
          ))}
        </TabList>
        <TabPanels>
          <TabPanel>
            <DiarioTab />
          </TabPanel>
          <TabPanel>
            <ComidasTab />
          </TabPanel>
          <TabPanel>
            <ArchivoTab />
          </TabPanel>
        </TabPanels>
      </TabGroup>
    </div>
  );
}

// ── Diario tab ──────────────────────────────────────────────────────────────

function DiarioTab() {
  return (
    <div className="space-y-8 pt-5">
      <FlareBanner />
      <TodayCard />
      <RecentDiary />
      <BrotesSection />
    </div>
  );
}

function FlareBanner() {
  const queryClient = useQueryClient();
  const flares = useQuery({ queryKey: ["flares"], queryFn: getFlares });
  const [editing, setEditing] = useState(false);
  const active = flares.data?.active;
  if (!active) return null;

  const onSaved = () => {
    setEditing(false);
    void queryClient.invalidateQueries({ queryKey: ["flares"] });
    void queryClient.invalidateQueries({ queryKey: ["health-summary"] });
  };

  return (
    <>
      <button
        onClick={() => setEditing(true)}
        className="flex w-full items-start gap-3 rounded-md border border-clinical/35 bg-clinical/[0.04] px-4 py-3 text-left"
      >
        <span className="mt-[6px] size-[7px] shrink-0 rounded-full bg-clinical" />
        <span>
          <span className="block text-[11px] font-semibold uppercase tracking-[0.1em] text-clinical">
            Brote activo
          </span>
          <span className="mt-0.5 block text-sm text-ink">
            {cap(SEVERITY_LABELS[active.severity])} · desde {shortDate(active.startDate)} ·{" "}
            {daysSince(active.startDate)} días
          </span>
        </span>
      </button>
      {editing && <FlareModal flare={active} onClose={() => setEditing(false)} onSaved={onSaved} />}
    </>
  );
}

function Stat({ v, l, alert }: { v: ReactNode; l: string; alert?: boolean }) {
  return (
    <div className="border-b border-r border-line px-3 py-3">
      <div className={`text-[22px] font-medium leading-none tabular-nums ${alert ? "text-clinical" : "text-ink"}`}>
        {v}
      </div>
      <div className="mt-1.5 text-[10.5px] uppercase tracking-wide text-ink-soft">{l}</div>
    </div>
  );
}

function TodayCard() {
  const date = today();
  const [showModal, setShowModal] = useState(false);
  const entry = useQuery({ queryKey: ["diary", date], queryFn: () => getEntry(date) });

  const e = entry.data;
  if (!e) return null;
  const logged = !isEmptyEntry(e);

  return (
    <div>
      <SectionLabel>Hoy</SectionLabel>
      {logged ? (
        <>
          <div className="grid grid-cols-3 border-l border-t border-line">
            <Stat v={e.bristol ?? "—"} l="Bristol" />
            <Stat v={e.bowelMovements ?? "—"} l="Deposic." />
            <Stat v={e.pain ?? "—"} l="Dolor" />
            <Stat v={e.urgency ?? "—"} l="Urgencia" />
            <Stat v={e.stress ?? "—"} l="Estrés" />
            <Stat v={e.blood ? "Sí" : "No"} l="Sangre" alert={e.blood} />
          </div>
          {e.notes && <p className="mt-3 text-sm leading-relaxed text-ink/75">{e.notes}</p>}
          <button
            onClick={() => setShowModal(true)}
            className="mt-4 h-12 w-full rounded-md border border-ink/85 text-sm font-medium text-ink transition-colors hover:bg-ink hover:text-paper"
          >
            Editar registro de hoy
          </button>
        </>
      ) : (
        <div className="rounded-md border border-line px-5 py-7 text-center">
          <p className="mb-4 text-sm text-ink-soft">Aún no has registrado tus síntomas de hoy.</p>
          <button
            onClick={() => setShowModal(true)}
            className="h-12 w-full rounded-md bg-ink text-sm font-medium text-paper"
          >
            Registrar hoy
          </button>
        </div>
      )}
      {showModal && <DiaryModal date={date} initial={e} onClose={() => setShowModal(false)} />}
    </div>
  );
}

const COLLAPSED = 5;

function RecentDiary() {
  const summary = useQuery({ queryKey: ["health-summary"], queryFn: () => getSummary(30) });
  const [editing, setEditing] = useState<DiaryEntry | null>(null);
  const [showAll, setShowAll] = useState(false);
  const entries = summary.data?.recentEntries ?? [];
  const visible = showAll ? entries : entries.slice(0, COLLAPSED);

  return (
    <div>
      <SectionLabel>Diario · últimos 30 días</SectionLabel>
      {entries.length === 0 && <p className="text-sm text-ink-soft">Sin registros todavía.</p>}
      {visible.map((e) => (
        <button
          key={e.date}
          onClick={() => setEditing(e)}
          className="flex min-h-11 w-full items-center justify-between gap-3 border-b border-line py-3 text-left"
          aria-label={`Editar el ${shortDate(e.date)}`}
        >
          <span className="min-w-0">
            <span className="block text-sm capitalize text-ink">
              {weekday(e.date)}. {shortDate(e.date)}
            </span>
            <span className="mt-0.5 block truncate text-[13px] text-ink-soft">
              {entrySummary(e) || "Sin datos"}
            </span>
          </span>
          <span className="flex shrink-0 items-center gap-2.5 pl-3">
            {e.blood && <span className="size-1.5 rounded-full bg-clinical" aria-label="Sangre" />}
            <IChevRight className="text-ink-soft" />
          </span>
        </button>
      ))}
      {entries.length > COLLAPSED && (
        <button onClick={() => setShowAll((v) => !v)} className="mt-3 text-[13px] text-ink-soft">
          {showAll ? "Mostrar menos" : `Mostrar todos (${entries.length})`}
        </button>
      )}
      {editing && <DiaryModal date={editing.date} initial={editing} onClose={() => setEditing(null)} />}
    </div>
  );
}

function Severity({ s }: { s: Flare["severity"] }) {
  const dot = s === "severe" ? "bg-clinical" : s === "moderate" ? "bg-ink" : "bg-ink-soft";
  return (
    <span
      className={`inline-flex items-center gap-1.5 text-xs uppercase tracking-wide ${
        s === "severe" ? "text-clinical" : "text-ink"
      }`}
    >
      <span className={`size-1.5 rounded-full ${dot}`} />
      {SEVERITY_LABELS[s]}
    </span>
  );
}

function BrotesSection() {
  const queryClient = useQueryClient();
  const flares = useQuery({ queryKey: ["flares"], queryFn: getFlares });
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<Flare | null>(null);
  const [showAll, setShowAll] = useState(false);

  const recent = flares.data?.recent ?? [];
  const hasActive = flares.data?.active != null;
  const visible = showAll ? recent : recent.slice(0, 3);

  const onSaved = () => {
    setShowCreate(false);
    setEditing(null);
    void queryClient.invalidateQueries({ queryKey: ["flares"] });
    void queryClient.invalidateQueries({ queryKey: ["health-summary"] });
  };

  return (
    <div>
      <SectionLabel>Brotes</SectionLabel>
      {recent.length === 0 && <p className="mb-3 text-sm text-ink-soft">Ninguno registrado.</p>}
      {visible.map((f) => (
        <button
          key={f.id}
          onClick={() => setEditing(f)}
          className="block w-full border-b border-line py-3.5 text-left"
          aria-label={`Editar brote del ${shortDateY(f.startDate)}`}
        >
          <span className="flex items-center justify-between gap-3">
            <span className="text-sm text-ink">
              {shortDateY(f.startDate)} — {f.endDate ? shortDateY(f.endDate) : "en curso"}
            </span>
            <Severity s={f.severity} />
          </span>
          {f.notes && (
            <span className="mt-1.5 line-clamp-2 block text-[13px] leading-relaxed text-ink-soft">{f.notes}</span>
          )}
        </button>
      ))}
      {recent.length > 3 && (
        <button onClick={() => setShowAll((v) => !v)} className="mt-3 text-[13px] text-ink-soft">
          {showAll ? "Mostrar menos" : `Mostrar todos (${recent.length})`}
        </button>
      )}
      {!hasActive && (
        <button
          onClick={() => setShowCreate(true)}
          className="mt-4 min-h-11 rounded-full border border-line px-5 text-sm text-ink-soft"
        >
          + Registrar brote
        </button>
      )}
      {showCreate && <FlareModal onClose={() => setShowCreate(false)} onSaved={onSaved} />}
      {editing && <FlareModal flare={editing} onClose={() => setEditing(null)} onSaved={onSaved} />}
    </div>
  );
}

// ── Analíticas y documentos tab ─────────────────────────────────────────────

function ArchivoTab() {
  return (
    <div className="space-y-9 pt-5">
      <div>
        <SectionLabel>Analíticas</SectionLabel>
        <AnalyteChart />
      </div>
      <div>
        <SectionLabel>Informes de laboratorio</SectionLabel>
        <LabReports />
      </div>
      <div>
        <SectionLabel>Documentos clínicos</SectionLabel>
        <MedicalDocuments />
      </div>
      <p className="border-t border-line pt-3 text-[11.5px] leading-relaxed text-ink-soft">
        Ítaca registra y muestra tus datos clínicos. No los interpreta ni ofrece consejo médico.
      </p>
    </div>
  );
}

// ── Comidas tab ─────────────────────────────────────────────────────────────

function ComidasTab() {
  const queryClient = useQueryClient();
  const meals = useQuery({ queryKey: ["meals"], queryFn: () => getMeals(14) });
  const [adding, setAdding] = useState(false);

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ["meals"] });
  const remove = useMutation({ mutationFn: deleteMeal, onSuccess: invalidate });

  const data = meals.data;
  const grouped = (data?.meals ?? []).reduce<Record<string, Meal[]>>((acc, m) => {
    (acc[m.date] ??= []).push(m);
    return acc;
  }, {});

  return (
    <div className="space-y-7 pt-5">
      <div className="flex items-center justify-between gap-3">
        <div>
          <div className="text-[22px] font-medium leading-none tabular-nums text-ink">
            {data ? `${data.onPlan}/${data.total}` : "—"}
          </div>
          <div className="mt-1.5 text-[11px] uppercase tracking-wide text-ink-soft">En pauta · últimos 14 días</div>
        </div>
        <button
          onClick={() => setAdding(true)}
          className="h-11 shrink-0 rounded-full bg-ink px-5 text-sm font-medium text-paper hover:bg-ink/90"
        >
          + Registrar comida
        </button>
      </div>

      <p className="rounded-md border border-line bg-line/[0.15] px-4 py-3 text-[13px] leading-relaxed text-ink-soft">
        Pregunta a Ítaca en el chat «¿qué ceno hoy?» o «hoy hago bici larga, ¿qué como?» y te propone según tu
        entreno y la pauta paleo antiinflamatoria.
      </p>

      {data && data.total === 0 && <p className="text-sm text-ink-soft">Aún no has registrado ninguna comida.</p>}

      {Object.entries(grouped).map(([date, dayMeals]) => (
        <div key={date}>
          <h3 className="mb-2 text-xs uppercase tracking-[0.12em] text-ink-soft">
            {weekday(date)}. {shortDate(date)}
          </h3>
          {dayMeals.map((m) => (
            <button
              key={m.id}
              onClick={() => {
                if (window.confirm("¿Eliminar esta comida?")) remove.mutate(m.id);
              }}
              className="flex w-full items-start gap-3 border-b border-line py-3 text-left last:border-b-0"
            >
              {m.onPlan != null && (
                <span
                  className={`mt-[7px] size-1.5 shrink-0 rounded-full ${m.onPlan ? "bg-income" : "bg-clinical"}`}
                  aria-label={m.onPlan ? "En pauta" : "Fuera de pauta"}
                />
              )}
              <span className="min-w-0 flex-1">
                <span className="block text-[15px] text-ink">{m.description}</span>
                <span className="mt-0.5 block text-[12px] uppercase tracking-[0.06em] text-ink-soft">
                  {MEAL_LABELS[m.mealType] ?? m.mealType}
                  {m.notes ? ` · ${m.notes}` : ""}
                </span>
              </span>
            </button>
          ))}
        </div>
      ))}

      {adding && <MealModal onClose={() => setAdding(false)} onSaved={() => { invalidate(); setAdding(false); }} />}
    </div>
  );
}

function MealModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [date, setDate] = useState(today());
  const [mealType, setMealType] = useState("dinner");
  const [description, setDescription] = useState("");
  const [onPlan, setOnPlan] = useState(true);
  const [notes, setNotes] = useState("");

  const save = useMutation({
    mutationFn: () =>
      logMeal({ date, mealType, description: description.trim(), onPlan, notes: notes.trim() || null }),
    onSuccess: onSaved,
  });

  return (
    <Modal title="Registrar comida" onClose={onClose}>
      <Field label="Tipo">
        <Listbox value={mealType} onChange={setMealType}>
          <div className="relative">
            <ListboxButton className="flex min-h-11 w-full items-center justify-between rounded-lg border border-line bg-paper px-4 text-base text-ink">
              {MEAL_LABELS[mealType]}
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" className="size-4 text-ink-soft">
                <path d="M6 9l6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </ListboxButton>
            <ListboxOptions className="absolute z-10 mt-1 w-full overflow-hidden rounded-lg border border-line bg-paper py-1 shadow-[0_8px_28px_rgba(28,28,26,0.12)]">
              {MEAL_TYPES.map((m) => (
                <ListboxOption
                  key={m.code}
                  value={m.code}
                  className="cursor-pointer px-4 py-2.5 text-base text-ink data-[focus]:bg-line/50 data-[selected]:font-medium"
                >
                  {m.label}
                </ListboxOption>
              ))}
            </ListboxOptions>
          </div>
        </Listbox>
      </Field>

      <Field label="Qué has comido">
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          placeholder="Ej.: salmón al horno con brócoli y boniato"
          className="w-full rounded-lg border border-line bg-paper px-4 py-2.5 text-base outline-none focus:border-ink-soft"
        />
      </Field>

      <Field label="Fecha">
        <input
          type="date"
          value={date}
          max={today()}
          onChange={(e) => setDate(e.target.value)}
          className="min-h-11 w-full rounded-lg border border-line bg-paper px-4 text-base outline-none focus:border-ink-soft"
        />
      </Field>

      <Field label="¿Encaja en la pauta?">
        <div className="flex gap-2">
          {[true, false].map((v) => (
            <button
              key={String(v)}
              onClick={() => setOnPlan(v)}
              className={`min-h-11 flex-1 rounded-full border text-sm ${
                onPlan === v ? "border-ink bg-ink text-paper" : "border-line text-ink-soft"
              }`}
            >
              {v ? "En pauta" : "Fuera"}
            </button>
          ))}
        </div>
      </Field>

      <Field label="Notas">
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={2}
          placeholder="Opcional"
          className="w-full rounded-lg border border-line bg-paper px-4 py-2.5 text-base outline-none focus:border-ink-soft"
        />
      </Field>

      <button
        onClick={() => save.mutate()}
        disabled={!description.trim() || save.isPending}
        className="mt-2 min-h-12 w-full rounded-lg bg-ink text-sm text-paper disabled:opacity-40"
      >
        Guardar comida
      </button>
    </Modal>
  );
}

// ── Flare modal (create / edit) ─────────────────────────────────────────────

function FlareModal({
  flare,
  onClose,
  onSaved,
}: {
  flare?: Flare;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [severity, setSeverity] = useState<Flare["severity"] | null>(flare?.severity ?? null);
  const [startDate, setStartDate] = useState(flare?.startDate ?? today());
  const [endDate, setEndDate] = useState(flare?.endDate ?? "");
  const [notes, setNotes] = useState(flare?.notes ?? "");

  const save = useMutation({
    mutationFn: () =>
      flare
        ? updateFlare(flare.id, {
            startDate,
            endDate,
            severity: severity!,
            notes: notes.trim(),
          })
        : startFlare(severity!, startDate, notes.trim() || undefined),
    onSuccess: onSaved,
  });
  const remove = useMutation({
    mutationFn: () => deleteFlare(flare!.id),
    onSuccess: onSaved,
  });

  return (
    <Modal title={flare ? "Editar brote" : "Registrar brote"} onClose={onClose}>
      <div className="mb-4">
        <p className="mb-2 text-xs uppercase tracking-wide text-ink-soft">Severidad</p>
        <div className="flex gap-2">
          {(Object.keys(SEVERITY_LABELS) as Flare["severity"][]).map((s) => (
            <button
              key={s}
              onClick={() => setSeverity(s)}
              className={`min-h-11 flex-1 rounded-full border text-sm ${
                severity === s ? "border-ink bg-ink text-paper" : "border-line text-ink-soft"
              }`}
            >
              {SEVERITY_LABELS[s]}
            </button>
          ))}
        </div>
      </div>

      <div className="mb-4">
        <p className="mb-2 text-xs uppercase tracking-wide text-ink-soft">Fecha de inicio</p>
        <input
          type="date"
          value={startDate}
          max={today()}
          onChange={(e) => setStartDate(e.target.value)}
          className="min-h-11 w-full rounded-lg border border-line bg-paper px-4 text-base outline-none focus:border-ink-soft"
        />
      </div>

      {flare && (
        <div className="mb-4">
          <p className="mb-2 text-xs uppercase tracking-wide text-ink-soft">
            Fecha de fin · vacía = en curso
          </p>
          <input
            type="date"
            value={endDate}
            min={startDate}
            max={today()}
            onChange={(e) => setEndDate(e.target.value)}
            className="min-h-11 w-full rounded-lg border border-line bg-paper px-4 text-base outline-none focus:border-ink-soft"
          />
        </div>
      )}

      <div className="mb-5">
        <p className="mb-2 text-xs uppercase tracking-wide text-ink-soft">Notas</p>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={2}
          placeholder="Opcional"
          className="w-full rounded-lg border border-line bg-paper px-4 py-2.5 text-base outline-none focus:border-ink-soft"
        />
      </div>

      <button
        onClick={() => save.mutate()}
        disabled={!severity || !startDate || save.isPending}
        className="min-h-12 w-full rounded-lg bg-ink text-sm text-paper disabled:opacity-40"
      >
        {flare ? "Guardar" : "Registrar"}
      </button>
      {flare && (
        <button
          onClick={() => {
            if (window.confirm("¿Eliminar este brote?")) remove.mutate();
          }}
          disabled={remove.isPending}
          className="mt-2 min-h-11 w-full text-sm text-red-800 disabled:opacity-40"
        >
          Eliminar brote
        </button>
      )}
    </Modal>
  );
}

// ── Diary entry helpers + modal ─────────────────────────────────────────────

function isEmptyEntry(e: DiaryEntry): boolean {
  return (
    e.bristol == null &&
    e.pain == null &&
    e.urgency == null &&
    e.bowelMovements == null &&
    e.stress == null &&
    !e.blood &&
    !e.notes
  );
}

function entrySummary(e: DiaryEntry): string {
  const parts: string[] = [];
  if (e.bristol != null) parts.push(`Bristol ${e.bristol}`);
  if (e.bowelMovements != null) parts.push(`${e.bowelMovements} dep.`);
  if (e.pain != null && e.pain > 0) parts.push(`dolor ${e.pain}`);
  if (e.urgency != null && e.urgency > 0) parts.push(`urgencia ${e.urgency}`);
  if (e.stress != null && e.stress > 0) parts.push(`estrés ${e.stress}`);
  return parts.join(" · ");
}

function DiaryModal({
  date,
  initial,
  onClose,
}: {
  date: string;
  initial: DiaryEntry;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Partial<DiaryEntry>>(initial);

  const invalidateAndClose = () => {
    void queryClient.invalidateQueries({ queryKey: ["diary", date] });
    void queryClient.invalidateQueries({ queryKey: ["health-summary"] });
    onClose();
  };
  const save = useMutation({
    mutationFn: () => saveEntry(date, form),
    onSuccess: invalidateAndClose,
  });
  const remove = useMutation({
    mutationFn: () => deleteEntry(date),
    onSuccess: invalidateAndClose,
  });

  const set = <K extends keyof DiaryEntry>(key: K, value: DiaryEntry[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
  };

  return (
    <Modal title={date === today() ? "Diario de hoy" : `Diario del ${shortDateY(date)}`} onClose={onClose}>
      <Field label="Bristol">
        <div className="flex gap-1.5">
          {[1, 2, 3, 4, 5, 6, 7].map((n) => (
            <button
              key={n}
              onClick={() => set("bristol", form.bristol === n ? null : n)}
              className={`size-11 rounded-full border text-sm ${
                form.bristol === n ? "border-ink bg-ink text-paper" : "border-line text-ink-soft"
              }`}
            >
              {n}
            </button>
          ))}
        </div>
      </Field>

      <Field label={`Deposiciones${form.bowelMovements != null ? ` · ${form.bowelMovements}` : ""}`}>
        <Stepper value={form.bowelMovements ?? 0} onChange={(v) => set("bowelMovements", v)} />
      </Field>

      <Slider label="Dolor" value={form.pain} onChange={(v) => set("pain", v)} />
      <Slider label="Urgencia" value={form.urgency} onChange={(v) => set("urgency", v)} />
      <Slider label="Estrés" value={form.stress} onChange={(v) => set("stress", v)} />

      <Field label="Sangre">
        <button
          onClick={() => set("blood", !form.blood)}
          className={`min-h-11 rounded-full border px-5 text-sm ${
            form.blood ? "border-clinical bg-clinical text-paper" : "border-line text-ink-soft"
          }`}
        >
          {form.blood ? "Sí" : "No"}
        </button>
      </Field>

      <Field label="Notas">
        <textarea
          value={form.notes ?? ""}
          onChange={(e) => set("notes", e.target.value)}
          rows={2}
          className="w-full rounded-lg border border-line bg-paper px-4 py-2.5 text-base outline-none focus:border-ink-soft"
        />
      </Field>

      <button
        onClick={() => save.mutate()}
        disabled={save.isPending}
        className="mt-2 min-h-12 w-full rounded-lg bg-ink text-sm text-paper disabled:opacity-40"
      >
        Guardar
      </button>
      {!isEmptyEntry(initial) && (
        <button
          onClick={() => {
            if (window.confirm("¿Eliminar el registro de este día?")) remove.mutate();
          }}
          disabled={remove.isPending}
          className="mt-2 min-h-11 w-full text-sm text-red-800 disabled:opacity-40"
        >
          Eliminar registro
        </button>
      )}
    </Modal>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="mb-4">
      <p className="mb-2 text-xs uppercase tracking-wide text-ink-soft">{label}</p>
      {children}
    </div>
  );
}

function Stepper({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  return (
    <div className="flex items-center gap-3">
      <button
        onClick={() => onChange(Math.max(0, value - 1))}
        className="size-11 rounded-full border border-line text-lg text-ink-soft"
      >
        −
      </button>
      <span className="w-6 text-center text-base">{value}</span>
      <button
        onClick={() => onChange(value + 1)}
        className="size-11 rounded-full border border-line text-lg text-ink-soft"
      >
        +
      </button>
    </div>
  );
}

function Slider({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number | null | undefined;
  onChange: (v: number) => void;
}) {
  return (
    <Field label={`${label}${value != null ? ` · ${value}` : ""}`}>
      <input
        type="range"
        min={0}
        max={10}
        value={value ?? 0}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full accent-[#1c1c1a]"
      />
    </Field>
  );
}
