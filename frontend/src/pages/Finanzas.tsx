import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Modal } from "../components/Modal";
import { MES } from "../lib/format";
import {
  getFinanceMonth,
  getFinanceOverview,
  importFinance,
  setAccountBalance,
  type FinanceAccount,
  type FinanceTx,
  type MonthView,
} from "../api/finance";

const CAT_ES: Record<string, string> = {
  groceries: "Alimentación", restaurants: "Restaurantes", transport: "Transporte", fuel: "Combustible",
  travel: "Viajes", housing: "Vivienda", utilities: "Suministros", health: "Salud", subscriptions: "Suscripciones",
  shopping: "Compras", leisure: "Ocio", income: "Ingresos", transfers: "Transferencias", work: "Trabajo",
  savings: "Ahorro", investment: "Inversión", fees: "Comisiones", cash: "Efectivo", other: "Otros",
};
const TYPE_ES: Record<string, string> = { checking: "Corriente", savings: "Ahorro", investment: "Inversión" };
const catLabel = (c: string) => CAT_ES[c] ?? c;
const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

// 1234.5 -> "1.234,50"
function money(n: number): string {
  return Math.abs(n).toLocaleString("de-DE", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function signed(n: number): string {
  return (n > 0 ? "+" : n < 0 ? "−" : "") + money(n);
}
function txDate(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  return `${d.getDate()} ${MES[d.getMonth()]}`;
}
function monthLabel(key: string): string {
  const [y, m] = key.split("-");
  const d = new Date(Number(y), Number(m) - 1, 1);
  return `${cap(d.toLocaleDateString("es-ES", { month: "long" }))} ${y}`;
}

export function Finanzas() {
  const overview = useQuery({ queryKey: ["finance-overview"], queryFn: getFinanceOverview });
  const [monthIdx, setMonthIdx] = useState<number | null>(null);
  const [currency, setCurrency] = useState("CHF");
  const [showImport, setShowImport] = useState(false);
  const [mov, setMov] = useState<FinanceTx | null>(null);
  const [editAccount, setEditAccount] = useState<FinanceAccount | null>(null);

  const months = overview.data?.monthOrder ?? [];
  const accounts = overview.data?.accounts ?? [];
  const lastIdx = months.length - 1;
  const idx = monthIdx === null ? lastIdx : Math.min(Math.max(monthIdx, 0), lastIdx);
  const month = months[idx];

  const monthQuery = useQuery({
    queryKey: ["finance-month", month, currency],
    queryFn: () => getFinanceMonth(month!, currency),
    enabled: !!month,
  });
  const data = monthQuery.data;

  return (
    <div>
      <header className="border-b border-line pb-3">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Finanzas</h1>
      </header>

      {months.length === 0 ? (
        <EmptyState onImport={() => setShowImport(true)} />
      ) : (
        <>
          <div className="mt-3 flex items-center justify-between">
            <div className="flex items-center gap-1">
              <Arrow dir={-1} disabled={idx === 0} onClick={() => setMonthIdx(idx - 1)} />
              <span className="min-w-[120px] text-center text-[15px] tabular-nums text-ink">{monthLabel(month)}</span>
              <Arrow dir={1} disabled={idx === lastIdx} onClick={() => setMonthIdx(idx + 1)} />
            </div>
            <div className="flex rounded-full bg-line/60 p-0.5">
              {["CHF", "EUR"].map((c) => (
                <button
                  key={c}
                  onClick={() => setCurrency(c)}
                  className={`h-8 rounded-full px-3.5 text-[13px] font-medium tabular-nums transition-colors ${
                    currency === c ? "bg-paper text-ink shadow-[0_1px_2px_rgba(28,28,26,0.08)]" : "text-ink-soft"
                  }`}
                >
                  {c}
                </button>
              ))}
            </div>
          </div>

          <div className="space-y-9 pt-7">
            {data && <Resumen data={data} currency={currency} />}
            <div className="border-t border-line" />
            {data && <Categorias data={data} currency={currency} />}
            <div className="border-t border-line" />
            <Cuentas accounts={accounts} currency={currency} onEdit={setEditAccount} />
            <div className="border-t border-line" />
            {data && <Movimientos data={data} onOpen={setMov} />}
            <button
              onClick={() => setShowImport(true)}
              className="flex h-12 w-full items-center justify-center gap-2 rounded-md border border-ink/80 text-[15px] font-medium text-ink transition-colors hover:bg-ink hover:text-paper"
            >
              ↑ Importar extracto (CSV)
            </button>
          </div>
        </>
      )}

      {showImport && <ImportModal accounts={accounts} onClose={() => setShowImport(false)} />}
      {mov && <MovDetailModal tx={mov} currency={currency} onClose={() => setMov(null)} />}
      {editAccount && <BalanceModal account={editAccount} onClose={() => setEditAccount(null)} />}
    </div>
  );
}

function Arrow({ dir, disabled, onClick }: { dir: -1 | 1; disabled: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      aria-label={dir < 0 ? "Mes anterior" : "Mes siguiente"}
      className={`flex size-9 items-center justify-center rounded-full ${disabled ? "text-line" : "text-ink hover:bg-line/50"}`}
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d={dir < 0 ? "M15 6l-6 6 6 6" : "M9 6l6 6-6 6"} />
      </svg>
    </button>
  );
}

function SecLabel({ children }: { children: string }) {
  return <h2 className="mb-3 text-xs uppercase tracking-[0.13em] text-ink-soft">{children}</h2>;
}

function Resumen({ data, currency }: { data: MonthView; currency: string }) {
  const net = data.ingresos + data.gastos;
  return (
    <section>
      <SecLabel>Resumen del mes</SecLabel>
      <div className="flex items-baseline gap-2 whitespace-nowrap">
        <span className={`text-[40px] font-semibold leading-none tracking-tight tabular-nums ${net >= 0 ? "text-income" : "text-ink"}`}>
          {signed(net)}
        </span>
        <span className="shrink-0 text-base text-ink-soft">{currency}</span>
      </div>
      <div className="mt-2.5 text-[13px] text-ink-soft">Neto del mes</div>
      <div className="mt-5 flex gap-8">
        <div>
          <div className="text-lg tabular-nums text-ink">{signed(data.gastos)}</div>
          <div className="mt-1 text-[11px] uppercase tracking-[0.08em] text-ink-soft">Gastos</div>
        </div>
        <div>
          <div className="text-lg tabular-nums text-income">{signed(data.ingresos)}</div>
          <div className="mt-1 text-[11px] uppercase tracking-[0.08em] text-ink-soft">Ingresos</div>
        </div>
      </div>
    </section>
  );
}

function Categorias({ data, currency }: { data: MonthView; currency: string }) {
  const [all, setAll] = useState(false);
  const cats = data.categorias;
  const max = cats[0]?.amount || 1;
  const list = all ? cats : cats.slice(0, 6);
  if (cats.length === 0) return null;
  return (
    <section>
      <SecLabel>Gasto por categoría</SecLabel>
      <div className="space-y-3.5">
        {list.map((c) => (
          <div key={c.category}>
            <div className="mb-1.5 flex items-baseline justify-between gap-3">
              <span className="text-sm text-ink">{catLabel(c.category)}</span>
              <span className="shrink-0 whitespace-nowrap text-sm tabular-nums text-ink">
                {money(c.amount)} <span className="text-xs text-ink-soft">{currency}</span>
              </span>
            </div>
            <div className="h-[3px] overflow-hidden rounded-full bg-line">
              <div className="h-full rounded-full bg-ink" style={{ width: `${(c.amount / max) * 100}%` }} />
            </div>
          </div>
        ))}
      </div>
      {cats.length > 6 && (
        <button onClick={() => setAll((v) => !v)} className="mt-4 text-[13px] text-ink-soft hover:text-ink">
          {all ? "Mostrar menos" : `Mostrar todas (${cats.length})`}
        </button>
      )}
    </section>
  );
}

function Cuentas({
  accounts,
  currency,
  onEdit,
}: {
  accounts: FinanceAccount[];
  currency: string;
  onEdit: (a: FinanceAccount) => void;
}) {
  const accts = accounts.filter((a) => a.currency === currency);
  if (accts.length === 0) return null;
  return (
    <section>
      <SecLabel>{`Cuentas · ${currency}`}</SecLabel>
      <div>
        {accts.map((a) => (
          <button
            key={a.id}
            onClick={() => onEdit(a)}
            className="flex w-full items-center justify-between border-b border-line py-3.5 text-left last:border-b-0"
          >
            <div>
              <div className="text-[15px] text-ink">{a.name}</div>
              <div className="mt-0.5 text-xs text-ink-soft">{TYPE_ES[a.type] ?? a.type}</div>
            </div>
            <div className="text-lg tabular-nums text-ink">
              {money(a.balance)} <span className="text-xs text-ink-soft">{currency}</span>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}

function BalanceModal({ account, onClose }: { account: FinanceAccount; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [value, setValue] = useState(String(account.balance));
  const mutation = useMutation({
    mutationFn: () => setAccountBalance(account.id, Number(value)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["finance-overview"] });
      onClose();
    },
  });
  return (
    <Modal title={`Saldo · ${account.name}`} onClose={onClose}>
      <label className="mb-2 block text-[13px] uppercase tracking-[0.08em] text-ink-soft">
        Saldo actual ({account.currency})
      </label>
      <input
        type="number"
        step="0.01"
        inputMode="decimal"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        className="mb-5 w-full rounded-md border border-line px-4 py-3 text-[17px] tabular-nums text-ink focus:border-ink/40 focus:outline-none"
      />
      <button
        disabled={value.trim() === "" || Number.isNaN(Number(value)) || mutation.isPending}
        onClick={() => mutation.mutate()}
        className="h-12 w-full rounded-md bg-ink text-[15px] font-medium text-paper hover:bg-ink/90 disabled:opacity-30"
      >
        {mutation.isPending ? "Guardando…" : "Guardar saldo"}
      </button>
    </Modal>
  );
}

function Movimientos({ data, onOpen }: { data: MonthView; onOpen: (t: FinanceTx) => void }) {
  const [all, setAll] = useState(false);
  const tx = data.tx;
  const list = all ? tx : tx.slice(0, 6);
  if (tx.length === 0) return <p className="text-sm text-ink-soft">Sin movimientos este mes.</p>;
  return (
    <section>
      <SecLabel>Movimientos</SecLabel>
      <div>
        {list.map((t, i) => (
          <button
            key={i}
            onClick={() => onOpen(t)}
            className="flex min-h-11 w-full items-center justify-between gap-3 border-b border-line py-3 text-left last:border-b-0"
          >
            <div className="min-w-0 flex-1">
              <div className="truncate text-[15px] text-ink">{t.description}</div>
              <div className="mt-1 flex items-center gap-2 text-[11px] text-ink-soft">
                <span className="uppercase tracking-[0.05em]">{catLabel(t.category)}</span>
                <span>· {txDate(t.date)}</span>
              </div>
            </div>
            <span className={`shrink-0 text-[15px] tabular-nums ${t.amount > 0 ? "text-income" : "text-ink"}`}>
              {signed(t.amount)}
            </span>
          </button>
        ))}
      </div>
      {tx.length > 6 && (
        <button onClick={() => setAll((v) => !v)} className="mt-3 text-[13px] text-ink-soft hover:text-ink">
          {all ? "Mostrar menos" : `Mostrar todos (${tx.length})`}
        </button>
      )}
    </section>
  );
}

function MovDetailModal({ tx, currency, onClose }: { tx: FinanceTx; currency: string; onClose: () => void }) {
  const rows: [string, string][] = [
    ["Fecha", txDate(tx.date)],
    ["Categoría", catLabel(tx.category)],
    ["Cuenta", tx.account],
    ["Divisa", currency],
  ];
  return (
    <Modal title="Movimiento" onClose={onClose}>
      <div className={`text-[34px] font-semibold tracking-tight tabular-nums ${tx.amount > 0 ? "text-income" : "text-ink"}`}>
        {signed(tx.amount)} <span className="text-base font-normal text-ink-soft">{currency}</span>
      </div>
      <div className="mt-1 text-[15px] text-ink">{tx.description}</div>
      <div className="mt-5">
        {rows.map(([k, v]) => (
          <div key={k} className="flex items-center justify-between border-b border-line py-3">
            <span className="text-[13px] text-ink-soft">{k}</span>
            <span className="text-[15px] text-ink">{v}</span>
          </div>
        ))}
      </div>
    </Modal>
  );
}

function ImportModal({ accounts, onClose }: { accounts: FinanceAccount[]; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [acc, setAcc] = useState(accounts[0]?.id ?? 0);
  const [file, setFile] = useState<File | null>(null);
  const mutation = useMutation({
    mutationFn: () => importFinance(acc, file!),
    onSuccess: (r) => {
      if (r.imported) {
        void queryClient.invalidateQueries({ queryKey: ["finance-overview"] });
        void queryClient.invalidateQueries({ queryKey: ["finance-month"] });
      }
    },
  });
  const result = mutation.data;
  return (
    <Modal title="Importar extracto" onClose={onClose}>
      <label className="mb-2 block text-[13px] uppercase tracking-[0.08em] text-ink-soft">Cuenta</label>
      <div className="mb-5 space-y-1.5">
        {accounts.map((a) => (
          <button
            key={a.id}
            onClick={() => setAcc(a.id)}
            className={`flex min-h-12 w-full items-center justify-between rounded-md border px-4 text-left ${
              acc === a.id ? "border-ink/70 bg-line/30" : "border-line hover:border-ink/30"
            }`}
          >
            <span className="text-[15px] text-ink">
              {a.name} <span className="text-[13px] text-ink-soft">· {a.currency}</span>
            </span>
            {acc === a.id && <span className="text-ink">✓</span>}
          </button>
        ))}
      </div>
      <label className="mb-2 block text-[13px] uppercase tracking-[0.08em] text-ink-soft">Archivo</label>
      <label className="mb-5 flex min-h-[88px] cursor-pointer flex-col items-center justify-center gap-1.5 rounded-md border border-dashed border-line hover:border-ink/30">
        <input type="file" accept=".csv,.pdf,text/csv,application/pdf" className="hidden" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <span className="text-[15px] text-ink">{file ? file.name : "Selecciona un archivo"}</span>
        <span className="text-xs text-ink-soft">{file ? "Listo" : "CSV del banco o PDF de finpension"}</span>
      </label>
      <button
        disabled={!file || mutation.isPending}
        onClick={() => mutation.mutate()}
        className="h-12 w-full rounded-md bg-ink text-[15px] font-medium text-paper hover:bg-ink/90 disabled:opacity-30"
      >
        {mutation.isPending ? "Importando…" : "Importar movimientos"}
      </button>
      {mutation.isError && (
        <p className="mt-3 text-[13px] text-clinical">No se pudo importar el archivo.</p>
      )}
      {result && (
        <p className="mt-3 text-[13px] leading-relaxed text-ink-soft">
          {result.imported && <span className="text-ink">✓ {result.account} · </span>}
          {result.message}
        </p>
      )}
    </Modal>
  );
}

function EmptyState({ onImport }: { onImport: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center px-8 pt-24 text-center">
      <div className="mb-5 flex size-14 items-center justify-center rounded-full border border-line text-ink-soft">
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
          <rect x="2.5" y="6" width="19" height="13" rx="2.5" />
          <path d="M2.5 10h19M16 14.5h2" strokeLinecap="round" />
        </svg>
      </div>
      <h2 className="text-lg font-medium text-ink">Aún no hay movimientos</h2>
      <p className="mt-2 max-w-[260px] text-sm leading-relaxed text-ink-soft">
        Importa un extracto bancario en CSV para empezar a ver tus gastos por categoría.
      </p>
      <button
        onClick={onImport}
        className="mt-6 flex h-12 items-center gap-2 rounded-md bg-ink px-6 text-[15px] font-medium text-paper hover:bg-ink/90"
      >
        ↑ Importar extracto (CSV)
      </button>
    </div>
  );
}
