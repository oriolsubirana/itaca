import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  confirmLabReport,
  deleteLabReport,
  discardLabReport,
  getLabReportDetail,
  getLabReports,
  uploadLabReport,
  STATUS_LABELS,
} from "../api/labs";

/** Upload + review flow: nothing reaches the charts until confirmed. */
export function LabReports() {
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [openReport, setOpenReport] = useState<number | null>(null);

  const reports = useQuery({ queryKey: ["lab-reports"], queryFn: getLabReports });

  const upload = useMutation({
    mutationFn: uploadLabReport,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["lab-reports"] }),
  });

  return (
    <div>
      <input
        ref={fileInput}
        type="file"
        accept="application/pdf"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) upload.mutate(file);
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
              <span className="text-ink-soft">{r.date}</span>
              <span className="flex-1 truncate">{r.laboratory ?? "—"}</span>
              <span
                className={`text-xs uppercase tracking-wide ${
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
    <div className="mb-3 rounded-lg border border-line p-3">
      {results.length === 0 ? (
        <p className="py-2 text-sm text-ink-soft">Todavía sin resultados — la extracción está en curso.</p>
      ) : (
        <ul className="mb-3">
          {results.map((res) => (
            <li key={res.id} className="flex items-baseline gap-2 border-b border-line py-1.5 text-sm last:border-b-0">
              <span className="flex-1 truncate">
                {res.analyteName ?? res.rawName}
                {!res.analyteName && (
                  <span className="ml-1 text-xs text-amber-700">(sin normalizar)</span>
                )}
              </span>
              <span className="font-medium">
                {res.value} {res.unit ?? ""}
              </span>
              {res.refMax != null && (
                <span className="text-xs text-ink-soft">
                  ref {res.refMin ?? 0}–{res.refMax}
                </span>
              )}
            </li>
          ))}
        </ul>
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
