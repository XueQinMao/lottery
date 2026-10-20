"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import DrawTrendCharts from "@/components/DrawTrendCharts";
import DrawFeatureSelector from "@/components/DrawFeatureSelector";
import DrawSummary from "@/components/DrawSummary";
import SampleQueryBar from "@/components/SampleQueryBar";
import { useDrawTrends } from "@/hooks/useDrawTrends";

export default function HistoryTrendPage() {
  const searchParams = useSearchParams();
  const [ball, setBall] = useState<"red" | "blue">(searchParams.get("ball") === "blue" ? "blue" : "red");
  const [feature, setFeature] = useState(searchParams.get("feature") ?? "coldHot");
  const [sampleSize, setSampleSize] = useState(100);
  const [endPeriod, setEndPeriod] = useState("");
  const [appliedPeriod, setAppliedPeriod] = useState("");
  const [validation, setValidation] = useState<string | null>(null);
  const { data, loading, error, retry } = useDrawTrends(sampleSize, appliedPeriod);
  const groups = data ? (ball === "red" ? data.redFeatures : data.blueFeatures) : [];
  const group = groups.find((entry) => entry.code === feature) ?? groups[0];

  const apply = () => {
    const period = endPeriod.trim();
    if (period && !/^\d{7}$/.test(period)) {
      setValidation("请输入 7 位截止期号，如 2026092；留空查询最新开奖。");
      return;
    }
    setValidation(null);
    if (period === appliedPeriod) retry();
    else setAppliedPeriod(period);
  };

  return (
    <div className="page">
      <div className="filter-card">
        <div className="draw-trend-toolbar history-query-toolbar">
          <div className="type-tabs" aria-label="球种">
            {(["red", "blue"] as const).map((type) => <button key={type} type="button"
              className={`type-tab ${ball === type ? "active" : ""}`} aria-pressed={ball === type}
              onClick={() => { setBall(type); setFeature("coldHot"); }}>
              <span className={`draw-trend-dot ${type}`} />{type === "red" ? "红球特征" : "蓝球特征"}
            </button>)}
          </div>
          <SampleQueryBar sampleSize={sampleSize} onSampleSizeChange={setSampleSize}
            endPeriod={endPeriod} onEndPeriodChange={setEndPeriod} onApply={apply} />
        </div>
        {validation && <p className="draw-trend-validation" role="alert">{validation}</p>}
      </div>
      {loading && <div className="status" role="status">正在加载历史开奖与特征趋势…</div>}
      {error && <div className="status error" role="alert">{error} <button type="button" className="type-tab" onClick={retry}>重新加载</button></div>}
      {!loading && !error && data && data.periods.length === 0 && <div className="status">没有符合条件的开奖记录，请调整截止期号。</div>}
      {!loading && !error && data && data.periods.length > 0 && group && <>
        <DrawSummary draw={data.draws[data.draws.length - 1]} firstPeriod={data.periods[0]} count={data.periods.length} />
        <DrawFeatureSelector groups={groups} selected={group.code} onSelect={setFeature} />
        <DrawTrendCharts data={data} group={group} ball={ball} />
      </>}
    </div>
  );
}
