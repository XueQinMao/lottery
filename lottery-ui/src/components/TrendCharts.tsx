"use client";

import { useEffect, useMemo, useRef } from "react";
import * as echarts from "echarts";
import type { EChartsType } from "echarts";
import type { TrendAnalysisVo } from "@/types/trend";

interface Props {
  data: TrendAnalysisVo;
}

function padBall(n: number) {
  return String(n).padStart(2, "0");
}

export default function TrendCharts({ data }: Props) {
  const omissionRef = useRef<HTMLDivElement>(null);
  const trendRef = useRef<HTMLDivElement>(null);
  const omissionChart = useRef<EChartsType | null>(null);
  const trendChart = useRef<EChartsType | null>(null);

  const titlePrefix = useMemo(() => {
    const typeLabel = data.ballType === "red" ? "红球" : "蓝球";
    return `${typeLabel}[${padBall(data.ball)}]`;
  }, [data.ball, data.ballType]);

  useEffect(() => {
    if (!omissionRef.current || !trendRef.current) return;

    if (!omissionChart.current) {
      omissionChart.current = echarts.init(omissionRef.current);
    }
    if (!trendChart.current) {
      trendChart.current = echarts.init(trendRef.current);
    }

    const { periods, omissions, indexValues, ma5, ma10, ma20, stats } = data;
    const colors = omissions.map((_, i) =>
      i === omissions.length - 1 ? "#3fb950" : "#f5a623",
    );

    omissionChart.current.setOption(
      {
        backgroundColor: "transparent",
        tooltip: {
          trigger: "axis",
          backgroundColor: "#161b22",
          borderColor: "#30363d",
          textStyle: { color: "#e6edf3" },
        },
        grid: { left: 50, right: 60, top: 20, bottom: 40 },
        xAxis: {
          type: "category",
          data: periods,
          axisLabel: {
            color: "#8b949e",
            interval: 9,
            fontSize: 10,
            rotate: 45,
          },
          axisLine: { lineStyle: { color: "#30363d" } },
        },
        yAxis: {
          type: "value",
          name: "遗漏",
          nameTextStyle: { color: "#8b949e" },
          axisLabel: { color: "#8b949e" },
          splitLine: { lineStyle: { color: "#21262d" } },
          axisLine: { lineStyle: { color: "#30363d" } },
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
              color: "#8b949e",
              formatter: (p: { value: number }) =>
                p.value === 0 ? "" : String(p.value),
            },
          },
        ],
      },
      true,
    );

    trendChart.current.setOption(
      {
        backgroundColor: "transparent",
        tooltip: {
          trigger: "axis",
          backgroundColor: "#161b22",
          borderColor: "#30363d",
          textStyle: { color: "#e6edf3" },
          formatter: (params: Array<{ dataIndex: number; axisValue: string }>) => {
            const idx = params[0].dataIndex;
            return (
              `期号: ${params[0].axisValue}<br/>` +
              `遗漏: ${omissions[idx]}<br/>` +
              `指数: ${indexValues[idx].toFixed(2)}`
            );
          },
        },
        grid: { left: 50, right: 60, top: 20, bottom: 40 },
        xAxis: {
          type: "category",
          data: periods,
          axisLabel: {
            color: "#8b949e",
            interval: 9,
            fontSize: 10,
            rotate: 45,
          },
          axisLine: { lineStyle: { color: "#30363d" } },
        },
        yAxis: {
          type: "value",
          name: "指数",
          nameTextStyle: { color: "#8b949e" },
          min: -1,
          axisLabel: { color: "#8b949e" },
          splitLine: { lineStyle: { color: "#21262d" } },
          axisLine: { lineStyle: { color: "#30363d" } },
        },
        series: [
          {
            name: "指数",
            type: "bar",
            data: indexValues,
            itemStyle: { color: "#f8514944" },
            barWidth: "60%",
          },
          {
            name: "MA5",
            type: "line",
            data: ma5,
            smooth: true,
            symbol: "none",
            lineStyle: { color: "#ff8c00", width: 2 },
          },
          {
            name: "MA10",
            type: "line",
            data: ma10,
            smooth: true,
            symbol: "none",
            lineStyle: { color: "#9966cc", width: 2 },
          },
          {
            name: "MA20",
            type: "line",
            data: ma20,
            smooth: true,
            symbol: "none",
            lineStyle: { color: "#4488ff", width: 2 },
          },
          {
            name: "指数均值",
            type: "line",
            data: new Array(indexValues.length).fill(1),
            symbol: "none",
            lineStyle: { color: "#d29922", type: "dashed", width: 1.5 },
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

  const arrangement =
    data.arrangement === 1
      ? { text: "多头排列 >", className: "tag long" }
      : data.arrangement === -1
        ? { text: "空头排列 <", className: "tag short" }
        : { text: "交叉排列", className: "tag" };

  const { stats } = data;

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
          <div className="label">指数均值</div>
          <div className="value yellow">{stats.indexMean.toFixed(2)}</div>
        </div>
        <div className="stat-card">
          <div className="label">出现次数</div>
          <div className="value">
            {stats.hitCount}/{stats.totalPeriods}
          </div>
        </div>
      </div>

      <div className="chart-container">
        <div className="chart-title">
          <span>{titlePrefix}遗漏走势</span>
          <span className={arrangement.className}>{arrangement.text}</span>
        </div>
        <div ref={omissionRef} className="chart-box" />
      </div>

      <div className="chart-container">
        <div className="chart-title">
          <span>{titlePrefix}趋势</span>
        </div>
        <div ref={trendRef} className="chart-box" />
        <div className="legend">
          <span>
            <span className="dot" style={{ background: "#ff8c00" }} />
            5期均线
          </span>
          <span>
            <span className="dot" style={{ background: "#9966cc" }} />
            10期均线
          </span>
          <span>
            <span className="dot" style={{ background: "#4488ff" }} />
            20期均线
          </span>
          <span>
            <span className="dot" style={{ background: "#d29922" }} />
            指数均值线
          </span>
        </div>
      </div>
    </>
  );
}
