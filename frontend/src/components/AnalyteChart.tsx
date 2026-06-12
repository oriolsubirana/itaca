import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Line,
  LineChart,
  ReferenceArea,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { getAnalyteSeries, getAnalytesWithData } from "../api/labs";

/**
 * Per-analyte evolution with the reference range shaded — only confirmed
 * reports feed this chart.
 */
export function AnalyteChart() {
  const analytes = useQuery({
    queryKey: ["analytes"],
    queryFn: getAnalytesWithData,
  });
  const [selected, setSelected] = useState<string | null>(null);
  const code = selected ?? analytes.data?.[0]?.code ?? null;

  const series = useQuery({
    queryKey: ["analyte-series", code],
    queryFn: () => getAnalyteSeries(code!),
    enabled: code !== null,
  });

  if (!analytes.data?.length) return null;

  const data = series.data;
  const reference = data?.points.findLast((p) => p.refMin != null || p.refMax != null);

  return (
    <div>
      <div className="mb-4">
        <label className="relative block">
          <select
            value={code ?? ""}
            onChange={(e) => setSelected(e.target.value)}
            className="min-h-11 w-full appearance-none rounded-lg border border-line bg-paper py-2 pl-3 pr-10 text-sm text-ink"
          >
            {analytes.data.map((a) => (
              <option key={a.code} value={a.code}>
                {a.name}
              </option>
            ))}
          </select>
          <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-ink-soft">
            ⌄
          </span>
        </label>
      </div>

      {data && data.points.length > 0 && (
        <>
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={data.points} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
              <XAxis
                dataKey="date"
                tick={{ fontSize: 11, fill: "#6b6963" }}
                tickFormatter={(d: string) => d.slice(5)}
                stroke="#e8e6e1"
              />
              <YAxis tick={{ fontSize: 11, fill: "#6b6963" }} stroke="#e8e6e1" width={48} />
              <Tooltip
                formatter={(value) => [`${value} ${data.unit}`, data.name]}
                contentStyle={{ border: "1px solid #e8e6e1", borderRadius: 8, fontSize: 12 }}
              />
              {reference && reference.refMax != null && (
                <ReferenceArea
                  y1={reference.refMin ?? 0}
                  y2={reference.refMax}
                  fill="#1c1c1a"
                  fillOpacity={0.05}
                  strokeOpacity={0}
                />
              )}
              <Line
                type="monotone"
                dataKey="value"
                stroke="#1c1c1a"
                strokeWidth={1.5}
                dot={{ r: 3, fill: "#1c1c1a" }}
              />
            </LineChart>
          </ResponsiveContainer>
          <p className="mt-1 text-xs text-ink-soft">
            {data.name} ({data.unit}) — zona sombreada: rango de referencia
          </p>
        </>
      )}
    </div>
  );
}
