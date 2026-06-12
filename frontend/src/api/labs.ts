import { api, apiBlob } from "./client";
import type { ReportCategory } from "./categories";

export interface LabReport {
  id: number;
  date: string;
  laboratory: string | null;
  filename: string | null;
  status: "pending_review" | "confirmed" | "discarded";
  category: ReportCategory | null;
  extracting: boolean;
  resultCount: number;
}

export interface LabResult {
  id: number;
  rawName: string;
  analyteCode: string | null;
  analyteName: string | null;
  date: string | null;
  value: number | null;
  textValue: string | null;
  unit: string | null;
  refMin: number | null;
  refMax: number | null;
  reviewed: boolean;
}

/** Partial update; omitted fields stay as they are, "" clears date/textValue/unit. */
export interface LabResultUpdate {
  date?: string;
  value?: number;
  textValue?: string;
  unit?: string;
  reviewed?: boolean;
}

export interface LabReportDetail {
  report: LabReport;
  results: LabResult[];
}

export interface AnalyteRef {
  id: number;
  code: string;
  name: string;
  canonicalUnit: string;
  category: string | null;
}

export interface AnalyteSeriesPoint {
  date: string;
  value: number;
  refMin: number | null;
  refMax: number | null;
}

export interface AnalyteSeries {
  code: string;
  name: string;
  unit: string;
  points: AnalyteSeriesPoint[];
}

export function uploadLabReports(files: File[]): Promise<LabReport[]> {
  const body = new FormData();
  files.forEach((file) => body.append("files", file));
  return api<LabReport[]>("/health/lab-reports", { method: "POST", body });
}

export const getLabReports = () => api<LabReport[]>("/health/lab-reports");

export const getLabReportDetail = (id: number) =>
  api<LabReportDetail>(`/health/lab-reports/${id}`);

export const confirmLabReport = (id: number) =>
  api<LabReport>(`/health/lab-reports/${id}/confirm`, { method: "POST" });

export const discardLabReport = (id: number) =>
  api<LabReport>(`/health/lab-reports/${id}/discard`, { method: "POST" });

export const deleteLabReport = (id: number) =>
  api<void>(`/health/lab-reports/${id}`, { method: "DELETE" });

export const setLabReportCategory = (id: number, category: ReportCategory | null) =>
  api<void>(`/health/lab-reports/${id}/category`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ category }),
  });

/**
 * Opens the stored PDF. The blank tab is opened synchronously (within the click
 * gesture) so iOS Safari doesn't block it, then pointed at the fetched blob.
 */
export async function openLabReportFile(id: number): Promise<void> {
  const tab = window.open("", "_blank");
  try {
    const blob = await apiBlob(`/health/lab-reports/${id}/file`);
    const url = URL.createObjectURL(blob);
    if (tab) tab.location.href = url;
    else window.location.href = url;
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) {
    tab?.close();
    throw e;
  }
}

export const reextractLabReport = (id: number) =>
  api<LabReport>(`/health/lab-reports/${id}/extract`, { method: "POST" });

export const renormalizeLabReport = (id: number) =>
  api<{ changed: number; total: number }>(`/health/lab-reports/${id}/renormalize`, { method: "POST" });

export const renormalizeAllLabReports = () =>
  api<{ changed: number; total: number }>("/health/lab-reports/renormalize", { method: "POST" });

export const updateLabResult = (id: number, update: LabResultUpdate) =>
  api<LabResult>(`/health/lab-results/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(update),
  });

export const deleteLabResult = (id: number) =>
  api<void>(`/health/lab-results/${id}`, { method: "DELETE" });

export const getAnalytesWithData = () => api<AnalyteRef[]>("/health/analytes");

export const getAnalyteSeries = (code: string) =>
  api<AnalyteSeries>(`/health/analytes/${code}/series`);

export const STATUS_LABELS: Record<LabReport["status"], string> = {
  pending_review: "pendiente de revisión",
  confirmed: "confirmado",
  discarded: "descartado",
};
