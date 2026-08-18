"use client";

import { useEffect, useMemo, useRef } from "react";
import * as echarts from "echarts";
import type { EChartsType } from "echarts";
import { CHART_THEME } from "@/lib/chartTheme";
import type { PatternTrendVo } from "@/types/pattern-trend";

interface Props {
  data: PatternTrendVo;
}

function formatIndex(v: number) {
  const two = v.toFixed(2);
  return two.endsWith("0") ? v.toFixed(1) : two;
}

function displayRatio(feature: string, ratio: string) {
  if (
    feature === "zone1Count" ||
    feature === "zone2Count" ||
    feature === "zone3Count"
  ) {
    return `${ratio}个`;
  }
  return ratio;
}

export default function PatternTrendCharts({ data }: Props) {
  const omissionRef = useRef<HTMLDivElement>(null);
  const trendRef = useRef<HTMLDivElement>(null);
  const omissionChart = useRef<EChartsType | null>(null);
  const trendChart = useRef<EChartsType | null>(null);

  const titlePrefix = useMemo(
    () => `${data.featureLabel}[${displayRatio(data.feature, data.ratio)}]`,
    [data.feature, data.featureLabel, data.ratio],
  );

  useEffect(() => {
    if (!omissionRef.current || !trendRef.current) return;

    if (!omissionChart.current) {
      omissionChart.current = echarts.init(omissionRef.current);
    }
    if (!trendChart.current) {
      trendChart.current = echarts.init(trendRef.current);
    }

    const { periods, omissions, indexValues, stats, hits } =
      data;
    const colors = omissions.map((_, i) =>
      i === omissions.length - 1 ? "#3fb950" : "#f5a623",
    );

    omissionChart.current.setOption(
      {
        backgroundColor: "transparent",
        tooltip: {
          trigger: "axis",
          backgroundColor: CHART_THEME.tooltipBg,
          borderColor: CHART_THEME.tooltipBorder,
          textStyle: { color: CHART_THEME.text },
          formatter: (
            params: Array<{ dataIndex: number; axisValue: string }>,
          ) => {
            const idx = params[0].dataIndex;
            return (
              `期号: ${params[0].axisValue}<br/>` +
              `遗漏: ${omissions[idx]}<br/>` +
              (hits[idx] ? "命中该比例" : "未命中")
            );
          },
        },
        grid: { left: 50, right: 60, top: 20, bottom: 40 },
        xAxis: {
          type: "category",
          data: periods,
          axisLabel: {
            color: CHART_THEME.muted,
            interval: 9,
            fontSize: 10,
            rotate: 45,
          },
          axisLine: { lineStyle: { color: CHART_THEME.axis } },
        },
        yAxis: {
          type: "value",
          name: "遗漏",
          nameTextStyle: { color: CHART_THEME.muted },
          axisLabel: { color: CHART_THEME.muted },
          splitLine: { lineStyle: { color: CHART_THEME.split } },
          axisLine: { lineStyle: { color: CHART_THEME.axis } },
        },
        series: [
          {
            type: "line",
            data: omissions,
            symbol: "circle",
            symbolSize: 8,
            itemStyle: {
              color: (p: { dataIndex: number }) => colors[p.dataIndex],
            },
            lineStyle: { color: "#f5a62355", width: 1 },
            markLine: {
              symbol: "none",
              silent: true,
              data: [
                {
                  yAxis: stats.maxOmission,
                  lineStyle: { color: "#f85149", type: "dashed" },
                  label: {
                    formatter: `最大 ${stats.maxOmission}`,
                    color: "#f85149",
                    position: "insideEndTop",
                    fontSize: 10,
                  },
                },
                {
                  yAxis: stats.avgOmission,
                  lineStyle: { color: "#58a6ff", type: "dashed" },
                  label: {
                    formatter: `平均 ${stats.avgOmission.toFixed(2)}`,
                    color: "#58a6ff",
                    position: "insideEndTop",
                    fontSize: 10,
                  },
                },
              ],
            },
            label: {
              show: true,
              position: "top",
              fontSize: 9,
              color: CHART_THEME.muted,
              formatter: (p: { value: number }) =>
                p.value === 0 ? "" : String(p.value),
            },
          },
        ],
      },
      true,
    );

    const lastIdx = indexValues.length - 1;
    const indexMin = Math.min(...indexValues);
    const indexMax = Math.max(...indexValues);
    const span = Math.max(0.4, indexMax - indexMin);
    const pad = span * 0.14;
    const hitColor = "#f85149";
    const missColor = "#3fb950";
    const gold = "#d29922";
    const areaTop =
      stats.index >= 0 ? "rgba(248, 81, 73, 0.14)" : "rgba(63, 185, 80, 0.14)";

    trendChart.current.setOption(
      {
        backgroundColor: "transparent",
        tooltip: {
          trigger: "axis",
          backgroundColor: CHART_THEME.tooltipBg,
          borderColor: CHART_THEME.tooltipBorder,
          textStyle: { color: CHART_THEME.text, fontSize: 12 },
          padding: [10, 12],
          axisPointer: {
            type: "line",
            snap: true,
            lineStyle: { color: "#d2992266", width: 1 },
          },
          formatter: (
            params: Array<{ dataIndex: number; axisValue: string }>,
          ) => {
            const idx = params[0].dataIndex;
            const hit = hits[idx];
            const tone = hit ? hitColor : missColor;
            const tag = hit ? "命中 +(1−p)" : "未命中 −p";
            return (
              `<div style="font-weight:600;margin-bottom:4px">第 ${params[0].axisValue} 期</div>` +
              `指数　<span style="color:${gold};font-weight:600">${indexValues[idx].toFixed(2)}</span><br/>` +
              `遗漏　${omissions[idx]}<br/>` +
              `<span style="color:${tone}">●</span> ${tag}`
            );
          },
        },
        grid: { left: 48, right: 56, top: 28, bottom: 36 },
        dataZoom: [
          {
            type: "inside",
            xAxisIndex: 0,
            filterMode: "none",
            zoomOnMouseWheel: true,
            moveOnMouseMove: true,
          },
        ],
        xAxis: {
          type: "category",
          data: periods,
          boundaryGap: false,
          axisLabel: {
            color: CHART_THEME.muted,
            interval: Math.max(0, Math.ceil(periods.length / 6) - 1),
            fontSize: 10,
            rotate: 0,
          },
          axisTick: { show: false },
          axisLine: { lineStyle: { color: CHART_THEME.axis } },
          splitLine: { show: false },
        },
        yAxis: {
          type: "value",
          name: "指数",
          min: indexMin - pad,
          max: indexMax + pad,
          splitNumber: 4,
          minInterval: span >= 2 ? 1 : 0.5,
          nameGap: 8,
          nameTextStyle: { color: CHART_THEME.muted, fontSize: 11, padding: [0, 0, 0, 8] },
          axisLabel: {
            color: CHART_THEME.muted,
            fontSize: 10,
            formatter: (v: number) =>
              span >= 2 ? String(Math.round(v)) : v.toFixed(1),
          },
          splitLine: {
            lineStyle: { color: CHART_THEME.split, type: "dashed", width: 1 },
          },
          axisLine: { show: false },
          axisTick: { show: false },
        },
        series: [
          {
            name: "指数",
            type: "line",
            data: indexValues,
            showSymbol: false,
            z: 1,
            lineStyle: { width: 0, opacity: 0 },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: areaTop },
                { offset: 1, color: "rgba(22, 27, 34, 0)" },
              ]),
            },
            markLine: {
              symbol: "none",
              silent: true,
              animation: false,
              data: [
                {
                  yAxis: stats.index,
                  lineStyle: {
                    color: gold,
                    type: "dashed",
                    width: 1.2,
                    opacity: 0.9,
                  },
                  label: {
                    formatter: formatIndex(stats.index),
                    color: gold,
                    position: "end",
                    fontSize: 11,
                    fontWeight: 600,
                    backgroundColor: CHART_THEME.tooltipBg,
                    padding: [2, 5],
                    borderRadius: 3,
                  },
                },
              ],
            },
          },
          {
            name: "走势",
            type: "custom",
            clip: true,
            z: 3,
            renderItem: (
              params: { dataIndex: number },
              api: {
                value: (dim: number) => number | string;
                coord: (data: Array<number | string>) => number[];
              },
            ) => {
              const idx = params.dataIndex;
              const curr = Number(api.value(1));
              const hit = hits[idx];
              const stroke = hit ? hitColor : missColor;
              if (idx === 0) {
                const p = api.coord([periods[0], curr]);
                return {
                  type: "circle",
                  shape: { cx: p[0], cy: p[1], r: 2.2 },
                  style: { fill: stroke },
                };
              }
              const prev = indexValues[idx - 1];
              const p0 = api.coord([periods[idx - 1], prev]);
              const p1 = api.coord([periods[idx], curr]);
              return {
                type: "line",
                shape: { x1: p0[0], y1: p0[1], x2: p1[0], y2: p1[1] },
                style: {
                  stroke,
                  lineWidth: hit ? 2.6 : 1.7,
                  lineCap: "round",
                  lineJoin: "round",
                  shadowBlur: hit ? 6 : 0,
                  shadowColor: hit ? "rgba(248, 81, 73, 0.45)" : "transparent",
                },
              };
            },
            data: periods.map((period, i) => [period, indexValues[i]]),
            encode: { x: 0, y: 1 },
          },
          {
            name: "当前",
            type: "scatter",
            data:
              lastIdx >= 0
                ? [[periods[lastIdx], indexValues[lastIdx]]]
                : [],
            symbol: "circle",
            symbolSize: 9,
            z: 5,
            itemStyle: {
              color: gold,
              borderColor: CHART_THEME.pageBg,
              borderWidth: 2,
              shadowBlur: 10,
              shadowColor: "rgba(210, 153, 34, 0.55)",
            },
            tooltip: { show: false },
          },
        ],
      },
      true,
    );

    const onResize = () => {
      omissionChart.current?.resize();
      trendChart.current?.resize();
    };
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, [data]);

  useEffect(() => {
    return () => {
      omissionChart.current?.dispose();
      trendChart.current?.dispose();
      omissionChart.current = null;
      trendChart.current = null;
    };
  }, []);

  const { stats } = data;
  const indexSign = stats.index > 0.05 ? "偏热" : stats.index < -0.05 ? "偏冷" : "持平";

  return (
    <>
      <div className="stats-panel">
        <div className="stat-card">
          <div className="label">最大遗漏</div>
          <div className="value red">{stats.maxOmission}</div>
        </div>
        <div className="stat-card">
          <div className="label">平均遗漏</div>
          <div className="value blue">{stats.avgOmission.toFixed(2)}</div>
        </div>
        <div className="stat-card">
          <div className="label">当前遗漏</div>
          <div className="value green">{stats.currentOmission}</div>
        </div>
        <div className="stat-card">
          <div className="label">指数</div>
          <div className="value yellow">{formatIndex(stats.index)}</div>
        </div>
        <div className="stat-card">
          <div className="label">出现 / 理论</div>
          <div className="value">
            {stats.hitCount}/{stats.theoreticalHits.toFixed(1)}
          </div>
        </div>
        <div className="stat-card">
          <div className="label">冷热</div>
          <div
            className={`value ${
              indexSign === "偏热"
                ? "red"
                : indexSign === "偏冷"
                  ? "blue"
                  : "green"
            }`}
          >
            {indexSign}
          </div>
        </div>
      </div>

      <div className="charts-grid">
      <div className="chart-container">
        <div className="chart-title">
          <span>{titlePrefix}遗漏走势</span>
          <span className="tag">近 {stats.totalPeriods} 期</span>
        </div>
        <div ref={omissionRef} className="chart-box" />
      </div>

      <div className="chart-container">
        <div className="chart-title">
          <span>{titlePrefix}趋势</span>
          <span className="muted">
            期号: {data.latestPeriod}　开奖号: {data.latestWinning}　指数:{" "}
            {formatIndex(stats.index)}
          </span>
        </div>
        <div ref={trendRef} className="chart-box" />
        <div className="legend">
          <span>
            <span className="dot" style={{ background: "#f85149" }} />
            命中
          </span>
          <span>
            <span className="dot" style={{ background: "#3fb950" }} />
            未命中
          </span>
          <span>
            <span className="dot dashed" style={{ color: "#d29922" }} />
            当前指数
          </span>
        </div>
      </div>
      </div>
    </>
  );
}
