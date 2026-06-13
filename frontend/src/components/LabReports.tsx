import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  confirmLabReport,
  deleteLabReport,
  deleteLabResult,
  discardLabReport,
  getLabReportDetail,
  getLabReports,
  openLabReportFile,
  reextractLabReport,
  renormalizeAllLabReports,
  renormalizeLabReport,
  semanticRenormalizeLabReports,
  setLabReportCategory,
  updateLabResult,
  uploadLabReports,
  STATUS_LABELS,
} from "../api/labs";
import type { LabResult, LabResultUpdate } from "../api/labs";
import { CATEGORY_ORDER } from "../api/categories";
import type { ReportCategory } from "../api/categories";
import { CategoryFilter, CategoryPicker } from "./CategoryControls";
import { Modal } from "./Modal";

/** Long histories collapse to the most recent few rows (90% phone usage). */
const COLLAPSED_COUNT = 5;

/** Upload + review flow: nothing reaches the charts until confirmed. */
export function LabReports() {
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [openReport, setOpenReport] = useState<number | null>(null);
  const [showAll, setShowAll] = useState(false);
  const [filter, setFilter] = useState<ReportCategory | "all">("all");

  // Poll while any extraction job is running so reports refresh on their own.
  const reports = useQuery({
    queryKey: ["lab-reports"],
    queryFn: getLabReports,
    refetchInterval: (query) => (query.state.data?.some((r) => r.extracting) ? 3000 : false),
  });

  const upload = useMutation({
    mutationFn: uploadLabReports,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["lab-reports"] }),
  });
  const [renormCount, setRenormCount] = useState<number | null>(null);
  const onRenormalized = (data: { changed: number }) => {
    void queryClient.invalidateQueries({ queryKey: ["lab-reports"] });
    void queryClient.invalidateQueries({ queryKey: ["lab-report"] });
    void queryClient.invalidateQueries({ queryKey: ["analytes"] });
    void queryClient.invalidateQueries({ queryKey: ["analyte-series"] });
    setRenormCount(data.changed);
    setTimeout(() => setRenormCount(null), 4000);
  };
  const renormalizeAll = useMutation({ mutationFn: renormalizeAllLabReports, onSuccess: onRenormalized });
  const semanticRenormalize = useMutation({ mutationFn: semanticRenormalizeLabReports, onSuccess: onRenormalized });

  const all = reports.data ?? [];
  const present = CATEGORY_ORDER.filter((c) => all.some((r) => r.category === c));
  const filtered = filter === "all" ? all : all.filter((r) => r.category === filter);
  const visible = showAll ? filtered : filtered.slice(0, COLLAPSED_COUNT);
  const hidden = filtered.length - visible.length;

  return (
    <div>
      <input
        ref={fileInput}
        type="file"
        accept="application/pdf"
        multiple
        className="hidden"
        onChange={(e) => {
          const files = Array.from(e.target.files ?? []);
          if (files.length > 0) upload.mutate(files);
          e.target.value = "";
        }}
      />
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <button
          onClick={() => fileInput.current?.click()}
          disabled={upload.isPending}
          className="min-h-11 rounded-full border border-line px-5 text-sm text-ink disabled:opacity-40"
        >
          {upload.isPending ? "Subiendo…" : "+ Subir analítica"}
        </button>
        {all.length > 0 && (
          <>
            <button
              onClick={() => renormalizeAll.mutate()}
              disabled={renormalizeAll.isPending || semanticRenormalize.isPending}
              className="min-h-11 text-sm text-ink-soft underline underline-offset-2 disabled:opacity-40"
            >
              {renormalizeAll.isPending ? "Re-normalizando…" : "↻ Re-normalizar"}
            </button>
            <button
              onClick={() => semanticRenormalize.mutate()}
              disabled={semanticRenormalize.isPending || renormalizeAll.isPending}
              className="min-h-11 text-sm text-ink-soft underline underline-offset-2 disabled:opacity-40"
            >
              {semanticRenormalize.isPending
                ? "Normalizando con IA…"
                : renormCount != null
                  ? `Normalizado (${renormCount})`
                  : "✨ Normalizar con IA"}
            </button>
          </>
        )}
      </div>

      {present.length > 1 && (
        <CategoryFilter
          value={filter}
          present={present}
          onChange={(c) => {
            setFilter(c);
            setShowAll(false);
          }}
        />
      )}

      <ul>
        {visible.map((r) => (
          <li key={r.id} className="border-b border-line last:border-b-0">
            <button
              onClick={() => setOpenReport(openReport === r.id ? null : r.id)}
              className="flex min-h-12 w-full items-center gap-3 py-2 text-left text-sm"
            >
              <span className="shrink-0 text-ink-soft">{r.date}</span>
              <span className="min-w-0 flex-1 truncate">
                {r.laboratory ?? r.filename ?? "—"}
              </span>
              <span
                className={`shrink-0 text-xs uppercase tracking-wide ${
                  r.status === "pending_review" ? "text-amber-700" : "text-ink-soft"
                }`}
              >
                {r.extracting ? "procesando…" : STATUS_LABELS[r.status]}
              </span>
            </button>
            {openReport === r.id && <ReportReview reportId={r.id} onDone={() => setOpenReport(null)} />}
          </li>
        ))}
      </ul>
      {hidden > 0 && (
        <button
          onClick={() => setShowAll(true)}
          className="min-h-11 w-full text-sm text-ink-soft"
        >
          Mostrar todos ({filtered.length})
        </button>
      )}
      {showAll && filtered.length > COLLAPSED_COUNT && (
        <button
          onClick={() => setShowAll(false)}
          className="min-h-11 w-full text-sm text-ink-soft"
        >
          Mostrar menos
        </button>
      )}
    </div>
  );
}

function ReportReview({ reportId, onDone }: { reportId: number; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<LabResult | null>(null);
  const detail = useQuery({
    queryKey: ["lab-report", reportId],
    queryFn: () => getLabReportDetail(reportId),
    refetchInterval: (query) => (query.state.data?.report.extracting ? 3000 : false),
  });

  // Row-level edits keep the panel open; report-level actions close it.
  const refreshDetail = () => {
    void queryClient.invalidateQueries({ queryKey: ["lab-report", reportId] });
    void queryClient.invalidateQueries({ queryKey: ["lab-reports"] });
  };
  const invalidate = () => {
    refreshDetail();
    void queryClient.invalidateQueries({ queryKey: ["analytes"] });
    void queryClient.invalidateQueries({ queryKey: ["analyte-series"] });
    onDone();
  };
  const confirm = useMutation({
    mutationFn: () => confirmLabReport(reportId),
    onSuccess: invalidate,
  });
  const discard = useMutation({
    mutationFn: () => discardLabReport(reportId),
    onSuccess: invalidate,
  });
  const remove = useMutation({
    mutationFn: () => deleteLabReport(reportId),
    onSuccess: invalidate,
  });
  const reextract = useMutation({
    mutationFn: () => reextractLabReport(reportId),
    onSuccess: refreshDetail,
  });
  const toggleReviewed = useMutation({
    mutationFn: (r: LabResult) => updateLabResult(r.id, { reviewed: !r.reviewed }),
    onSuccess: refreshDetail,
  });
  const viewFile = useMutation({ mutationFn: () => openLabReportFile(reportId) });
  const setCategory = useMutation({
    mutationFn: (c: ReportCategory | null) => setLabReportCategory(reportId, c),
    onSuccess: refreshDetail,
  });
  const renormalize = useMutation({
    mutationFn: () => renormalizeLabReport(reportId),
    onSuccess: () => {
      refreshDetail();
      void queryClient.invalidateQueries({ queryKey: ["analytes"] });
      void queryClient.invalidateQueries({ queryKey: ["analyte-series"] });
    },
  });

  if (!detail.data) return null;
  const { report, results } = detail.data;
  const editable = report.status === "pending_review" && !report.extracting;
  const reviewedCount = results.filter((r) => r.reviewed).length;

  return (
    <div className="mb-3 rounded-lg border border-line p-4">
      <div className="mb-3 border-b border-line pb-3">
        <p className="truncate text-sm font-medium">
          {report.filename ?? report.laboratory ?? "Informe"}
        </p>
        <p className="truncate text-xs text-ink-soft">
          {report.laboratory && `${report.laboratory} · `}
          {report.date}
        </p>
      </div>
      <div className="mb-3 flex items-center gap-2">
        <span className="text-xs text-ink-soft">Tema:</span>
        <CategoryPicker value={report.category} onChange={(c) => setCategory.mutate(c)} />
      </div>
      <button
        onClick={() => viewFile.mutate()}
        disabled={viewFile.isPending}
        className="mb-3 min-h-11 w-full rounded-lg border border-line text-sm text-ink-soft disabled:opacity-40"
      >
        {viewFile.isPending ? "Abriendo…" : "Ver documento (PDF)"}
      </button>
      {report.extracting && (
        <p className="mb-3 flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
          <span className="inline-block size-3 shrink-0 animate-spin rounded-full border-2 border-amber-700 border-t-transparent" />
          Procesando el PDF… los resultados aparecerán aquí solos.
        </p>
      )}
      {results.length === 0 ? (
        <div className="py-2">
          {!report.extracting && (
            <>
              <p className="mb-3 text-sm text-ink-soft">
                Sin resultados — la extracción no encontró nada en el PDF.
              </p>
              <button
                onClick={() => reextract.mutate()}
                disabled={reextract.isPending}
                className="min-h-11 rounded-full border border-line px-5 text-sm text-ink disabled:opacity-40"
              >
                ↻ Reprocesar extracción
              </button>
            </>
          )}
        </div>
      ) : (
        <>
          <ul className="mb-3 max-h-80 overflow-y-auto overscroll-contain">
            {results.map((res) => (
              <li
                key={res.id}
                className="grid grid-cols-[auto_1fr_auto] items-center gap-x-2 border-b border-line last:border-b-0"
              >
                {editable && (
                  <button
                    onClick={() => toggleReviewed.mutate(res)}
                    aria-label={res.reviewed ? "Desmarcar revisado" : "Marcar revisado"}
                    className="flex size-11 shrink-0 items-center justify-center"
                  >
                    <span
                      className={`flex size-5 items-center justify-center rounded-full border text-[11px] ${
                        res.reviewed ? "border-ink bg-ink text-paper" : "border-line text-transparent"
                      }`}
                    >
                      ✓
                    </span>
                  </button>
                )}
                <button
                  onClick={() => editable && setEditing(res)}
                  disabled={!editable}
                  className={`min-w-0 py-2 text-left ${!editable ? "col-span-2 cursor-default" : ""} ${
                    res.reviewed ? "opacity-50" : ""
                  }`}
                >
                  <span className="block truncate text-sm leading-snug">
                    {res.analyteName ?? res.rawName}
                    {!res.analyteName && (
                      <span className="ml-1.5 align-middle text-[11px] uppercase tracking-wide text-amber-700">
                        sin normalizar
                      </span>
                    )}
                  </span>
                  {res.date && (
                    <span className="block text-xs tabular-nums text-ink-soft">{res.date}</span>
                  )}
                </button>
                <button
                  onClick={() => editable && setEditing(res)}
                  disabled={!editable}
                  className={`max-w-44 py-2 text-right sm:max-w-64 ${!editable ? "cursor-default" : ""} ${
                    res.reviewed ? "opacity-50" : ""
                  }`}
                >
                  <span className="block break-words text-sm font-medium tabular-nums">
                    {res.value ?? res.textValue} {res.unit ?? ""}
                  </span>
                  {res.refMax != null && (
                    <span className="block text-xs tabular-nums text-ink-soft">
                      ref {res.refMin ?? 0}–{res.refMax}
                    </span>
                  )}
                </button>
              </li>
            ))}
          </ul>
          <div className="mb-3 flex items-center justify-between gap-3">
            <p className="text-xs text-ink-soft">
              {editable && `${reviewedCount}/${results.length} revisados · `}
              {results.filter((r) => r.analyteName).length} normalizados
            </p>
            <div className="flex shrink-0 items-center gap-3">
              <button
                onClick={() => renormalize.mutate()}
                disabled={renormalize.isPending}
                className="text-xs text-ink-soft underline underline-offset-2 disabled:opacity-40"
              >
                {renormalize.isPending ? "normalizando…" : "↻ re-normalizar"}
              </button>
              {editable && (
                <button
                  onClick={() => reextract.mutate()}
                  disabled={reextract.isPending}
                  className="text-xs text-ink-soft underline underline-offset-2 disabled:opacity-40"
                >
                  {reextract.isPending ? "reprocesando…" : "↻ reprocesar"}
                </button>
              )}
            </div>
          </div>
        </>
      )}
      {editable && results.length > 0 && (
        <div className="flex gap-2">
          <button
            onClick={() => confirm.mutate()}
            disabled={confirm.isPending}
            className="min-h-11 flex-1 rounded-lg bg-ink text-sm text-paper disabled:opacity-40"
          >
            Confirmar
          </button>
          <button
            onClick={() => discard.mutate()}
            disabled={discard.isPending}
            className="min-h-11 flex-1 rounded-lg border border-line text-sm text-ink-soft disabled:opacity-40"
          >
            Descartar
          </button>
        </div>
      )}
      <button
        onClick={() => {
          if (window.confirm("¿Eliminar este informe y todos sus resultados?")) remove.mutate();
        }}
        disabled={remove.isPending}
        className="mt-2 min-h-11 w-full text-sm text-red-800 disabled:opacity-40"
      >
        Eliminar informe
      </button>
      {editing && (
        <EditResultModal
          key={editing.id}
          result={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            refreshDetail();
          }}
        />
      )}
    </div>
  );
}

function EditResultModal({
  result,
  onClose,
  onSaved,
}: {
  result: LabResult;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [date, setDate] = useState(result.date ?? "");
  const [value, setValue] = useState(result.value?.toString() ?? "");
  const [textValue, setTextValue] = useState(result.textValue ?? "");
  const [unit, setUnit] = useState(result.unit ?? "");

  const save = useMutation({
    mutationFn: () => {
      const update: LabResultUpdate = { date, textValue, unit, reviewed: true };
      if (value.trim() !== "") update.value = Number(value);
      else if (result.value != null) update.clearValue = true; // numeric -> text-only
      return updateLabResult(result.id, update);
    },
    onSuccess: onSaved,
  });
  const remove = useMutation({
    mutationFn: () => deleteLabResult(result.id),
    onSuccess: onSaved,
  });

  const valueInvalid = value.trim() !== "" && Number.isNaN(Number(value));
  const nothingLeft = value.trim() === "" && textValue.trim() === "";

  return (
    <Modal title="Corregir resultado" onClose={onClose}>
      <p className="mb-4 text-sm font-medium">{result.analyteName ?? result.rawName}</p>
      <label className="mb-3 block text-sm">
        <span className="mb-1 block text-xs text-ink-soft">Fecha (vacío = la del informe)</span>
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className="min-h-11 w-full rounded-lg border border-line bg-transparent px-3 text-sm"
        />
      </label>
      <label className="mb-3 block text-sm">
        <span className="mb-1 block text-xs text-ink-soft">Valor numérico</span>
        <input
          type="text"
          inputMode="decimal"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          className="min-h-11 w-full rounded-lg border border-line bg-transparent px-3 text-sm"
        />
      </label>
      <label className="mb-3 block text-sm">
        <span className="mb-1 block text-xs text-ink-soft">Valor textual (neg, +, normal…)</span>
        <input
          type="text"
          value={textValue}
          onChange={(e) => setTextValue(e.target.value)}
          className="min-h-11 w-full rounded-lg border border-line bg-transparent px-3 text-sm"
        />
      </label>
      <label className="mb-4 block text-sm">
        <span className="mb-1 block text-xs text-ink-soft">Unidad</span>
        <input
          type="text"
          value={unit}
          onChange={(e) => setUnit(e.target.value)}
          className="min-h-11 w-full rounded-lg border border-line bg-transparent px-3 text-sm"
        />
      </label>
      <button
        onClick={() => save.mutate()}
        disabled={save.isPending || valueInvalid || nothingLeft}
        className="min-h-11 w-full rounded-lg bg-ink text-sm text-paper disabled:opacity-40"
      >
        Guardar y marcar revisado
      </button>
      <button
        onClick={() => {
          if (window.confirm("¿Eliminar este resultado?")) remove.mutate();
        }}
        disabled={remove.isPending}
        className="mt-2 min-h-11 w-full text-sm text-red-800 disabled:opacity-40"
      >
        Eliminar resultado
      </button>
    </Modal>
  );
}
