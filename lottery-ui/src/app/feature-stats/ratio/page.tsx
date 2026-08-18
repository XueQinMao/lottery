"use client";

import { useCallback, useEffect, useState } from "react";
import FeatureLineChart from "@/components/FeatureLineChart";
import FeatureStatsToolbar from "@/components/FeatureStatsToolbar";
import { fetchFeatureStats } from "@/lib/api";
import type { FeatureStatsVo } from "@/types/feature-stats";

function blueOddLabel(value: number) {
  return value === 1 ? "奇" : value === 0 ? "偶" : String(value);
}

export default function FeatureRatioPage() {
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
        <h1>质合比 / 奇偶比统计</h1>
        <p className="sub">
          质合比仅红球；奇偶比红蓝分开。Y 轴为个数（蓝球 1=奇 / 0=偶），虚线为均值。截止期号空=最新。
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
            title="红球质合比（Y = 质数个数，提示中为 质:合）"
            yName="质数个数"
            periods={data.periods}
            values={data.primeCounts}
            avg={data.primeAvg}
            labels={data.primeRatios}
            lineColor="#a371f7"
            yMin={0}
            yMax={6}
            yInterval={1}
          />
          <FeatureLineChart
            title="红球奇偶比（Y = 奇数个数，提示中为 奇:偶）"
            yName="奇数个数"
            periods={data.periods}
            values={data.redOddCounts}
            avg={data.redOddAvg}
            labels={data.redOddEvenRatios}
            lineColor="#f85149"
            yMin={0}
            yMax={6}
            yInterval={1}
          />
          <FeatureLineChart
            title="蓝球奇偶统计（1 = 奇，0 = 偶）"
            yName="奇偶"
            periods={data.periods}
            values={data.blueOddFlags}
            avg={data.blueOddAvg}
            labels={data.blueOddEvenLabels}
            lineColor="#58a6ff"
            yMin={0}
            yMax={1}
            yInterval={1}
            yFormatter={blueOddLabel}
          />
        </>
      )}
    </main>
  );
}
