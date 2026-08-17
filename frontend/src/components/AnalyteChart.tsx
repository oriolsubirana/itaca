import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Listbox,
  ListboxButton,
  ListboxOption,
  ListboxOptions,
} from "@headlessui/react";
import {
  Line,
  LineChart,
  ReferenceArea,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { getMeasurements, getMeasurementSeries } from "../api/labs";
import type { MeasurementRef } from "../api/labs";

const MONTHS_ES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];

/** Groups measurements into their panels, preserving the backend's category-then-name order. */
function groupByCategory(measurements: MeasurementRef[]): [string, MeasurementRef[]][] {
  const groups = new Map<string, MeasurementRef[]>();
  for (const m of measurements) {
    (groups.get(m.category) ?? groups.set(m.category, []).get(m.category)!).push(m);
  }
  return [...groups.entries()];
}

/** "2024-02-01" -> "feb '24": month + year so points from different years don't collide. */
function formatTick(date: string): string {
  const [year, month] = date.split("-");
  return `${MONTHS_ES[Number(month) - 1]} '${year.slice(2)}`;
}

/**
 * Per-analyte evolution with the reference range shaded — only confirmed
 * reports feed this chart.
 */
export function AnalyteChart() {
  const measurements = useQuery({
    queryKey: ["analytes"],
    queryFn: getMeasurements,
  });
  const [selected, setSelected] = useState<string | null>(null);
  // Fall back to the first measurement when nothing is selected or the selection
  // no longer exists (e.g. its only report was deleted).
  const selectionValid = selected != null && (measurements.data?.some((m) => m.key === selected) ?? false);
  const key = (selectionValid ? selected : measurements.data?.[0]?.key) ?? null;

  const series = useQuery({
    queryKey: ["analyte-series", key],
    queryFn: () => getMeasurementSeries(key!),
    enabled: key !== null,
  });

  if (!measurements.data?.length) return null;

  const data = series.data;
  const reference = data?.points.findLast((p) => p.refMax != null);
  const selectedName = measurements.data.find((m) => m.key === key)?.name ?? "—";

  return (
    <div className="mb-8">
      <Listbox value={key ?? ""} onChange={setSelected}>
        <ListboxButton className="group flex min-h-11 w-full items-center justify-between gap-2 rounded-lg border border-line bg-paper px-3 text-left text-sm text-ink outline-none transition-colors data-[focus]:border-ink-soft data-[open]:border-ink-soft">
          <span className="truncate">{selectedName}</span>
          <svg
            aria-hidden="true"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.25"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="size-4 shrink-0 text-ink-soft transition-transform group-data-[open]:rotate-180"
          >
            <path d="M4 6l4 4 4-4" />
          </svg>
        </ListboxButton>
        <ListboxOptions
          anchor="bottom"
          transition
          className="z-50 max-h-72 w-[var(--button-width)] overflow-auto rounded-lg border border-line bg-paper p-1 shadow-lg [--anchor-gap:4px] outline-none data-[closed]:opacity-0 data-[closed]:transition data-[enter]:duration-100 data-[enter]:ease-out data-[leave]:duration-75 data-[leave]:ease-in"
        >
          {groupByCategory(measurements.data).map(([category, items]) => (
            <div key={category}>
              <p className="px-3 pt-3 pb-1 text-[11px] uppercase tracking-wide text-ink-soft first:pt-1">
                {category}
              </p>
              {items.map((m) => (
                <ListboxOption
                  key={m.key}
                  value={m.key}
                  className="group flex cursor-pointer items-center justify-between gap-2 rounded-md px-3 py-2.5 text-sm text-ink-soft transition-colors data-[focus]:bg-line data-[selected]:text-ink"
                >
                  <span className="truncate">{m.name}</span>
                  <span className="text-ink opacity-0 transition-opacity group-data-[selected]:opacity-100">
                    ✓
                  </span>
                </ListboxOption>
              ))}
            </div>
          ))}
        </ListboxOptions>
      </Listbox>

      {data && data.points.length > 0 && (
        <>
          <div className="mt-5 flex items-end justify-between gap-3">
            <div>
              <div className="text-3xl font-semibold leading-none tracking-tight tabular-nums text-ink">
                {data.points[data.points.length - 1].value}
                <span className="ml-1.5 text-base font-normal text-ink-soft">{data.unit}</span>
              </div>
              <div className="mt-2 text-xs text-ink-soft">
                Último · {data.points[data.points.length - 1].date.slice(0, 7)}
                {data.points.length > 1 && ` · anterior ${data.points[data.points.length - 2].value}`}
              </div>
            </div>
            {reference?.refMax != null && (
              <div className="text-right text-xs leading-snug text-ink-soft">
                <div className="uppercase tracking-wide">Rango ref.</div>
                <div className="tabular-nums text-ink/70">
                  {reference.refMin ?? 0}–{reference.refMax} {data.unit}
                </div>
              </div>
            )}
          </div>
          <ResponsiveContainer
            width="100%"
            height={200}
            className="mt-3 [&_.recharts-wrapper]:outline-none [&_svg]:outline-none"
          >
            <LineChart data={data.points} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
              <XAxis
                dataKey="date"
                tick={{ fontSize: 11, fill: "var(--color-ink-soft)" }}
                tickFormatter={formatTick}
                stroke="var(--color-line)"
              />
              <YAxis tick={{ fontSize: 11, fill: "var(--color-ink-soft)" }} stroke="var(--color-line)" width={48} />
              <Tooltip
                formatter={(value) => [`${value} ${data.unit}`, data.name]}
                contentStyle={{
                  background: "var(--color-paper)",
                  border: "1px solid var(--color-line)",
                  borderRadius: 8,
                  fontSize: 12,
                  color: "var(--color-ink)",
                }}
              />
              {reference && reference.refMax != null && (
                <ReferenceArea
                  y1={reference.refMin ?? 0}
                  y2={reference.refMax}
                  fill="var(--color-ink)"
                  fillOpacity={0.05}
                  strokeOpacity={0}
                />
              )}
              <Line
                type="monotone"
                dataKey="value"
                stroke="var(--color-ink)"
                strokeWidth={1.5}
                dot={{ r: 3, fill: "var(--color-ink)" }}
              />
            </LineChart>
          </ResponsiveContainer>
          <p className="mt-1 mb-2 text-xs text-ink-soft">
            {data.name} — zona sombreada: rango de referencia
          </p>
        </>
      )}
    </div>
  );
}
