import type { FeatureStatsVo } from "@/types/feature-stats";
import type { LotteryAnalysisResp } from "@/types/llm-analysis";
import type { PatternFeature, PatternTrendVo } from "@/types/pattern-trend";
import type { ApiResult, BallType, TrendAnalysisVo } from "@/types/trend";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8866";

function withEndPeriod(params: URLSearchParams, endPeriod?: string) {
  const p = endPeriod?.trim();
  if (p) {
    params.set("endPeriod", p);
  }
}

export async function fetchTrend(
  ballType: BallType,
  ball: number,
  sampleSize = 100,
  endPeriod?: string,
): Promise<TrendAnalysisVo> {
  const params = new URLSearchParams({
    ballType,
    ball: String(ball),
    sampleSize: String(sampleSize),
  });
  withEndPeriod(params, endPeriod);
  const res = await fetch(`${API_BASE}/api/history/trend?${params}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<TrendAnalysisVo>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "趋势分析失败");
  }
  return json.data;
}

export async function fetchFeatureStats(
  sampleSize = 100,
  endPeriod?: string,
): Promise<FeatureStatsVo> {
  const params = new URLSearchParams({
    sampleSize: String(sampleSize),
  });
  withEndPeriod(params, endPeriod);
  const res = await fetch(`${API_BASE}/api/history/feature-stats?${params}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<FeatureStatsVo>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "形态统计失败");
  }
  return json.data;
}

export async function fetchAnalyzeLatest(
  sampleSize = 100,
): Promise<LotteryAnalysisResp> {
  const params = new URLSearchParams({
    sampleSize: String(sampleSize),
  });
  const res = await fetch(`${API_BASE}/api/llm/analyze/latest?${params}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<LotteryAnalysisResp>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "特征分析失败");
  }
  return json.data;
}

export async function fetchPatternTrend(
  feature: PatternFeature,
  ratio: string,
  sampleSize = 100,
  endPeriod?: string,
): Promise<PatternTrendVo> {
  const params = new URLSearchParams({
    feature,
    ratio,
    sampleSize: String(sampleSize),
  });
  withEndPeriod(params, endPeriod);
  const res = await fetch(`${API_BASE}/api/history/pattern-trend?${params}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<PatternTrendVo>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "形态趋势分析失败");
  }
  return json.data;
}
