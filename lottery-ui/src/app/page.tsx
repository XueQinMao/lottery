"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import SampleQueryBar from "@/components/SampleQueryBar";
import TrendCharts from "@/components/TrendCharts";
import { fetchTrend } from "@/lib/api";
import type { BallType, TrendAnalysisVo } from "@/types/trend";

/** 勾选"排除空头趋冷"后，只显示这些相位对应的号码。 */
const KEEP_PHASES = new Set<string>(["rising", "rebounding"]);

export default function Home() {
  const [ballType, setBallType] = useState<BallType>("red");
  const [ball, setBall] = useState(1);
  const [sampleSize, setSampleSize] = useState(100);
  const [endPeriod, setEndPeriod] = useState("");
  const [appliedPeriod, setAppliedPeriod] = useState("");
  const [data, setData] = useState<TrendAnalysisVo | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // “排除空头趋冷”复选框：勾选后仅保留 phase ∈ {rising, rebounding} 的号码
  const [excludeFalling, setExcludeFalling] = useState(false);
  // 每个号码的 phase 缓存（仅在复选框开启时填充），key = `${ballType}:${ball}`
  const [phaseMap, setPhaseMap] = useState<Record<string, string>>({});
  const [phasesLoading, setPhasesLoading] = useState(false);
  const [phasesError, setPhasesError] = useState<string | null>(null);

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

  // 被排除的号码集合（按当前球类型 + 当前样本/截止期下的 phase 不在保留集合）
  const excludedBalls = useMemo(() => {
    const excluded = new Set<number>();
    if (!excludeFalling) return excluded;
    for (let n = 1; n <= maxBall; n++) {
      const phase = phaseMap[`${ballType}:${n}`];
      // 未知相位（尚未加载完）一律先排除，避免误显示
      if (!phase || !KEEP_PHASES.has(phase)) {
        excluded.add(n);
      }
    }
    return excluded;
  }, [excludeFalling, phaseMap, ballType, maxBall]);

  // 切换球类型 / 样本 / 截止期时，若复选框开启则逐号请求 phase（复用原 /trend 接口）
  useEffect(() => {
    if (!excludeFalling) {
      setPhaseMap({});
      setPhasesError(null);
      return;
    }
    let cancelled = false;
    setPhasesLoading(true);
    setPhasesError(null);

    const period = appliedPeriod || undefined;
    Promise.all(
      Array.from({ length: maxBall }, (_, i) => i + 1).map((n) =>
        fetchTrend(ballType, n, sampleSize, period)
          .then((r) => [`${ballType}:${n}`, r.phase ?? ""] as const)
          .catch(() => [`${ballType}:${n}`, ""] as const),
      ),
    )
      .then((entries) => {
        if (cancelled) return;
        const map: Record<string, string> = {};
        for (const [k, v] of entries) map[k] = v;
        setPhaseMap(map);
      })
      .catch((e) => {
        if (!cancelled) {
          setPhasesError(e instanceof Error ? e.message : "相位查询失败");
        }
      })
      .finally(() => {
        if (!cancelled) setPhasesLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [excludeFalling, ballType, sampleSize, appliedPeriod, maxBall]);

  // 若当前选中球被排除，自动回退到第一个未被排除的号码
  useEffect(() => {
    if (!excludeFalling || excludedBalls.size === 0) return;
    if (excludedBalls.has(ball)) {
      for (let n = 1; n <= maxBall; n++) {
        if (!excludedBalls.has(n)) {
          setBall(n);
          return;
        }
      }
    }
  }, [excludeFalling, excludedBalls, ball, maxBall]);

  const keptCount = maxBall - excludedBalls.size;

  return (
    <div className="page">
      <SampleQueryBar
        sampleSize={sampleSize}
        onSampleSizeChange={setSampleSize}
        endPeriod={endPeriod}
        onEndPeriodChange={setEndPeriod}
        onApply={applyPeriod}
      />

      <div className="trend-layout">
        <aside className="picker-card">
          <div className="type-tabs">
            <button
              type="button"
              className={`type-tab ${ballType === "red" ? "active" : ""}`}
              onClick={() => switchType("red")}
            >
              红球 01-33
            </button>
            <button
              type="button"
              className={`type-tab ${ballType === "blue" ? "active" : ""}`}
              onClick={() => switchType("blue")}
            >
              蓝球 01-16
            </button>
          </div>

          <label className="phase-filter" title="勾选后仅显示趋势相位为「多头上升 / 空头反弹回暖」的号码">
            <input
              type="checkbox"
              checked={excludeFalling}
              onChange={(e) => setExcludeFalling(e.target.checked)}
            />
            <span>排除空头趋冷</span>
            {excludeFalling && phasesLoading && <span className="phase-filter-hint">（加载中…）</span>}
            {excludeFalling && !phasesLoading && phasesError && (
              <span className="phase-filter-hint error">{`（${phasesError}）`}</span>
            )}
            {excludeFalling && !phasesLoading && !phasesError && (
              <span className="phase-filter-hint">{`（保留 ${keptCount} 个）`}</span>
            )}
          </label>

          <div className="ball-grid">
            {Array.from({ length: maxBall }, (_, i) => i + 1).map((n) => {
              const excluded = excludedBalls.has(n);
              return (
                <button
                  key={n}
                  type="button"
                  className={`ball-btn ${ballType} ${ball === n ? "active" : ""} ${excluded ? "excluded" : ""}`}
                  onClick={() => setBall(n)}
                  disabled={excluded}
                  aria-pressed={ball === n}
                  aria-label={`${ballType === "red" ? "红球" : "蓝球"} ${String(n).padStart(2, "0")}${excluded ? "（已排除）" : ""}`}
                >
                  {String(n).padStart(2, "0")}
                </button>
              );
            })}
          </div>
        </aside>

        <div className="trend-main">
          {loading && <div className="status">加载中...</div>}
          {error && <div className="status error">{error}</div>}
          {!loading && !error && data && <TrendCharts data={data} />}
        </div>
      </div>

      <div className="info-bar">
        算法：指数 = 平均遗漏 / max(遗漏值, 1) | SMA 基于指数序列 | 与
        LotteryTrendUtils.java 完全一致
        {appliedPeriod ? `　截止期 ${appliedPeriod}` : "　截止=最新"}
        {data ? `　样本 ${data.stats.totalPeriods} 期` : ""}
        {excludeFalling && !phasesLoading && !phasesError
          ? `　仅显示多头上升/回暖（保留 ${keptCount} 个，排除 ${excludedBalls.size} 个）`
          : ""}
      </div>
    </div>
  );
}
