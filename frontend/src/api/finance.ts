import { api } from "./client";

export interface FinanceAccount {
  id: number;
  name: string;
  type: string; // checking | savings | investment
  currency: string; // CHF | EUR
  balance: number;
  importable: boolean; // has a statement/report parser
}

export interface FinanceOverview {
  monthOrder: string[]; // ["2026-04", "2026-05", ...]
  accounts: FinanceAccount[];
}

export interface CategorySpend {
  category: string; // canonical code (e.g. "groceries")
  amount: number; // positive magnitude
}

export interface FinanceTx {
  date: string;
  description: string;
  category: string;
  amount: number; // signed: negative = gasto, positive = ingreso
  account: string;
}

export interface MonthView {
  ingresos: number;
  gastos: number; // negative
  categorias: CategorySpend[];
  tx: FinanceTx[];
}

export const getFinanceOverview = () => api<FinanceOverview>("/finance/overview");

export const getFinanceMonth = (month: string, currency: string) =>
  api<MonthView>(`/finance/month?month=${month}&currency=${currency}`);

export interface ImportResult {
  imported: boolean;
  account: string;
  date: string | null;
  value: number | null;
  message: string | null;
}

/** Sets an account's current balance (CSV statements carry no balance). */
export const setAccountBalance = (accountId: number, balance: number) =>
  api<FinanceOverview>("/finance/balance", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ accountId, balance }),
  });

/** Multipart upload of a statement/report (finpension PDF for now). */
export async function importFinance(accountId: number, file: File): Promise<ImportResult> {
  const form = new FormData();
  form.append("accountId", String(accountId));
  form.append("file", file);
  const headers = new Headers();
  const token = import.meta.env.VITE_API_TOKEN as string | undefined;
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const res = await fetch("/api/finance/import", { method: "POST", body: form, headers });
  if (!res.ok) throw new Error(`Import ${res.status}`);
  return (await res.json()) as ImportResult;
}
