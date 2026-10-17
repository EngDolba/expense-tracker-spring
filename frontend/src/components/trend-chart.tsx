"use client";

import {
  Bar,
  CartesianGrid,
  ComposedChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

// Recharts writes these straight into SVG attributes, so the palette tokens are
// spelled out as sRGB here: --lime and --violet from globals.css.
const EXPENSE = "#aff03c";
const INCOME = "#ac7dff";

export function TrendChart({
  data,
  emptyLabel = "No trend data yet",
}: {
  data: Array<{ month: string; expenses: number; income: number }>;
  emptyLabel?: string;
}) {
  if (!data.length) {
    return <div className="empty-state h-[280px]">{emptyLabel}</div>;
  }

  return (
    <div className="h-[280px] w-full">
      <ResponsiveContainer>
        <ComposedChart data={data} margin={{ top: 10, right: 8, left: -15, bottom: 0 }}>
          <CartesianGrid stroke="rgba(233, 255, 240, .08)" strokeDasharray="3 4" vertical={false} />
          <XAxis
            dataKey="month"
            tickLine={false}
            axisLine={false}
            fontSize={11}
            tick={{ fill: "#808c86" }}
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            fontSize={11}
            tick={{ fill: "#808c86" }}
          />
          <Tooltip
            cursor={{ fill: "rgba(233, 255, 240, .05)" }}
            contentStyle={{
              background: "#1b2320",
              border: "1px solid rgba(233, 255, 240, .17)",
              borderRadius: 13,
              boxShadow: "0 22px 60px rgba(0, 0, 0, .55)",
            }}
            labelStyle={{ color: "#94a29b", fontSize: 11 }}
            itemStyle={{ fontSize: 12 }}
          />
          <Bar dataKey="expenses" fill={EXPENSE} radius={[5, 5, 0, 0]} maxBarSize={22} />
          <Bar dataKey="income" fill={INCOME} radius={[5, 5, 0, 0]} maxBarSize={22} />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}
