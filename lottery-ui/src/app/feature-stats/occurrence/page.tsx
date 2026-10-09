"use client";

import { useEffect, useState } from "react";
import FeatureLineChart from "@/components/FeatureLineChart";
import FeatureStatsToolbar from "@/components/FeatureStatsToolbar";
import SampleQueryBar from "@/components/SampleQueryBar";
import { fetchFeatureStats } from "@/lib/api";
import type { FeatureStatsVo } from "@/types/feature-stats";

const COLORS = ["#DC2626", "#F59E0B", "#22C55E", "#3B82F6", "#8B5CF6", "#EC4899", "#14B8A6"];
const ZONES: Record<string, { label: string; range: string; color: string }> = {
  zone1Count: { label: "一区", range: "01–11", color: "#DC2626" },
  zone2Count: { label: "二区", range: "12–22", color: "#F59E0B" },
  zone3Count: { label: "三区", range: "23–33", color: "#3B82F6" },
};

function occurrenceLabel(value: number) {
  return value === 1 ? "出现" : value === 0 ? "未出现" : "";
}

function featureHint(code: string) {
  switch (code) {
    case "oddEven": return "比例顺序：奇数:偶数。";
    case "bigSmall": return "比例顺序：大号:小号；17–33 为大号，01–16 为小号。";
    case "primeComp": return "比例顺序：质数:合数；沿用形态指数口径，01 计为质数。";
    case "ratio012": return "比例顺序：0路:1路:2路（红球号码除以 3 的余数）。";
    case "span": return "跨度 = 最大红球 − 最小红球；与形态指数一致，取值为 5–32。";
    case "sumRange": return "和值为六个红球之和；沿用形态指数的 16 个闭区间，包含两端。";
    case "sumTail": return "和值尾数 = 六个红球之和 % 10；取值为 0–9。";
    case "threeZone": return "比例顺序：一区:二区:三区；一区 01–11、二区 12–22、三区 23–33。";
    default: return "一区 01–11、二区 12–22、三区 23–33；各区个数取值为 0–6。";
  }
}

export default function FeatureOccurrencePage() {
  const [sampleSize, setSampleSize] = useState(100);
  const [endPeriod, setEndPeriod] = useState("");
  const [appliedPeriod, setAppliedPeriod] = useState("");
  const [featureCode, setFeatureCode] = useState("oddEven");
  const [data, setData] = useState<FeatureStatsVo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const result = await fetchFeatureStats(sampleSize, appliedPeriod || undefined);
        if (!cancelled) setData(result);
      } catch (e) {
        if (!cancelled) {
          setData(null);
          setError(e instanceof Error ? e.message : "加载失败");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [sampleSize, appliedPeriod]);

  const features = data?.occurrenceFeatures ?? [];
  const zoneFeatures = features.filter((feature) => ZONES[feature.code]);
  const isZoneView = featureCode === "zoneCounts";
  const selected = isZoneView ? zoneFeatures[0] : features.find((feature) => feature.code === featureCode);
  // 接口已包含0～6各数量的逐期出现标记，还原为每期实际开出的个数。
  const zoneCharts = zoneFeatures.map((feature) => {
    const counts = (data?.periods ?? []).map((_, periodIndex) => feature.series.reduce(
      (count, series) => count + Number(series.value) * series.hits[periodIndex], 0,
    ));
    return {
      code: feature.code,
      ...ZONES[feature.code],
      counts,
      avg: counts.reduce((sum, count) => sum + count, 0) / Math.max(1, counts.length),
    };
  });

  return (
    <div className="page">
      <div className="filter-card">
        <FeatureStatsToolbar />
        <SampleQueryBar
          sampleSize={sampleSize}
          onSampleSizeChange={setSampleSize}
          endPeriod={endPeriod}
          onEndPeriodChange={setEndPeriod}
          onApply={() => setAppliedPeriod(endPeriod.trim())}
        />
        <div className="type-tabs">
          {features.filter((feature) => !ZONES[feature.code]).map((feature) => (
            <button
              key={feature.code}
              type="button"
              className={`type-tab ${featureCode === feature.code ? "active" : ""}`}
              onClick={() => setFeatureCode(feature.code)}
            >
              {feature.label}
            </button>
          ))}
          {zoneFeatures.length > 0 && (
            <button
              type="button"
              className={`type-tab ${isZoneView ? "active" : ""}`}
              onClick={() => setFeatureCode("zoneCounts")}
            >
              分区数量
            </button>
          )}
        </div>
      </div>
      {loading && <div className="status">加载中...</div>}
      {error && <div className="status error">{error}</div>}
      {!loading && !error && selected && data && (
        <>
          <div className="info-bar">
            {isZoneView
              ? "一区 01–11、二区 12–22、三区 23–33；按期展示各区实际开出的红球数量，最少0个、最多6个，虚线为各区样本平均数量。"
              : `${featureHint(selected.code)} 每种取值单独绘图，1=出现，0=未出现，虚线为样本出现率。`}
            当前样本 {data.periods.length} 期；截止 {data.periods.at(-1) ?? "—"}。
          </div>
          <div className="charts-grid">
            {isZoneView ? zoneCharts.map((zone) => (
              <FeatureLineChart
                key={zone.code}
                title={`${zone.label}历史开出红球数量（${zone.range}）`}
                yName="红球数量"
                periods={data.periods}
                values={zone.counts}
                avg={zone.avg}
                avgLabel="平均数量"
                lineColor={zone.color}
                yMin={0}
                yMax={6}
                yInterval={1}
              />
            )) : selected.series.map((series, index) => (
              <FeatureLineChart
                key={`${selected.code}-${series.value}`}
                title={`${selected.label} ${series.value}（出现 ${series.hitCount} 次）`}
                yName="出现情况"
                periods={data.periods}
                values={series.hits}
                avg={series.hitRate}
                avgLabel="出现率"
                avgDisplay={`${(series.hitRate * 100).toFixed(2)}%`}
                lineColor={COLORS[index % COLORS.length]}
                yMin={0}
                yMax={1}
                yInterval={1}
                yFormatter={occurrenceLabel}
              />
            ))}
          </div>
        </>
      )}
      {!loading && !error && !selected && <div className="status">暂无历史出现统计数据</div>}
    </div>
  );
}
