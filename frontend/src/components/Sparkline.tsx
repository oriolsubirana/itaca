/** Tiny trend line; the last point turns clinical red when out of the reference range. */
export function Sparkline({
  values,
  refMin,
  refMax,
  width = 104,
  height = 46,
}: {
  values: number[];
  refMin?: number | null;
  refMax?: number | null;
  width?: number;
  height?: number;
}) {
  if (values.length < 2) return null;
  let mn = Math.min(...values);
  let mx = Math.max(...values);
  const pad = (mx - mn) * 0.18 || 1;
  mn -= pad;
  mx += pad;
  const x = (i: number) => 2 + i * ((width - 4) / (values.length - 1));
  const y = (v: number) => 3 + (1 - (v - mn) / (mx - mn)) * (height - 6);
  const pts = values.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ");
  const last = values[values.length - 1];
  const out = (refMin != null && last < refMin) || (refMax != null && last > refMax);

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} className="shrink-0 overflow-visible">
      <polyline
        points={pts}
        fill="none"
        stroke="var(--color-ink)"
        strokeWidth="1.4"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <circle
        cx={x(values.length - 1)}
        cy={y(last)}
        r="2.6"
        fill={out ? "var(--color-clinical)" : "var(--color-ink)"}
        stroke="var(--color-paper)"
        strokeWidth="1.2"
      />
    </svg>
  );
}
