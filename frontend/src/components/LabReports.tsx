import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  confirmLabReport,
  deleteLabReport,
  discardLabReport,
  getLabReportDetail,
  getLabReports,
  uploadLabReports,
  STATUS_LABELS,
} from "../api/labs";

/** Upload + review flow: nothing reaches the charts until confirmed. */
export function LabReports() {
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [openReport, setOpenReport] = useState<number | null>(null);

  const reports = useQuery({ queryKey: ["lab-reports"], queryFn: getLabReports });

  const upload = useMutation({
    mutationFn: uploadLabReports,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["lab-reports"] }),
  });

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
      <button
        onClick={() => fileInput.current?.click()}
        disabled={upload.isPending}
        className="mb-4 min-h-11 rounded-full border border-line px-5 text-sm text-ink disabled:opacity-40"
      >
        {upload.isPending ? "Subiendo…" : "+ Subir analítica"}
      </button>

      <ul>
        {(reports.data ?? []).map((r) => (
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
                {r.resultCount === 0 && r.status === "pending_review"
                  ? "procesando…"
                  : STATUS_LABELS[r.status]}
              </span>
            </button>
            {openReport === r.id && <ReportReview reportId={r.id} onDone={() => setOpenReport(null)} />}
          </li>
        ))}
      </ul>
    </div>
  );
}

function ReportReview({ reportId, onDone }: { reportId: number; onDone: () => void }) {
  const queryClient = useQueryClient();
  const detail = useQuery({
    queryKey: ["lab-report", reportId],
    queryFn: () => getLabReportDetail(reportId),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["lab-reports"] });
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

  if (!detail.data) return null;
  const { report, results } = detail.data;

  return (
    <div className="mb-3 rounded-lg border border-line p-4">
      <div className="mb-3 flex items-baseline justify-between gap-3 border-b border-line pb-3">
        <span className="min-w-0 truncate text-sm font-medium">
          {report.filename ?? report.laboratory ?? "Informe"}
        </span>
        <span className="shrink-0 text-xs text-ink-soft">
          {report.laboratory && `${report.laboratory} · `}
          {report.date}
        </span>
      </div>
      {results.length === 0 ? (
        <p className="py-2 text-sm text-ink-soft">Todavía sin resultados — la extracción está en curso.</p>
      ) : (
        <>
          <ul className="mb-3 max-h-80 overflow-y-auto overscroll-contain">
            {results.map((res) => (
              <li
                key={res.id}
                className="grid grid-cols-[1fr_auto] items-baseline gap-x-3 border-b border-line py-2 last:border-b-0"
              >
                <span className="text-sm leading-snug">
                  {res.analyteName ?? res.rawName}
                  {!res.analyteName && (
                    <span className="ml-1.5 align-middle text-[11px] uppercase tracking-wide text-amber-700">
                      sin normalizar
                    </span>
                  )}
                </span>
                <span className="text-right text-sm font-medium tabular-nums">
                  {res.value} {res.unit ?? ""}
                </span>
                {res.refMax != null && (
                  <span className="col-start-2 text-right text-xs tabular-nums text-ink-soft">
                    ref {res.refMin ?? 0}–{res.refMax}
                  </span>
                )}
              </li>
            ))}
          </ul>
          <p className="mb-3 text-xs text-ink-soft">
            {results.length} resultados · {results.filter((r) => r.analyteName).length} normalizados
          </p>
        </>
      )}
      {report.status === "pending_review" && results.length > 0 && (
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
    </div>
  );
}
