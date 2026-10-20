import type { DrawTrend, DrawTrendGroup, DrawTrendSeries } from "@/types/draw-trend";
import type { FeatureStatsVo } from "@/types/feature-stats";

function series(code: string, label: string, values: (number | null)[]): DrawTrendSeries {
  const samples = values.filter((value): value is number => value !== null);
  const total = samples.reduce((sum, value) => sum + value, 0);
  return { code, label, values, total, sampleCount: samples.length, average: samples.length ? total / samples.length : 0 };
}

export function mergeFeatureStats(trends: DrawTrend, stats: FeatureStatsVo): DrawTrend {
  const indices = new Map(stats.periods.map((period, index) => [period, index]));
  const align = (values: (number | null)[]) => trends.periods.map((period) => {
    const index = indices.get(period);
    return index === undefined ? null : values[index] ?? null;
  });
  const deltas = align(stats.sumDeltaValues);
  const deltaSamples = deltas.filter((value): value is number => value !== null);
  const primeCounts = align(stats.primeCounts);
  const additional: DrawTrendGroup[] = [
    {
      code: "sumDelta", label: "和值增长差值", description: "本期红球和值减去上一期和值；展示区间首期无对照，留空且不计入均值。",
      occurrence: false, min: Math.min(0, ...deltaSamples), max: Math.max(1, ...deltaSamples),
      series: [series("sumDelta", "和值增长差值", deltas)],
    },
    {
      code: "primeRatio", label: "质合比", description: "沿用原质合分类口径；比例顺序为质:合，0:6 至 6:0 共 7 种情况，每种情况独立绘图。",
      occurrence: true, min: 0, max: 1,
      series: Array.from({ length: 7 }, (_, prime) => {
        const ratio = `${prime}:${6 - prime}`;
        return series(ratio, ratio, primeCounts.map((count) => count === null ? null : Number(count === prime)));
      }),
      detailLabels: trends.periods.map((period) => {
        const index = indices.get(period);
        return index === undefined ? "—" : stats.primeRatios[index] ?? "—";
      }),
    },
    {
      code: "tailCounts", label: "尾数分布", description: "红球号码除以 10 的余数为尾数；0尾至9尾分别统计，每期各尾数个数之和为6。",
      occurrence: false, min: 0, max: 6,
      series: Array.from({ length: 10 }, (_, tail) => series(`tail${tail}`, `${tail}尾个数`, align(stats.tailCounts[tail] ?? []))),
    },
  ];
  return { ...trends, redFeatures: [...trends.redFeatures, ...additional] };
}
