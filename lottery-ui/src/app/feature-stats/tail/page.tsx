"use client";

import { useCallback, useEffect, useState } from "react";
import FeatureLineChart from "@/components/FeatureLineChart";
import FeatureStatsToolbar from "@/components/FeatureStatsToolbar";
import SampleQueryBar from "@/components/SampleQueryBar";
import { fetchFeatureStats } from "@/lib/api";
import type { FeatureStatsVo } from "@/types/feature-stats";

const TAIL_COLORS = [
  "#DC2626",
  "#F59E0B",
  "#22C55E",
  "#3B82F6",
  "#8B5CF6",
  "#EC4899",
  "#14B8A6",
  "#F97316",
  "#6366F1",
  "#84CC16",
];

export default function FeatureTailPage() {
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
      {!loading && !error && data && (data.tailCounts?.length ? (
        <div className="charts-grid">
          {data.tailCounts.map((values, tail) => (
            <FeatureLineChart
              key={tail}
              title={`${tail}尾个数（红球号码 % 10 = ${tail}）`}
              yName={`${tail}尾个数`}
              periods={data.periods}
              values={values}
              avg={data.tailAvgs?.[tail] ?? 0}
              lineColor={TAIL_COLORS[tail]}
              yMin={0}
              yMax={6}
              yInterval={1}
            />
          ))}
        </div>
      ) : (
        <div className="status">暂无尾数统计数据</div>
      ))}
    </div>
  );
}
