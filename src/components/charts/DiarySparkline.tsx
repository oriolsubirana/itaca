"use client";

import { Line, LineChart, ResponsiveContainer, YAxis } from "recharts";

export default function DiarySparkline({ data }: { data: { fecha: string; dolor: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height={48}>
      <LineChart data={data} margin={{ top: 4, bottom: 4, left: 0, right: 0 }}>
        <YAxis hide domain={[0, 10]} />
        <Line
          type="monotone"
          dataKey="dolor"
          stroke="var(--color-accent)"
          strokeWidth={1.8}
          dot={false}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
