"use client";

import { useMemo } from "react";
import FeatureLineChart from "@/components/FeatureLineChart";
import type { DrawTrend, DrawTrendGroup } from "@/types/draw-trend";

const COLORS = ["#537EAC", "#A57B36", "#C85163", "#328575", "#8066A8", "#B45891"];
const BAND_COLORS: Record<string, string> = { cold: COLORS[0], warm: COLORS[1], hot: COLORS[2] };
const occurrenceLabel = (value: number) => value === 1 ? "出现" : value === 0 ? "未出现" : "";

export default function DrawTrendCharts({ data, group, ball }: {
  data: DrawTrend; group: DrawTrendGroup; ball: "red" | "blue";
}) {
  const labels = useMemo(() => data.draws.map((draw) =>
    `${draw.reds.map((number) => String(number).padStart(2, "0")).join(" ")} + ${String(draw.blue).padStart(2, "0")}`), [data.draws]);
  const isTemperature = group.code.toLowerCase().includes("coldhot");
  const incomplete = isTemperature && group.series.some((series) => series.sampleCount < data.periods.length);

  return (
    <section className="draw-trend-content" aria-labelledby="draw-feature-title">
      <div className="draw-trend-heading">
        <div>
          <h2 id="draw-feature-title">{ball === "red" ? "红球" : "蓝球"} · {group.label}</h2>
        </div>
        <span className="draw-trend-badge">{group.series.length} 张独立折线图</span>
      </div>
      {incomplete && <div className="draw-trend-notice" role="status">部分期次缺少开奖前 30 期历史，冷温热分类留空；图中断点不计入均值。</div>}
      <div className="charts-grid draw-charts-grid">
        {group.series.map((series, index) => (
          <FeatureLineChart key={`${ball}-${group.code}-${series.code}`}
            title={`${series.label}${group.occurrence ? ` · 出现 ${series.total} 期` : ""}`}
            yName={group.occurrence ? "出现情况" : group.code === "sum" ? "和值" : group.code === "span" ? "跨度" : group.code === "sumDelta" ? "增长差值" : "个数"}
            periods={data.periods} values={series.values} avg={series.average}
            avgLabel={group.occurrence ? "出现率" : "均值"}
            avgDisplay={series.sampleCount === 0 ? "暂无分类" : group.occurrence ? `${(series.average * 100).toFixed(1)}%` : series.average.toFixed(2)}
            lineColor={isTemperature ? BAND_COLORS[series.code] ?? COLORS[index % 3] : ball === "red" ? COLORS[2] : COLORS[0]}
            labels={group.detailLabels ?? labels} tooltipLabel={group.detailLabels ? "质:合" : "开奖号码"} yMin={group.min} yMax={group.max}
            yInterval={group.min >= 0 && group.max <= 6 ? 1 : undefined}
            yFormatter={group.occurrence ? occurrenceLabel : undefined} zoom
          />
        ))}
      </div>
      <details className="trend-disclosure">
        <summary>统计口径</summary>
        <p>{group.description}</p>
        <p>
          按开奖先后排列；{group.occurrence ? "纵轴 1=出现、0=未出现，虚线为样本出现率。" : "纵轴为实际数值，虚线为样本均值。"}
          {group.occurrence && "包含样本中从未出现的全部情况。"}
        </p>
        {isTemperature && <p className="draw-trend-note">{data.coldHotBasis}</p>}
      </details>
      <details className="trend-disclosure draw-trend-records">
        <summary>查看逐期开奖与当前特征数据（{data.periods.length} 期）</summary>
        <div className="draw-trend-table-scroll" tabIndex={0} aria-label="逐期数据表，可横向滚动">
          <table className="draw-trend-table">
            <caption>{ball === "red" ? "红球" : "蓝球"}{group.label}逐期数据，— 表示缺少计算所需的历史数据</caption>
            <thead><tr><th scope="col">期号</th><th scope="col">红球</th><th scope="col">蓝球</th>
              {group.series.map((series) => <th scope="col" key={series.code}>{series.label}</th>)}
            </tr></thead>
            <tbody>{data.draws.map((draw, index) => <tr key={draw.period}>
              <th scope="row">{draw.period}</th>
              <td className="draw-trend-red">{draw.reds.map((number) => String(number).padStart(2, "0")).join(" ")}</td>
              <td className="draw-trend-blue">{String(draw.blue).padStart(2, "0")}</td>
              {group.series.map((series) => <td key={series.code}>{series.values[index] ?? "—"}</td>)}
            </tr>)}</tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
