import type { FeatureStatsVo } from "@/types/feature-stats";
import type { LotteryAnalysisResp } from "@/types/llm-analysis";
import type { LotteryAdjustResp, AdjustHistoryFile } from "@/types/llm-recommend";
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

export async function fetchLlmRecommend(
  count = 2,
  isTopN = false,
): Promise<LotteryAdjustResp> {
  const res = await fetch(`${API_BASE}/api/llm/adjust/${count}/${isTopN}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<LotteryAdjustResp>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "LLM 推荐失败");
  }
  return json.data;
}

export async function fetchLlmRecommendHistory(
  limit = 20,
): Promise<AdjustHistoryFile[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  const res = await fetch(`${API_BASE}/api/llm/adjust/history?${params}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<AdjustHistoryFile[]>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "获取推荐记录失败");
  }
  return json.data;
}

export async function fetchLlmRecommendHistoryDetail(
  fileName: string,
): Promise<LotteryAdjustResp> {
  const res = await fetch(
    `${API_BASE}/api/llm/adjust/history/${encodeURIComponent(fileName)}`,
    { cache: "no-store" },
  );
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<LotteryAdjustResp>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "读取推荐详情失败");
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
