"use client";

import { useCallback, useEffect, useState } from "react";
import FeatureLineChart from "@/components/FeatureLineChart";
import FeatureStatsToolbar from "@/components/FeatureStatsToolbar";
import SampleQueryBar from "@/components/SampleQueryBar";
import { fetchColdHotTrend } from "@/lib/api";
import type { ColdHotTrendVo } from "@/types/cold-hot-trend";

/** 蓝球档位数值 → 文案 */
function blueBandLabel(value: number) {
  return value === 2 ? "热" : value === 1 ? "温" : value === 0 ? "冷" : String(value);
}

export default function FeatureColdHotPage() {
  const [sampleSize, setSampleSize] = useState(100);
  const [endPeriod, setEndPeriod] = useState("");
  const [appliedPeriod, setAppliedPeriod] = useState("");
  const [data, setData] = useState<ColdHotTrendVo | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (size: number, period: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchColdHotTrend(size, period || undefined);
      setData(result);
    } catch (e) {
      setData(null);
      setError(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(sampleSize, appliedPeriod);
  }, [load, sampleSize, appliedPeriod]);

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
      </div>

      {loading && <div className="status">加载中...</div>}
      {error && <div className="status error">{error}</div>}
      {!loading && !error && data && (
        <div className="charts-grid">
          <FeatureLineChart
            title="红球热号个数（基于前 30 期冷温热分类）"
            yName="热号个数"
            periods={data.periods}
            values={data.redHotCounts}
            avg={data.redHotAvg}
            lineColor="#DC2626"
            yMin={0}
            yMax={6}
            yInterval={1}
          />
          <FeatureLineChart
            title="红球温号个数（基于前 30 期冷温热分类）"
            yName="温号个数"
            periods={data.periods}
            values={data.redWarmCounts}
            avg={data.redWarmAvg}
            lineColor="#F59E0B"
            yMin={0}
            yMax={6}
            yInterval={1}
          />
          <FeatureLineChart
            title="红球冷号个数（基于前 30 期冷温热分类）"
            yName="冷号个数"
            periods={data.periods}
            values={data.redColdCounts}
            avg={data.redColdAvg}
            lineColor="#3B82F6"
            yMin={0}
            yMax={6}
            yInterval={1}
          />
          <FeatureLineChart
            title="蓝球档位（2=热, 1=温, 0=冷）"
            yName="档位"
            periods={data.periods}
            values={data.blueBandFlags}
            avg={data.blueBandFlags.reduce(
              (s, v) => s + (v ?? 0),
              0,
            ) / Math.max(1, data.blueBandFlags.length)}
            labels={data.blueBandLabels}
            lineColor="#8B5CF6"
            yMin={0}
            yMax={2}
            yInterval={1}
            yFormatter={blueBandLabel}
          />
        </div>
      )}

      <div className="info-bar">
        每期取其前 30 期作为样本调用 ColdHotAnalysisUtils 计算冷温热号，再统计本期实际开出号码落入各档的个数。
        {appliedPeriod ? `　截止期 ${appliedPeriod}` : "　截止=最新"}
        {data ? `　样本 ${data.periods.length} 期` : ""}
        {data
          ? `　红球均：热 ${data.redHotAvg} / 温 ${data.redWarmAvg} / 冷 ${data.redColdAvg}`
          : ""}
        {data
          ? `　蓝球占比：热 ${(data.blueHotAvg * 100).toFixed(1)}% / 温 ${(data.blueWarmAvg * 100).toFixed(1)}% / 冷 ${(data.blueColdAvg * 100).toFixed(1)}%`
          : ""}
      </div>
    </div>
  );
}
