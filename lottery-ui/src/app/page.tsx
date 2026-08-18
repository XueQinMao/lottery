"use client";

import { useCallback, useEffect, useState } from "react";
import SampleQueryBar from "@/components/SampleQueryBar";
import TrendCharts from "@/components/TrendCharts";
import { fetchTrend } from "@/lib/api";
import type { BallType, TrendAnalysisVo } from "@/types/trend";

export default function Home() {
  const [ballType, setBallType] = useState<BallType>("red");
  const [ball, setBall] = useState(1);
  const [sampleSize, setSampleSize] = useState(100);
  const [endPeriod, setEndPeriod] = useState("");
  const [appliedPeriod, setAppliedPeriod] = useState("");
  const [data, setData] = useState<TrendAnalysisVo | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const maxBall = ballType === "red" ? 33 : 16;

  const load = useCallback(
    async (type: BallType, num: number, size: number, period: string) => {
      setLoading(true);
      setError(null);
      try {
        const result = await fetchTrend(type, num, size, period || undefined);
        setData(result);
      } catch (e) {
        setData(null);
        setError(e instanceof Error ? e.message : "加载失败");
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    void load(ballType, ball, sampleSize, appliedPeriod);
  }, [ballType, ball, sampleSize, appliedPeriod, load]);

  const switchType = (type: BallType) => {
    setBallType(type);
    setBall(1);
  };

  const applyPeriod = () => setAppliedPeriod(endPeriod.trim());

  return (
    <main className="page">
      <header className="header">
        <h1>双色球遗漏趋势分析</h1>
        <p className="sub">
          可切换近 30/50/100 期；截止期号空=最新，填写如 2026092 表示含该期往前推
        </p>
      </header>

      <SampleQueryBar
        sampleSize={sampleSize}
        onSampleSizeChange={setSampleSize}
        endPeriod={endPeriod}
        onEndPeriodChange={setEndPeriod}
        onApply={applyPeriod}
      />

      <div className="type-tabs">
        <button
          type="button"
          className={`type-tab ${ballType === "red" ? "active" : ""}`}
          onClick={() => switchType("red")}
        >
          红球 (01-33)
        </button>
        <button
          type="button"
          className={`type-tab ${ballType === "blue" ? "active" : ""}`}
          onClick={() => switchType("blue")}
        >
          蓝球 (01-16)
        </button>
      </div>

      <div className="ball-grid">
        {Array.from({ length: maxBall }, (_, i) => i + 1).map((n) => (
          <button
            key={n}
            type="button"
            className={`ball-btn ${ballType} ${ball === n ? "active" : ""}`}
            onClick={() => setBall(n)}
          >
            {String(n).padStart(2, "0")}
          </button>
        ))}
      </div>

      {loading && <div className="status">加载中...</div>}
      {error && <div className="status error">{error}</div>}
      {!loading && !error && data && <TrendCharts data={data} />}

      <div className="info-bar">
        算法：指数 = 平均遗漏 / max(遗漏值, 1) | SMA 基于指数序列 | 与
        LotteryTrendUtils.java 完全一致
        {appliedPeriod ? `　截止期 ${appliedPeriod}` : "　截止=最新"}
        {data ? `　样本 ${data.stats.totalPeriods} 期` : ""}
      </div>
    </main>
  );
}
