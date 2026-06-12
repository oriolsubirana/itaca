import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  confirmMedicalDocument,
  deleteMedicalDocument,
  discardMedicalDocument,
  getMedicalDocumentDetail,
  getMedicalDocuments,
  openMedicalDocumentFile,
  reextractMedicalDocument,
  uploadMedicalDocuments,
  MEDICAL_STATUS_LABELS,
} from "../api/medical";

/** Clinical documents: upload narrative reports, review the extracted facts, confirm. */
export function MedicalDocuments() {
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState<number | null>(null);

  const documents = useQuery({
    queryKey: ["medical-documents"],
    queryFn: getMedicalDocuments,
    refetchInterval: (query) => (query.state.data?.some((d) => d.extracting) ? 3000 : false),
  });

  const upload = useMutation({
    mutationFn: uploadMedicalDocuments,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["medical-documents"] }),
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
        {upload.isPending ? "Subiendo…" : "+ Subir documento"}
      </button>

      <ul>
        {(documents.data ?? []).map((d) => (
          <li key={d.id} className="border-b border-line last:border-b-0">
            <button
              onClick={() => setOpen(open === d.id ? null : d.id)}
              className="flex min-h-12 w-full items-center gap-3 py-2 text-left text-sm"
            >
              <span className="shrink-0 text-ink-soft">{d.date ?? "—"}</span>
              <span className="min-w-0 flex-1 truncate">
                {d.type ?? d.filename ?? "Documento"}
              </span>
              <span
                className={`shrink-0 text-xs uppercase tracking-wide ${
                  d.status === "pending_review" ? "text-amber-700" : "text-ink-soft"
                }`}
              >
                {d.extracting ? "procesando…" : MEDICAL_STATUS_LABELS[d.status]}
              </span>
            </button>
            {open === d.id && <DocumentReview documentId={d.id} onDone={() => setOpen(null)} />}
          </li>
        ))}
      </ul>
    </div>
  );
}

function DocumentReview({ documentId, onDone }: { documentId: number; onDone: () => void }) {
  const queryClient = useQueryClient();
  const detail = useQuery({
    queryKey: ["medical-document", documentId],
    queryFn: () => getMedicalDocumentDetail(documentId),
    refetchInterval: (query) => (query.state.data?.document.extracting ? 3000 : false),
  });

  const refreshDetail = () => {
    void queryClient.invalidateQueries({ queryKey: ["medical-document", documentId] });
    void queryClient.invalidateQueries({ queryKey: ["medical-documents"] });
  };
  const close = () => {
    refreshDetail();
    onDone();
  };
  const confirm = useMutation({ mutationFn: () => confirmMedicalDocument(documentId), onSuccess: close });
  const discard = useMutation({ mutationFn: () => discardMedicalDocument(documentId), onSuccess: close });
  const remove = useMutation({ mutationFn: () => deleteMedicalDocument(documentId), onSuccess: close });
  const reextract = useMutation({ mutationFn: () => reextractMedicalDocument(documentId), onSuccess: refreshDetail });
  const viewFile = useMutation({ mutationFn: () => openMedicalDocumentFile(documentId) });

  if (!detail.data) return null;
  const { document, diagnoses, medications } = detail.data;
  const editable = document.status === "pending_review" && !document.extracting;

  return (
    <div className="mb-3 rounded-lg border border-line p-4">
      <div className="mb-3 flex items-baseline justify-between gap-3 border-b border-line pb-3">
        <span className="min-w-0 truncate text-sm font-medium">
          {document.type ?? document.filename ?? "Documento"}
        </span>
        <span className="shrink-0 text-xs text-ink-soft">
          {document.center && `${document.center} · `}
          {document.date ?? ""}
        </span>
      </div>
      <button
        onClick={() => viewFile.mutate()}
        disabled={viewFile.isPending}
        className="mb-3 min-h-11 w-full rounded-lg border border-line text-sm text-ink-soft disabled:opacity-40"
      >
        {viewFile.isPending ? "Abriendo…" : "Ver documento (PDF)"}
      </button>

      {document.extracting ? (
        <p className="mb-3 flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
          <span className="inline-block size-3 shrink-0 animate-spin rounded-full border-2 border-amber-700 border-t-transparent" />
          Procesando el documento… los datos aparecerán aquí solos.
        </p>
      ) : (
        <>
          {diagnoses.length > 0 && (
            <div className="mb-3">
              <p className="mb-1 text-xs uppercase tracking-wide text-ink-soft">Diagnósticos</p>
              <ul>
                {diagnoses.map((dx) => (
                  <li key={dx.id} className="flex items-baseline justify-between gap-3 py-1 text-sm">
                    <span className="min-w-0">
                      {dx.label}
                      {dx.code && <span className="ml-1.5 text-xs text-ink-soft">{dx.code}</span>}
                    </span>
                    {dx.date && <span className="shrink-0 text-xs tabular-nums text-ink-soft">{dx.date}</span>}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {medications.length > 0 && (
            <div className="mb-3">
              <p className="mb-1 text-xs uppercase tracking-wide text-ink-soft">Medicación</p>
              <ul>
                {medications.map((m) => (
                  <li key={m.id} className="py-1 text-sm">
                    <span className="font-medium">{m.name}</span>
                    {[m.dose, m.schedule, m.duration].filter(Boolean).length > 0 && (
                      <span className="text-ink-soft">
                        {" "}
                        {[m.dose, m.schedule, m.duration].filter(Boolean).join(" · ")}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {diagnoses.length === 0 && medications.length === 0 && (
            <p className="mb-3 text-sm text-ink-soft">
              Sin datos extraídos. Puedes reprocesar el documento.
            </p>
          )}
          <div className="mb-3 flex items-center justify-between gap-3">
            <p className="text-xs text-ink-soft">
              {diagnoses.length} diagnósticos · {medications.length} medicaciones
            </p>
            {editable && (
              <button
                onClick={() => reextract.mutate()}
                disabled={reextract.isPending}
                className="shrink-0 text-xs text-ink-soft underline underline-offset-2 disabled:opacity-40"
              >
                {reextract.isPending ? "reprocesando…" : "↻ reprocesar"}
              </button>
            )}
          </div>
        </>
      )}

      {editable && (
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
          if (window.confirm("¿Eliminar este documento y todos sus datos?")) remove.mutate();
        }}
        disabled={remove.isPending}
        className="mt-2 min-h-11 w-full text-sm text-red-800 disabled:opacity-40"
      >
        Eliminar documento
      </button>
    </div>
  );
}
