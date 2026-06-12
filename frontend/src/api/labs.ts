import { api } from "./client";

export interface LabReport {
  id: number;
  date: string;
  laboratory: string | null;
  status: "pending_review" | "confirmed" | "discarded";
  resultCount: number;
}

export interface LabResult {
  id: number;
  rawName: string;
  analyteCode: string | null;
  analyteName: string | null;
  value: number;
  unit: string | null;
  refMin: number | null;
  refMax: number | null;
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

export const getAnalytesWithData = () => api<AnalyteRef[]>("/health/analytes");

export const getAnalyteSeries = (code: string) =>
  api<AnalyteSeries>(`/health/analytes/${code}/series`);

export const STATUS_LABELS: Record<LabReport["status"], string> = {
  pending_review: "pendiente de revisión",
  confirmed: "confirmado",
  discarded: "descartado",
};
