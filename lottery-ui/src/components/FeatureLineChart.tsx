"use client";

import { useEffect, useRef } from "react";
import { CHART_THEME } from "@/lib/chartTheme";
import * as echarts from "echarts";
import type { EChartsType } from "echarts";

export interface FeatureLineChartProps {
  title: string;
  yName: string;
  periods: string[];
  values: Array<number | null>;
  avg: number;
  avgLabel?: string;
  avgDisplay?: string;
  lineColor?: string;
  labels?: string[];
  tooltipLabel?: string;
  zoom?: boolean;
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
  avgLabel = "平均",
  avgDisplay,
  lineColor = CHART_THEME.ballBlue,
  labels,
  tooltipLabel = "比例",
  zoom = false,
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

    const avgText = avgDisplay ?? (Number.isInteger(avg) ? String(avg) : avg.toFixed(2));

    chartRef.current.setOption(
      {
        backgroundColor: "transparent",
        tooltip: {
          trigger: "axis",
          confine: true,
          backgroundColor: CHART_THEME.tooltipBg,
          borderColor: CHART_THEME.tooltipBorder,
          textStyle: { color: CHART_THEME.text },
          extraCssText: "box-shadow: 0 6px 24px #26334512; border-radius: 8px; padding: 10px 14px;",
          formatter: (
            params: Array<{
              dataIndex: number;
              axisValue: string;
              value: number | null;
            }>,
          ) => {
            const idx = params[0].dataIndex;
            const ratio = labels?.[idx];
            const raw = params[0].value;
            const display = raw == null ? "—" : yFormatter ? yFormatter(raw) : String(raw);
            return (
              `期号: ${params[0].axisValue}<br/>` +
              `${yName}: ${display}` +
              (ratio ? `<br/>${tooltipLabel}: ${ratio}` : "")
            );
          },
        },
        grid: { left: 12, right: 18, top: 32, bottom: zoom ? 76 : 52, containLabel: true },
        dataZoom: zoom ? [
          {
            type: "inside",
            filterMode: "none",
            zoomOnMouseWheel: false,
            moveOnMouseWheel: false,
            preventDefaultMouseMove: false,
          },
          {
            type: "slider", height: 14, bottom: 8, borderColor: "transparent",
            backgroundColor: CHART_THEME.split, fillerColor: "#48679b16",
            dataBackground: { lineStyle: { color: CHART_THEME.axis }, areaStyle: { color: CHART_THEME.split } },
            selectedDataBackground: { lineStyle: { color: lineColor }, areaStyle: { color: "#48679b12" } },
            handleStyle: { color: CHART_THEME.tooltipBg, borderColor: CHART_THEME.axis },
            moveHandleStyle: { color: CHART_THEME.axis },
            textStyle: { color: CHART_THEME.muted },
          },
        ] : [],
        xAxis: {
          type: "category",
          data: periods,
          name: "期号",
          nameLocation: "middle",
          nameGap: 28,
          nameTextStyle: { color: CHART_THEME.muted },
          axisLabel: {
            color: CHART_THEME.muted,
            interval: "auto",
            hideOverlap: true,
            fontSize: 11,
            margin: 12,
          },
          axisLine: { lineStyle: { color: CHART_THEME.axis } },
          axisTick: { show: false },
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
            fontSize: 11,
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
            showSymbol: false,
            symbolSize: 6,
            itemStyle: { color: lineColor },
            lineStyle: { color: lineColor, width: 1.8 },
            markLine: {
              symbol: "none",
              silent: true,
              data: [
                {
                  yAxis: avg,
                  lineStyle: { color: "#a4b1c2", type: "dashed", width: 1 },
                  label: {
                    formatter: `${avgLabel} ${avgText}`,
                    color: CHART_THEME.muted,
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
    const observer = new ResizeObserver(onResize);
    observer.observe(boxRef.current);
    return () => observer.disconnect();
  }, [
    avg,
    avgLabel,
    avgDisplay,
    labels,
    tooltipLabel,
    zoom,
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
        <span className="chart-series-title"><i className="chart-series-dot" style={{ background: lineColor }} aria-hidden="true" />{title}</span>
        <span className="tag">{avgLabel} {avgDisplay ?? (Number.isInteger(avg) ? avg : avg.toFixed(2))}</span>
      </div>
      <div ref={boxRef} className="chart-box" role="img" aria-label={`${title}，按开奖期号排列的${yName}折线图`} />
    </div>
  );
}
