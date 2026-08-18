"use client";

import { useCallback, useEffect, useState } from "react";
import FeatureLineChart from "@/components/FeatureLineChart";
import FeatureStatsToolbar from "@/components/FeatureStatsToolbar";
import { fetchFeatureStats } from "@/lib/api";
import type { FeatureStatsVo } from "@/types/feature-stats";

export default function FeatureSumSpanPage() {
  const [sampleSize, setSampleSize] = useState(100);
  const [endPeriod, setEndPeriod] = useState("");
  const [appliedPeriod, setAppliedPeriod] = useState("");
  const [data, setData] = useState<FeatureStatsVo | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (size: number, period: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchFeatureStats(size, period || undefined);
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
    <main className="page">
      <header className="header">
        <h1>红球和值 / 差值统计</h1>
        <p className="sub">
          X 轴为开奖期号，虚线为样本均值（仅红球）。截止期号空=最新。
        </p>
      </header>

      <FeatureStatsToolbar
        sampleSize={sampleSize}
        onSampleSizeChange={setSampleSize}
        endPeriod={endPeriod}
        onEndPeriodChange={setEndPeriod}
        onApplyPeriod={() => setAppliedPeriod(endPeriod.trim())}
      />

      {loading && <div className="status">加载中...</div>}
      {error && <div className="status error">{error}</div>}
      {!loading && !error && data && (
        <>
          <FeatureLineChart
            title="红球和值统计"
            yName="和值"
            periods={data.periods}
            values={data.sumValues}
            avg={data.sumAvg}
            lineColor="#f85149"
          />
          <FeatureLineChart
            title="红球差值统计（跨度 = 最大号 − 最小号）"
            yName="差值"
            periods={data.periods}
            values={data.spanValues}
            avg={data.spanAvg}
            lineColor="#3fb950"
          />
        </>
      )}
    </main>
  );
}
