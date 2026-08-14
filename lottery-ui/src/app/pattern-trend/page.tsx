"use client";

import { useCallback, useEffect, useState } from "react";
import PatternTrendCharts from "@/components/PatternTrendCharts";
import { fetchPatternTrend } from "@/lib/api";
import type { PatternFeature, PatternTrendVo } from "@/types/pattern-trend";

type BallGroup = "red" | "blue";

const RED_FEATURES: { code: PatternFeature; label: string; defaultRatio: string }[] =
  [
    { code: "oddEven", label: "奇偶比", defaultRatio: "1:5" },
    { code: "bigSmall", label: "大小比", defaultRatio: "2:4" },
    { code: "primeComp", label: "质合比", defaultRatio: "2:4" },
    { code: "ratio012", label: "012路比", defaultRatio: "0:4:2" },
    { code: "span", label: "跨度", defaultRatio: "21" },
    { code: "sumRange", label: "和值区间", defaultRatio: "73-78" },
    { code: "sumTail", label: "和值尾数", defaultRatio: "0" },
    { code: "threeZone", label: "三区比", defaultRatio: "1:1:4" },
    { code: "zone1Count", label: "一区个数", defaultRatio: "2" },
    { code: "zone2Count", label: "二区个数", defaultRatio: "2" },
    { code: "zone3Count", label: "三区个数", defaultRatio: "1" },
  ];

const BLUE_FEATURES: { code: PatternFeature; label: string; defaultRatio: string }[] =
  [
    { code: "blueOddEven", label: "奇偶", defaultRatio: "奇" },
    { code: "blueBigSmall", label: "大小", defaultRatio: "大" },
    { code: "blueBigSmallOddEven", label: "大小奇偶", defaultRatio: "大奇" },
    { code: "blueRatio012", label: "012路", defaultRatio: "0路" },
  ];

const SAMPLE_OPTIONS = [30, 50, 100];

function ratioButtonLabel(feature: PatternFeature, ratio: string) {
  if (
    feature === "zone1Count" ||
    feature === "zone2Count" ||
    feature === "zone3Count"
  ) {
    return `${ratio}个`;
  }
  return ratio;
}

export default function PatternTrendPage() {
  const [ballGroup, setBallGroup] = useState<BallGroup>("red");
  const [feature, setFeature] = useState<PatternFeature>("oddEven");
  const [ratio, setRatio] = useState("1:5");
  const [sampleSize, setSampleSize] = useState(100);
  const [data, setData] = useState<PatternTrendVo | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const features = ballGroup === "red" ? RED_FEATURES : BLUE_FEATURES;

  const load = useCallback(
    async (feat: PatternFeature, r: string, size: number) => {
      setLoading(true);
      setError(null);
      try {
        const result = await fetchPatternTrend(feat, r, size);
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
    void load(feature, ratio, sampleSize);
  }, [feature, load, ratio, sampleSize]);

  const switchGroup = (group: BallGroup) => {
    const first = group === "red" ? RED_FEATURES[0] : BLUE_FEATURES[0];
    setBallGroup(group);
    setFeature(first.code);
    setRatio(first.defaultRatio);
  };

  const ratios = data?.ratioOptions.map((o) => o.ratio) ?? [ratio];

  return (
    <main className="page">
      <header className="header">
        <h1>形态遗漏与超额指数</h1>
        <p className="sub">
          指数 = 实际出现次数 − 理论出现次数（n × p）；命中 +(1-p)，未命中 −p
        </p>
      </header>

      <div className="type-tabs">
        <button
          type="button"
          className={`type-tab ${ballGroup === "red" ? "active" : ""}`}
          onClick={() => switchGroup("red")}
        >
          红球
        </button>
        <button
          type="button"
          className={`type-tab ${ballGroup === "blue" ? "active" : ""}`}
          onClick={() => switchGroup("blue")}
        >
          蓝球
        </button>
      </div>

      <div className="type-tabs">
        {features.map((item) => (
          <button
            key={item.code}
            type="button"
            className={`type-tab ${feature === item.code ? "active" : ""}`}
            onClick={() => {
              setFeature(item.code);
              setRatio(item.defaultRatio);
            }}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="type-tabs ratio-tabs">
        {ratios.map((r) => {
          const opt = data?.ratioOptions.find((o) => o.ratio === r);
          return (
            <button
              key={r}
              type="button"
              className={`type-tab ${ratio === r ? "active" : ""}`}
              onClick={() => setRatio(r)}
            >
              {ratioButtonLabel(feature, r)}
              {opt != null ? ` (${opt.hitCount})` : ""}
            </button>
          );
        })}
      </div>

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

      {loading && <div className="status">加载中...</div>}
      {error && <div className="status error">{error}</div>}
      {!loading && !error && data && <PatternTrendCharts data={data} />}

      <div className="info-bar">
        遗漏走势为圆点折线；趋势图按命中红 / 未命中绿分段着色。
        {ballGroup === "red"
          ? " 质合比按走势图口径：01 计为质数。"
          : " 蓝球 1-16：大小 1-8 小 / 9-16 大；012路 0路 5 个、1路 6 个、2路 5 个。"}
        {data
          ? `　当前 p=${(data.stats.theoreticalProb * 100).toFixed(2)}%`
          : ""}
      </div>
    </main>
  );
}
