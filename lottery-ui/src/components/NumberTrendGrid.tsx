"use client";

import { useMemo, useState, type CSSProperties } from "react";
import { buildRedTrendPatterns, type RedPatternSegment } from "@/lib/redTrendPatterns";
import type { NumberTrend, NumberTrendMode, NumberTrendStats } from "@/types/number-trend";

const CELL_WIDTH = 28;
const ROW_HEIGHT = 32;
const pad = (value: number) => String(value).padStart(2, "0");
const columnCenter = (number: number) => (number - 0.5) * CELL_WIDTH;
const rowCenter = (row: number) => (row + 0.5) * ROW_HEIGHT;

function PatternLine({ segment, projected }: { segment: RedPatternSegment; projected?: boolean }) {
  return <line x1={columnCenter(segment.from.number)} y1={rowCenter(segment.from.row)}
    x2={columnCenter(segment.to.number)} y2={rowCenter(segment.to.row)} vectorEffect="non-scaling-stroke"
    className={`number-chart-line red-pattern ${projected ? "projected" : ""}`} data-pattern={segment.kind} />;
}
const SUMMARY: { label: string; value: (stats: NumberTrendStats) => number | string }[] = [
  { label: "出现次数", value: (stats) => stats.hitCount },
  { label: "平均遗漏", value: (stats) => stats.averageOmission == null ? "—" : stats.averageOmission.toFixed(1) },
  { label: "最大遗漏", value: (stats) => stats.maxOmission },
  { label: "最大连出", value: (stats) => stats.maxConsecutive },
  { label: "当前遗漏", value: (stats) => stats.currentOmission },
];

export default function NumberTrendGrid({ data, mode, showOmissions, showRedPatterns, showBlueLine, showGuides }: {
  data: NumberTrend; mode: NumberTrendMode; showOmissions: boolean; showRedPatterns: boolean;
  showBlueLine: boolean; showGuides: boolean;
}) {
  const [highlight, setHighlight] = useState<string | null>(null);
  const columns = useMemo(() => [
    ...(mode !== "blue" ? data.redStats.map((stats) => ({ key: `red-${stats.number}`, ball: "red", stats })) : []),
    ...(mode !== "red" ? data.blueStats.map((stats) => ({ key: `blue-${stats.number}`, ball: "blue", stats })) : []),
  ], [data.redStats, data.blueStats, mode]);
  const redCount = mode === "blue" ? 0 : 33;
  const chartStyle = { "--number-columns": columns.length } as CSSProperties;
  const rowStyle = { gridTemplateColumns: `var(--number-period-width) repeat(${columns.length}, minmax(0, 1fr))` };
  const redPatterns = useMemo(() => buildRedTrendPatterns(data.rows, data.redCandidates), [data.rows, data.redCandidates]);
  const redVisible = mode !== "blue" && showRedPatterns;
  const blueVisible = mode !== "red" && showBlueLine;
  const candidates = new Map(redPatterns.candidates.map((candidate) => [candidate.number, candidate]));
  const showCandidateRow = redVisible && candidates.size > 0;
  const bodyHeight = (data.rows.length + Number(showCandidateRow)) * ROW_HEIGHT;

  return <>
    {redVisible && <section className="number-pattern-summary" aria-label="下期红球候选">
      <strong>下期红球候选</strong>
      {candidates.size > 0 ? <ul className="number-pattern-candidates">
        {redPatterns.candidates.map(({ number, reasons }) => <li key={number}>
          <button type="button" className="number-pattern-candidate" aria-pressed={highlight === `red-${number}`}
            title={reasons.join("。")} aria-label={`${pad(number)}，${reasons.join("。")}`}
            onClick={() => setHighlight(highlight === `red-${number}` ? null : `red-${number}`)}>
            {pad(number)}
          </button>
        </li>)}
      </ul> : <span className="muted">当前末段暂无可外推的形态</span>}
    </section>}
    <div className={`number-chart-scroll ${showGuides ? "with-guides" : ""}`} tabIndex={0} aria-label="号码走势图，可横向和纵向滚动">
      <div className="number-chart" style={chartStyle} role="table" aria-label="双色球号码分布与遗漏表"
        aria-rowcount={data.rows.length + 6 + Number(showCandidateRow)} aria-colcount={columns.length + 1}>
        <div className="number-chart-head" role="rowgroup">
          <div className="number-chart-groups" style={rowStyle} aria-hidden="true">
            <span className="number-chart-period">开奖期号</span>
            {mode !== "blue" && <span className="number-chart-group red" style={{ gridColumn: `span ${redCount}` }}>红球号码分布 · 01–33</span>}
            {mode !== "red" && <span className="number-chart-group blue" style={{ gridColumn: "span 16" }}>蓝球号码分布 · 01–16</span>}
          </div>
          <div className="number-chart-row" style={rowStyle} role="row">
            <span className="number-chart-period" role="columnheader">期号 / 号码</span>
            {columns.map(({ key, ball, stats }) => <span key={key} role="columnheader" className={`number-chart-cell ${ball} ${showGuides && (ball === "red" ? [11, 22, 33] : [4, 8, 12, 16]).includes(stats.number) ? "zone-end" : ""}`}>
              <button type="button" className="number-chart-number" aria-pressed={highlight === key}
                aria-label={`高亮${ball === "red" ? "红球" : "蓝球"} ${pad(stats.number)}`}
                onClick={() => setHighlight(highlight === key ? null : key)}>{pad(stats.number)}</button>
            </span>)}
          </div>
        </div>
        <div className="number-chart-body" role="rowgroup">
          {(redVisible || blueVisible) && <svg className="number-chart-lines" viewBox={`0 0 ${columns.length * CELL_WIDTH} ${bodyHeight}`}
            preserveAspectRatio="none" height={bodyHeight} aria-hidden="true">
            {redVisible && redPatterns.segments.map((segment) => <PatternLine segment={segment}
              key={`${segment.from.row}-${segment.from.number}-${segment.to.row}-${segment.to.number}`} />)}
            {showCandidateRow && redPatterns.candidates.flatMap((candidate) => candidate.projections.map((segment) =>
              <PatternLine segment={segment} projected key={`next-${segment.from.row}-${segment.from.number}-${segment.to.number}`} />))}
            {blueVisible && <polyline points={data.rows.map((row, index) =>
              `${columnCenter(redCount + row.blue)},${rowCenter(index)}`).join(" ")}
              vectorEffect="non-scaling-stroke" className="number-chart-line blue" />}
          </svg>}
          {data.rows.map((row, index) => <div key={row.period} className={`number-chart-row ${index === data.rows.length - 1 ? "latest" : ""}`} style={rowStyle} role="row">
            <span className="number-chart-period" role="rowheader" title={`${row.period} ${row.openDate}`}>{row.period}</span>
            {columns.map(({ key, ball, stats }) => {
              const omission = (ball === "red" ? row.redOmissions : row.blueOmissions)[stats.number - 1];
              const hit = omission === 0;
              const zoneEnd = (ball === "red" ? [11, 22, 33] : [4, 8, 12, 16]).includes(stats.number);
              return <span key={key} role="cell" className={`number-chart-cell ${ball} ${highlight === key ? "highlight" : ""} ${zoneEnd ? "zone-end" : ""}`}
                aria-label={`${ball === "red" ? "红球" : "蓝球"}${pad(stats.number)} ${hit ? "开出" : `遗漏 ${omission} 期`}`}
                title={`${row.period} · ${ball === "red" ? "红球" : "蓝球"} ${pad(stats.number)} · ${hit ? "本期开出" : `连续遗漏 ${omission} 期`}`}>
                {hit ? <span className={`number-chart-ball ${ball}`}>{pad(stats.number)}</span> : showOmissions ? <span className="number-chart-omission">{omission}</span> : null}
              </span>;
            })}
          </div>)}
          {showCandidateRow && <div className="number-chart-row number-chart-candidate-row" style={rowStyle} role="row">
            <span className="number-chart-period" role="rowheader">下期候选</span>
            {columns.map(({ key, ball, stats }) => {
              const candidate = ball === "red" ? candidates.get(stats.number) : undefined;
              return <span key={key} className={`number-chart-cell ${highlight === key ? "highlight" : ""}`} role="cell"
                title={candidate?.reasons.join("。")}>
                {candidate && <span className="number-chart-ball candidate">{pad(candidate.number)}</span>}
              </span>;
            })}
          </div>}
        </div>
        <div className="number-chart-summary" role="rowgroup">
          {SUMMARY.map((item) => <div key={item.label} className="number-chart-row" style={rowStyle} role="row">
            <span className="number-chart-period" role="rowheader">{item.label}</span>
            {columns.map(({ key, stats }) => <span key={key} className="number-chart-cell" role="cell">{item.value(stats)}</span>)}
          </div>)}
        </div>
      </div>
    </div>
  </>;
}
