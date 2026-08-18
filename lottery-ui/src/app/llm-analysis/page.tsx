"use client";

import { useCallback, useEffect, useState } from "react";
import AnalysisReport from "@/components/AnalysisReport";
import { fetchAnalyzeLatest } from "@/lib/api";
import type { LotteryAnalysisResp } from "@/types/llm-analysis";

const SAMPLE_OPTIONS = [100];

export default function LlmAnalysisPage() {
  const [sampleSize, setSampleSize] = useState(100);
  const [data, setData] = useState<LotteryAnalysisResp | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (size: number) => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchAnalyzeLatest(size);
      setData(result);
    } catch (e) {
      setData(null);
      setError(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(sampleSize);
  }, [load, sampleSize]);

  return (
    <div className="page">
      <div className="filter-card">
        <div className="type-tabs">
          {SAMPLE_OPTIONS.map((n) => (
            <button
              key={n}
              type="button"
              className={`type-tab ${sampleSize === n ? "active" : ""}`}
              onClick={() => setSampleSize(n)}
            >
              近 {n} 期
            </button>
          ))}
        </div>
      </div>

      {loading && <div className="status">分析中，请稍候...</div>}
      {error && <div className="status error">{error}</div>}
      {!loading && !error && data && <AnalysisReport data={data} />}
    </div>
  );
}
