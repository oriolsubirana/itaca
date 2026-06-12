import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PageTitle } from "../components/PageTitle";
import { AnalyteChart } from "../components/AnalyteChart";
import { LabReports } from "../components/LabReports";
import { Modal } from "../components/Modal";
import {
  endFlare,
  getEntry,
  getFlares,
  getSummary,
  saveEntry,
  startFlare,
  SEVERITY_LABELS,
  type DiaryEntry,
  type Flare,
} from "../api/health";

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function Salud() {
  return (
    <>
      <PageTitle>Salud</PageTitle>
      <FlareBanner />
      <TodaySection />
      <section className="border-t border-line py-5">
        <h2 className="mb-4 text-xs uppercase tracking-[0.15em] text-ink-soft">
          Analíticas
        </h2>
        <AnalyteChart />
        <LabReports />
      </section>
      <RecentEntries />
    </>
  );
}

function FlareBanner() {
  const queryClient = useQueryClient();
  const flares = useQuery({ queryKey: ["flares"], queryFn: getFlares });
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["flares"] });
    void queryClient.invalidateQueries({ queryKey: ["health-summary"] });
  };
  const end = useMutation({ mutationFn: endFlare, onSuccess: invalidate });
  const [showModal, setShowModal] = useState(false);

  const active = flares.data?.active;
  if (flares.isPending) return null;

  if (active) {
    return (
      <section className="mb-6 flex min-h-12 items-center justify-between rounded-lg border border-red-200 bg-red-50 px-4 py-3">
        <p className="text-sm text-red-900">
          Brote {SEVERITY_LABELS[active.severity]} desde el {active.startDate}
        </p>
        <button
          onClick={() => end.mutate()}
          disabled={end.isPending}
          className="min-h-11 px-2 text-xs uppercase tracking-wide text-red-900"
        >
          Finalizar
        </button>
      </section>
    );
  }

  return (
    <section className="mb-6">
      <button
        onClick={() => setShowModal(true)}
        className="min-h-11 rounded-full border border-line px-5 text-sm text-ink-soft"
      >
        + Registrar brote
      </button>
      {showModal && (
        <FlareModal
          onClose={() => setShowModal(false)}
          onSaved={() => {
            setShowModal(false);
            invalidate();
          }}
        />
      )}
    </section>
  );
}

function FlareModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [severity, setSeverity] = useState<Flare["severity"] | null>(null);
  const [date, setDate] = useState(today());
  const [notes, setNotes] = useState("");
  const save = useMutation({
    mutationFn: () => startFlare(severity!, date, notes.trim() || undefined),
    onSuccess: onSaved,
  });

  return (
    <Modal title="Registrar brote" onClose={onClose}>
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
          value={date}
          max={today()}
          onChange={(e) => setDate(e.target.value)}
          className="min-h-11 w-full rounded-lg border border-line bg-paper px-4 text-base outline-none focus:border-ink-soft"
        />
      </div>

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
        disabled={!severity || !date || save.isPending}
        className="min-h-12 w-full rounded-lg bg-ink text-sm text-paper disabled:opacity-40"
      >
        Registrar
      </button>
    </Modal>
  );
}

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

function TodaySection() {
  const date = today();
  const [showModal, setShowModal] = useState(false);
  const entry = useQuery({
    queryKey: ["diary", date],
    queryFn: () => getEntry(date),
  });

  if (!entry.data) return null;
  const empty = isEmptyEntry(entry.data);

  return (
    <section className="border-t border-line py-5">
      <h2 className="mb-3 text-xs uppercase tracking-[0.15em] text-ink-soft">
        Hoy
      </h2>
      <div className="flex items-center gap-3">
        <p className="flex-1 text-sm leading-relaxed">
          {empty ? (
            <span className="text-ink-soft">Sin registrar todavía.</span>
          ) : (
            <>
              {entrySummary(entry.data)}
              {entry.data.blood && (
                <span
                  className="ml-2 inline-block size-2 rounded-full bg-red-800"
                  aria-label="Sangre"
                />
              )}
            </>
          )}
        </p>
        <button
          onClick={() => setShowModal(true)}
          className="min-h-11 shrink-0 rounded-full border border-line px-5 text-sm text-ink-soft"
        >
          {empty ? "+ Registrar día" : "Editar"}
        </button>
      </div>
      {showModal && (
        <DiaryModal date={date} initial={entry.data} onClose={() => setShowModal(false)} />
      )}
    </section>
  );
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

  const save = useMutation({
    mutationFn: () => saveEntry(date, form),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["diary", date] });
      void queryClient.invalidateQueries({ queryKey: ["health-summary"] });
      onClose();
    },
  });

  const set = <K extends keyof DiaryEntry>(key: K, value: DiaryEntry[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
  };

  return (
    <Modal title="Diario de hoy" onClose={onClose}>
      <Field label="Bristol">
        <div className="flex gap-1.5">
          {[1, 2, 3, 4, 5, 6, 7].map((n) => (
            <button
              key={n}
              onClick={() => set("bristol", form.bristol === n ? null : n)}
              className={`size-11 rounded-full border text-sm ${
                form.bristol === n
                  ? "border-ink bg-ink text-paper"
                  : "border-line text-ink-soft"
              }`}
            >
              {n}
            </button>
          ))}
        </div>
      </Field>

      <Field label={`Deposiciones${form.bowelMovements != null ? ` · ${form.bowelMovements}` : ""}`}>
        <Stepper
          value={form.bowelMovements ?? 0}
          onChange={(v) => set("bowelMovements", v)}
        />
      </Field>

      <Slider label="Dolor" value={form.pain} onChange={(v) => set("pain", v)} />
      <Slider label="Urgencia" value={form.urgency} onChange={(v) => set("urgency", v)} />
      <Slider label="Estrés" value={form.stress} onChange={(v) => set("stress", v)} />

      <Field label="Sangre">
        <button
          onClick={() => set("blood", !form.blood)}
          className={`min-h-11 rounded-full border px-5 text-sm ${
            form.blood ? "border-red-800 bg-red-800 text-paper" : "border-line text-ink-soft"
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
    </Modal>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
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

function RecentEntries() {
  const summary = useQuery({
    queryKey: ["health-summary"],
    queryFn: () => getSummary(30),
  });
  const entries = summary.data?.recentEntries ?? [];

  return (
    <section className="border-t border-line py-5">
      <h2 className="mb-3 text-xs uppercase tracking-[0.15em] text-ink-soft">
        Últimos 30 días
      </h2>
      {entries.length === 0 && (
        <p className="text-sm text-ink-soft">Sin registros todavía.</p>
      )}
      <ul>
        {entries.map((e) => (
          <li
            key={e.date}
            className="flex items-center gap-4 border-b border-line py-3 text-sm last:border-b-0"
          >
            <span className="w-24 shrink-0 text-ink-soft">{e.date.slice(5)}</span>
            <span className="flex-1">
              {e.bristol != null && `Bristol ${e.bristol}`}
              {e.bowelMovements != null && ` · ${e.bowelMovements} dep.`}
              {e.pain != null && e.pain > 0 && ` · dolor ${e.pain}`}
            </span>
            {e.blood && <span className="size-2 rounded-full bg-red-800" aria-label="Sangre" />}
          </li>
        ))}
      </ul>
    </section>
  );
}
