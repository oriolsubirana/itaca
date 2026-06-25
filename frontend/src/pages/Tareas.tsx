import { useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Modal } from "../components/Modal";
import { createTask, deleteTask, getTasks, updateTask, type Task } from "../api/tasks";
import { shortDate, today } from "../lib/format";

/** "hoy" / "mañana" / "vencía 23 jun" (overdue) / "1 jul". */
function dueLabel(task: Task): string {
  const due = task.dueDate;
  if (!due) return "";
  const t = today();
  if (due === t) return "hoy";
  const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  if (due === tomorrow) return "mañana";
  return task.overdue ? `vencía ${shortDate(due)}` : shortDate(due);
}

export function Tareas() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const tasks = useQuery({ queryKey: ["tasks"], queryFn: () => getTasks(true) });
  const [adding, setAdding] = useState(false);

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ["tasks"] });
  const toggle = useMutation({
    mutationFn: (t: Task) => updateTask(t.id, { done: !t.done }),
    onSuccess: invalidate,
  });
  const remove = useMutation({ mutationFn: (id: number) => deleteTask(id), onSuccess: invalidate });

  const open = tasks.data?.open ?? [];
  const done = tasks.data?.done ?? [];
  const overdue = tasks.data?.overdueCount ?? 0;

  return (
    <div>
      <header className="-mt-1 flex items-center justify-between gap-2 border-b border-line pb-3">
        <div className="flex items-center gap-2">
          <button
            onClick={() => void navigate({ to: "/" })}
            aria-label="Volver"
            className="-ml-2 flex size-9 items-center justify-center rounded-full text-ink hover:bg-line/50"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="size-5">
              <path d="M15 6l-6 6 6 6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">Tareas</h1>
        </div>
        <button
          onClick={() => setAdding(true)}
          className="rounded-full bg-ink px-4 py-1.5 text-[13px] font-medium text-paper"
        >
          Añadir
        </button>
      </header>

      {overdue > 0 && (
        <p className="mt-4 text-sm text-clinical">
          {overdue} {overdue === 1 ? "tarea vencida" : "tareas vencidas"}
        </p>
      )}

      {open.length === 0 && done.length === 0 ? (
        <p className="pt-12 text-center text-sm text-ink-soft">Sin tareas pendientes. Añade una arriba.</p>
      ) : (
        <div className="mt-6 space-y-8">
          {open.length > 0 && (
            <section>
              <h2 className="mb-3 text-xs uppercase tracking-[0.13em] text-ink-soft">Pendientes</h2>
              <ul className="overflow-hidden rounded-2xl border border-line">
                {open.map((t, i) => (
                  <TaskRow
                    key={t.id}
                    task={t}
                    border={i > 0}
                    onToggle={() => toggle.mutate(t)}
                    onDelete={() => remove.mutate(t.id)}
                  />
                ))}
              </ul>
            </section>
          )}

          {done.length > 0 && (
            <section>
              <h2 className="mb-3 text-xs uppercase tracking-[0.13em] text-ink-soft">Hechas</h2>
              <ul className="overflow-hidden rounded-2xl border border-line">
                {done.map((t, i) => (
                  <TaskRow
                    key={t.id}
                    task={t}
                    border={i > 0}
                    onToggle={() => toggle.mutate(t)}
                    onDelete={() => remove.mutate(t.id)}
                  />
                ))}
              </ul>
            </section>
          )}
        </div>
      )}

      {adding && <AddTask onClose={() => setAdding(false)} onAdded={invalidate} />}
    </div>
  );
}

function TaskRow({
  task,
  border,
  onToggle,
  onDelete,
}: {
  task: Task;
  border: boolean;
  onToggle: () => void;
  onDelete: () => void;
}) {
  const due = dueLabel(task);
  return (
    <li className={`flex items-center gap-3 px-3.5 py-3 ${border ? "border-t border-line" : ""}`}>
      <button
        onClick={onToggle}
        aria-label={task.done ? "Marcar como pendiente" : "Marcar como hecha"}
        className={`flex size-6 shrink-0 items-center justify-center rounded-full border transition-colors ${
          task.done ? "border-ink bg-ink text-paper" : "border-line text-transparent hover:border-ink/50"
        }`}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" className="size-3.5">
          <path d="M5 12.5l4 4 10-10" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      <div className="min-w-0 flex-1">
        <div className={`text-[15px] leading-snug ${task.done ? "text-ink-soft line-through" : "text-ink"}`}>
          {task.title}
        </div>
        {!task.done && due && (
          <div className={`mt-0.5 text-[12px] ${task.overdue ? "text-clinical" : "text-ink-soft"}`}>{due}</div>
        )}
      </div>
      <button
        onClick={onDelete}
        aria-label="Eliminar"
        className="flex size-9 shrink-0 items-center justify-center rounded-full text-ink-soft hover:text-clinical"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" className="size-[18px]">
          <path d="M6 7h12M9 7V5h6v2m-7 0 .7 12h6.6L17 7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
    </li>
  );
}

function AddTask({ onClose, onAdded }: { onClose: () => void; onAdded: () => void }) {
  const [title, setTitle] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [notes, setNotes] = useState("");

  const save = useMutation({
    mutationFn: () =>
      createTask({ title: title.trim(), dueDate: dueDate || null, notes: notes.trim() || null }),
    onSuccess: () => {
      onAdded();
      onClose();
    },
  });

  const canSave = title.trim().length > 0 && !save.isPending;

  return (
    <Modal title="Nueva tarea" onClose={onClose}>
      <div className="space-y-4">
        <input
          autoFocus
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="¿Qué tienes que hacer?"
          className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-[15px] text-ink outline-none placeholder:text-ink-soft focus:border-ink/40"
        />
        <label className="block">
          <span className="text-[11px] uppercase tracking-[0.08em] text-ink-soft">Fecha límite (opcional)</span>
          <input
            type="date"
            value={dueDate}
            onChange={(e) => setDueDate(e.target.value)}
            className="mt-1.5 w-full rounded-xl border border-line bg-paper px-4 py-3 text-[15px] text-ink outline-none focus:border-ink/40"
          />
        </label>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Notas (opcional)"
          rows={2}
          className="w-full resize-none rounded-xl border border-line bg-paper px-4 py-3 text-[15px] text-ink outline-none placeholder:text-ink-soft focus:border-ink/40"
        />
        {save.isError && <p className="text-sm text-clinical">No se pudo guardar. Inténtalo de nuevo.</p>}
        <button
          onClick={() => save.mutate()}
          disabled={!canSave}
          className="min-h-12 w-full rounded-full bg-ink text-sm font-medium text-paper disabled:opacity-40"
        >
          Añadir tarea
        </button>
      </div>
    </Modal>
  );
}
