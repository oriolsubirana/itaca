import { useEffect, useRef } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getInbox, retryIngest, uploadIngest, type IngestedFile } from "../api/ingestion";

const STATUS: Record<string, { label: string; cls: string }> = {
  pending: { label: "Procesando…", cls: "text-ink-soft" },
  processed: { label: "Hecho", cls: "text-income" },
  error: { label: "Error", cls: "text-clinical" },
};

const DESTINATION: Record<string, string> = { health: "Salud", finance: "Finanzas" };

function whenLabel(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString("es-ES", { day: "numeric", month: "short" }) +
    ` · ${d.toLocaleTimeString("es-ES", { hour: "2-digit", minute: "2-digit" })}`;
}

export function Entradas() {
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);

  const inbox = useQuery({
    queryKey: ["ingest-inbox"],
    queryFn: getInbox,
    // Files are classified asynchronously; poll while anything is still pending.
    refetchInterval: (q) => (q.state.data?.some((f) => f.status === "pending") ? 2000 : false),
  });

  const refreshInbox = () => void queryClient.invalidateQueries({ queryKey: ["ingest-inbox"] });

  const upload = useMutation({ mutationFn: uploadIngest, onSuccess: refreshInbox });
  const retry = useMutation({ mutationFn: retryIngest, onSuccess: refreshInbox });

  // Refresh a destination's pages exactly when a file finishes processing (the import
  // lands asynchronously, well after the upload). The first load only records the
  // already-processed entries; only later transitions trigger a downstream refetch.
  const seenProcessed = useRef<Set<number> | null>(null);
  useEffect(() => {
    const data = inbox.data;
    if (!data) return;
    if (seenProcessed.current === null) {
      seenProcessed.current = new Set(data.filter((f) => f.status === "processed").map((f) => f.id));
      return;
    }
    let finance = false;
    let health = false;
    for (const f of data) {
      if (f.status === "processed" && !seenProcessed.current.has(f.id)) {
        seenProcessed.current.add(f.id);
        if (f.destination === "finance") finance = true;
        if (f.destination === "health") health = true;
      }
    }
    if (finance) {
      void queryClient.invalidateQueries({ queryKey: ["finance-overview"] });
      void queryClient.invalidateQueries({ queryKey: ["finance-month"] });
    }
    if (health) void queryClient.invalidateQueries({ queryKey: ["lab-reports"] });
  }, [inbox.data, queryClient]);

  const files = inbox.data ?? [];

  return (
    <div>
      <header className="flex items-end justify-between border-b border-line pb-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">Entradas</h1>
          <p className="mt-1 text-sm text-ink-soft">Sube cualquier PDF o CSV; Ítaca lo clasifica y lo archiva.</p>
        </div>
      </header>

      <button
        onClick={() => fileInput.current?.click()}
        disabled={upload.isPending}
        className="mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-md border border-ink/80 text-[15px] font-medium text-ink transition-colors hover:bg-ink hover:text-paper disabled:opacity-40"
      >
        {upload.isPending ? "Subiendo…" : "↑ Subir documentos"}
      </button>
      <input
        ref={fileInput}
        type="file"
        accept=".pdf,.csv,application/pdf,text/csv"
        multiple
        className="hidden"
        onChange={(e) => {
          const files = Array.from(e.target.files ?? []);
          if (files.length > 0) upload.mutate(files);
          e.target.value = "";
        }}
      />
      {upload.isError && <p className="mt-2 text-[13px] text-clinical">No se pudo subir el archivo.</p>}

      <div className="mt-8">
        {files.length === 0 ? (
          <p className="py-12 text-center text-sm text-ink-soft">
            Aún no has subido nada. Comparte aquí una analítica o un extracto.
          </p>
        ) : (
          <ul className="border-t border-line">
            {files.map((f) => (
              <Row
                key={f.id}
                file={f}
                onRetry={() => retry.mutate(f.id)}
                retrying={retry.isPending && retry.variables === f.id}
              />
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function Row({ file, onRetry, retrying }: { file: IngestedFile; onRetry: () => void; retrying: boolean }) {
  const status = STATUS[file.status] ?? STATUS.pending;
  const destination = file.destination ? DESTINATION[file.destination] ?? file.destination : null;
  return (
    <li className="flex items-start justify-between gap-3 border-b border-line py-3.5">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="shrink-0 rounded bg-line/70 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-ink-soft">
            {file.type}
          </span>
          <span className="truncate text-[15px] text-ink">{file.name}</span>
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[12px] text-ink-soft">
          <span className={status.cls}>{status.label}</span>
          {destination && <span>· → {destination}</span>}
          <span>· {whenLabel(file.createdAt)}</span>
        </div>
        {file.status === "processed" && file.detail && (
          <p className="mt-1 text-[12px] text-ink-soft">{file.detail}</p>
        )}
        {file.status === "error" && file.errorMessage && (
          <p className="mt-1 text-[12px] text-clinical">{file.errorMessage}</p>
        )}
      </div>
      {file.status === "error" && (
        <button
          onClick={onRetry}
          disabled={retrying}
          className="mt-0.5 shrink-0 rounded-full border border-line px-3 py-1.5 text-[13px] text-ink hover:bg-line/40 disabled:opacity-40"
        >
          Reintentar
        </button>
      )}
    </li>
  );
}
