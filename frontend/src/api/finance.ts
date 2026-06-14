import { api } from "./client";

export interface FinanceAccount {
  id: number;
  name: string;
  type: string; // checking | savings | investment
  currency: string; // CHF | EUR
  balance: number;
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
