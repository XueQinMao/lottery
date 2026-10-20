"use client";

import { useState } from "react";
import NumberTrendGrid from "@/components/NumberTrendGrid";
import SampleQueryBar from "@/components/SampleQueryBar";
import DrawSummary from "@/components/DrawSummary";
import { useHistoryQuery } from "@/hooks/useHistoryQuery";
import { fetchNumberTrends } from "@/lib/api";
import type { NumberTrendMode } from "@/types/number-trend";

const MODES: { value: NumberTrendMode; label: string }[] = [
  { value: "all", label: "红蓝综合" }, { value: "red", label: "红球走势" }, { value: "blue", label: "蓝球走势" },
];

export default function NumberTrendPage() {
  const [sampleSize, setSampleSize] = useState(30);
  const [endPeriod, setEndPeriod] = useState("");
  const [appliedPeriod, setAppliedPeriod] = useState("");
  const [validation, setValidation] = useState<string | null>(null);
  const [mode, setMode] = useState<NumberTrendMode>("all");
  const [showOmissions, setShowOmissions] = useState(true);
  const [showRedPatterns, setShowRedPatterns] = useState(true);
  const [showBlueLine, setShowBlueLine] = useState(true);
  const [showGuides, setShowGuides] = useState(true);
  const { data, loading, error, retry } = useHistoryQuery(fetchNumberTrends, sampleSize, appliedPeriod);

  const apply = () => {
    const period = endPeriod.trim();
    if (period && !/^\d{7}$/.test(period)) {
      setValidation("截止期号应为 7 位数字，如 2026092；留空查询最新开奖。");
      return;
    }
    setValidation(null);
    if (period === appliedPeriod) retry();
    else setAppliedPeriod(period);
  };

  return <div className="page">
    <div className="filter-card">
      <div className="draw-trend-toolbar">
        <div className="type-tabs" aria-label="走势类型">
          {MODES.map((item) => <button type="button" key={item.value} className={`type-tab ${mode === item.value ? "active" : ""}`}
            aria-pressed={mode === item.value} onClick={() => setMode(item.value)}>{item.label}</button>)}
        </div>
        <div className="number-chart-options">
          <label><input type="checkbox" checked={showOmissions} onChange={(event) => setShowOmissions(event.target.checked)} />显示遗漏</label>
          {mode !== "blue" && <label><input type="checkbox" checked={showRedPatterns}
            onChange={(event) => setShowRedPatterns(event.target.checked)} />红球形态线</label>}
          {mode !== "red" && <label><input type="checkbox" checked={showBlueLine}
            onChange={(event) => setShowBlueLine(event.target.checked)} />蓝球走势线</label>}
          <label><input type="checkbox" checked={showGuides} onChange={(event) => setShowGuides(event.target.checked)} />区间辅助线</label>
        </div>
      </div>
      <SampleQueryBar sampleSize={sampleSize} onSampleSizeChange={setSampleSize} endPeriod={endPeriod}
        onEndPeriodChange={setEndPeriod} onApply={apply} />
      {validation && <p className="draw-trend-validation" role="alert">{validation}</p>}
    </div>
    {!loading && !error && data && data.rows.length > 0 && <DrawSummary draw={data.rows[data.rows.length - 1]}
      firstPeriod={data.rows[0].period} count={data.rows.length} />}
    <div className="number-chart-legend">
      <span><i className="number-chart-legend-ball red" />红球命中</span><span><i className="number-chart-legend-ball blue" />蓝球命中</span>
      <span>灰色数字为连续遗漏期数</span><span>点击号码表头可高亮整列</span>
    </div>
    {loading && <div className="status" role="status">正在加载号码走势图…</div>}
    {error && <div className="status error" role="alert">{error} <button type="button" className="type-tab" onClick={retry}>重新加载</button></div>}
    {!loading && !error && data && (data.rows.length > 0
      ? <NumberTrendGrid data={data} mode={mode} showOmissions={showOmissions} showRedPatterns={showRedPatterns}
        showBlueLine={showBlueLine} showGuides={showGuides} />
      : <div className="status">没有可展示的开奖记录。</div>)}
    <details className="trend-disclosure number-chart-help">
      <summary>走势图说明与统计口径</summary>
      <p>期号从上到下由旧到新。命中球显示号码，未命中格显示截至当期连续未开出的期数；遗漏沿用数据库中更早的开奖记录累计，切换展示期数不会清零。</p>
      <p>红球横线连接同期开出的邻近号码，竖线连接相邻期或间隔一期的重号。斜线标记等步长、折返及与横竖线相接的邻号。最近两期相差 1 的红球也可直接连成斜线。蓝球按逐期开奖连线。</p>
      <p>虚线把末段形态延伸到截止期之后的一期，最多标出 8 个红球候选。把鼠标放在候选号码上可查看该号码对应的形态。</p>
      <p>红球辅助线划分 1–11、12–22、23–33，蓝球划分 1–4、5–8、9–12、13–16。</p>
      <p>底部统计当前展示期数：平均遗漏 =（展示期数 − 出现次数）÷ 出现次数；未出现记为 —。最大遗漏为展示期内观察到的最大值，最大连出只统计展示期内的连续命中。</p>
    </details>
  </div>;
}
