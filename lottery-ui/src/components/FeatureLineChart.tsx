"use client";

import { useEffect, useRef } from "react";
import { CHART_THEME } from "@/lib/chartTheme";
import * as echarts from "echarts";
import type { EChartsType } from "echarts";

export interface FeatureLineChartProps {
  title: string;
  yName: string;
  periods: string[];
  values: number[];
  avg: number;
  lineColor?: string;
  labels?: string[];
  yMin?: number;
  yMax?: number;
  yInterval?: number;
  yFormatter?: (value: number) => string;
}

export default function FeatureLineChart({
  title,
  yName,
  periods,
  values,
  avg,
  lineColor = "#3B82F6",
  labels,
  yMin,
  yMax,
  yInterval,
  yFormatter,
}: FeatureLineChartProps) {
  const boxRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<EChartsType | null>(null);

  useEffect(() => {
    if (!boxRef.current) return;
    if (!chartRef.current) {
      chartRef.current = echarts.init(boxRef.current);
    }

    const avgText = Number.isInteger(avg) ? String(avg) : avg.toFixed(2);

    chartRef.current.setOption(
      {
        backgroundColor: "transparent",
        tooltip: {
          trigger: "axis",
          backgroundColor: CHART_THEME.tooltipBg,
          borderColor: CHART_THEME.tooltipBorder,
          textStyle: { color: CHART_THEME.text },
          formatter: (
            params: Array<{ dataIndex: number; axisValue: string; value: number }>,
          ) => {
            const idx = params[0].dataIndex;
            const ratio = labels?.[idx];
            return (
              `期号: ${params[0].axisValue}<br/>` +
              `${yName}: ${params[0].value}` +
              (ratio ? `<br/>比例: ${ratio}` : "")
            );
          },
        },
        grid: { left: 50, right: 70, top: 28, bottom: 48 },
        xAxis: {
          type: "category",
          data: periods,
          name: "期号",
          nameTextStyle: { color: CHART_THEME.muted },
          axisLabel: {
            color: CHART_THEME.muted,
            interval: Math.max(0, Math.floor(periods.length / 10) - 1),
            fontSize: 10,
            rotate: 45,
          },
          axisLine: { lineStyle: { color: CHART_THEME.axis } },
        },
        yAxis: {
          type: "value",
          name: yName,
          min: yMin,
          max: yMax,
          interval: yInterval,
          nameTextStyle: { color: CHART_THEME.muted },
          axisLabel: {
            color: CHART_THEME.muted,
            ...(yFormatter ? { formatter: yFormatter } : {}),
          },
          splitLine: { lineStyle: { color: CHART_THEME.split } },
          axisLine: { lineStyle: { color: CHART_THEME.axis } },
        },
        series: [
          {
            type: "line",
            data: values,
            symbol: "circle",
            symbolSize: 7,
            itemStyle: { color: lineColor },
            lineStyle: { color: lineColor, width: 2 },
            markLine: {
              symbol: "none",
              silent: true,
              data: [
                {
                  yAxis: avg,
                  lineStyle: { color: "#d29922", type: "dashed", width: 1.5 },
                  label: {
                    formatter: `平均 ${avgText}`,
                    color: "#d29922",
                    position: "insideEndTop",
                    fontSize: 11,
                  },
                },
              ],
            },
          },
        ],
      },
      true,
    );

    const onResize = () => chartRef.current?.resize();
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, [
    avg,
    labels,
    lineColor,
    periods,
    values,
    yFormatter,
    yInterval,
    yMax,
    yMin,
    yName,
  ]);

  useEffect(() => {
    return () => {
      chartRef.current?.dispose();
      chartRef.current = null;
    };
  }, []);

  return (
    <div className="chart-container">
      <div className="chart-title">
        <span>{title}</span>
        <span className="tag">平均 {Number.isInteger(avg) ? avg : avg.toFixed(2)}</span>
      </div>
      <div ref={boxRef} className="chart-box" />
    </div>
  );
}
