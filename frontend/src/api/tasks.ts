import { api } from "./client";

export interface Task {
  id: number;
  title: string;
  notes: string | null;
  dueDate: string | null; // YYYY-MM-DD
  done: boolean;
  doneAt: string | null;
  source: string; // manual | chat | email
  createdAt: string;
  overdue: boolean;
}

export interface TasksView {
  open: Task[];
  done: Task[];
  openCount: number;
  overdueCount: number;
}

export interface CreateTaskBody {
  title: string;
  notes?: string | null;
  dueDate?: string | null;
}

/** PATCH: omit a field to leave it; send dueDate "" to clear the deadline; done toggles completion. */
export interface UpdateTaskBody {
  title?: string;
  notes?: string | null;
  dueDate?: string | null;
  done?: boolean;
}

export const getTasks = (includeDone = true) => api<TasksView>(`/tasks?includeDone=${includeDone}`);

export const createTask = (body: CreateTaskBody) =>
  api<Task>("/tasks", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

export const updateTask = (id: number, body: UpdateTaskBody) =>
  api<Task>(`/tasks/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

export const deleteTask = (id: number) => api<void>(`/tasks/${id}`, { method: "DELETE" });
