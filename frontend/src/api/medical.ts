import { api, apiBlob } from "./client";

export interface MedicalDocument {
  id: number;
  date: string | null;
  type: string | null;
  provider: string | null;
  center: string | null;
  filename: string | null;
  status: "pending_review" | "confirmed" | "discarded";
  extracting: boolean;
  diagnosisCount: number;
  medicationCount: number;
}

export interface MedicalDiagnosis {
  id: number;
  code: string | null;
  label: string;
  date: string | null;
}

export interface MedicalMedication {
  id: number;
  name: string;
  dose: string | null;
  schedule: string | null;
  duration: string | null;
  reason: string | null;
}

export interface MedicalDocumentDetail {
  document: MedicalDocument;
  diagnoses: MedicalDiagnosis[];
  medications: MedicalMedication[];
  fullText: string | null;
}

export function uploadMedicalDocuments(files: File[]): Promise<MedicalDocument[]> {
  const body = new FormData();
  files.forEach((file) => body.append("files", file));
  return api<MedicalDocument[]>("/health/medical-documents", { method: "POST", body });
}

export const getMedicalDocuments = () => api<MedicalDocument[]>("/health/medical-documents");

export const getMedicalDocumentDetail = (id: number) =>
  api<MedicalDocumentDetail>(`/health/medical-documents/${id}`);

export const confirmMedicalDocument = (id: number) =>
  api<MedicalDocument>(`/health/medical-documents/${id}/confirm`, { method: "POST" });

export const discardMedicalDocument = (id: number) =>
  api<MedicalDocument>(`/health/medical-documents/${id}/discard`, { method: "POST" });

export const reextractMedicalDocument = (id: number) =>
  api<MedicalDocument>(`/health/medical-documents/${id}/extract`, { method: "POST" });

export const deleteMedicalDocument = (id: number) =>
  api<void>(`/health/medical-documents/${id}`, { method: "DELETE" });

/** Opens the stored PDF (blank tab opened within the click so iOS doesn't block it). */
export async function openMedicalDocumentFile(id: number): Promise<void> {
  const tab = window.open("", "_blank");
  try {
    const blob = await apiBlob(`/health/medical-documents/${id}/file`);
    const url = URL.createObjectURL(blob);
    if (tab) tab.location.href = url;
    else window.location.href = url;
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) {
    tab?.close();
    throw e;
  }
}

export const MEDICAL_STATUS_LABELS: Record<MedicalDocument["status"], string> = {
  pending_review: "pendiente de revisión",
  confirmed: "confirmado",
  discarded: "descartado",
};
