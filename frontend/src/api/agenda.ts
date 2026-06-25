import { api } from "./client";

export interface AgendaEvent {
  summary: string;
  start: string; // RFC3339 dateTime, or YYYY-MM-DD if all-day
  allDay: boolean;
  location: string | null;
}

export interface InboxThread {
  threadId: string;
  subject: string;
  from: string;
  receivedIso: string;
  ageDays: number;
}

export const getCalendar = (days = 7) => api<AgendaEvent[]>(`/calendar?days=${days}`);

export const getInbox = (days = 21) => api<InboxThread[]>(`/inbox?days=${days}`);
