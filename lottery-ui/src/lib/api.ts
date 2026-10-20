import type { FeatureStatsVo } from "@/types/feature-stats";
import type { DrawTrend } from "@/types/draw-trend";
import type { DrawReview } from "@/types/draw-review";
import type { LotteryAnalysisResp } from "@/types/llm-analysis";
import type {
  LotteryAdjustResp,
  LlmRecommendTask,
} from "@/types/llm-recommend";
import type { PredictFileRecord } from "@/types/predict-file-record";
import type { ApiResult } from "@/types/api-result";
import type { NumberTrend } from "@/types/number-trend";
import type { ZoneTrendImage, ZoneTrendSnapshot } from "@/types/zone-trend-analysis";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8866";

export async function fetchDrawReview(period?: string, signal?: AbortSignal): Promise<DrawReview | null> {
  const params = period ? "?" + new URLSearchParams({ period }) : "";
  const response = await fetch(`${API_BASE}/api/history/reviews${params}`, { cache: "no-store", signal });
  if (response.status === 404) return null;
  const result = await response.json() as ApiResult<DrawReview>;
  if (!response.ok || result.code !== 200 || !result.data) {
    throw new Error(result.message || "复盘加载失败");
  }
  return result.data;
}

export async function syncDrawHistory(): Promise<void> {
  const response = await fetch(`${API_BASE}/api/history`, { method: "POST" });
  const result = await response.json() as ApiResult<null>;
  if (!response.ok || result.code !== 200) throw new Error(result.message || "同步开奖失败");
}

export async function analyzeZoneTrends(snapshot: ZoneTrendSnapshot, images: ZoneTrendImage[], signal: AbortSignal): Promise<LotteryAnalysisResp> {
  const response = await fetch(`${API_BASE}/api/llm/zone-trends`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ snapshot, images }),
    signal: AbortSignal.any([signal, AbortSignal.timeout(150_000)]),
  });
  const result = await response.json() as ApiResult<LotteryAnalysisResp>;
  if (!response.ok || result.code !== 200 || !result.data?.zoneTrendAnalysis) {
    throw new Error(result.message || `走势图分析请求失败：HTTP ${response.status}`);
  }
  return result.data;
}

function withEndPeriod(params: URLSearchParams, endPeriod?: string) {
  const p = endPeriod?.trim();
  if (p) {
    params.set("endPeriod", p);
  }
}

export async function fetchDrawTrends(
  sampleSize: number,
  endPeriod: string,
  signal?: AbortSignal,
): Promise<DrawTrend> {
  const params = new URLSearchParams({ sampleSize: String(sampleSize) });
  withEndPeriod(params, endPeriod);
  const response = await fetch(`${API_BASE}/api/history/draw-trends?${params}`, {
    cache: "no-store",
    signal,
  });
  if (!response.ok) throw new Error(`历史开奖趋势请求失败：HTTP ${response.status}`);
  const result = (await response.json()) as ApiResult<DrawTrend>;
  if (result.code !== 200 || !result.data) {
    throw new Error(result.message || "历史开奖趋势加载失败");
  }
  return result.data;
}

export async function fetchNumberTrends(sampleSize: number, endPeriod: string, signal?: AbortSignal): Promise<NumberTrend> {
  const params = new URLSearchParams({ sampleSize: String(sampleSize) });
  withEndPeriod(params, endPeriod);
  const res = await fetch(`${API_BASE}/api/history/number-trends?${params}`, {
    cache: "no-store",
    signal,
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<NumberTrend>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "号码走势图加载失败");
  }
  return json.data;
}

export async function fetchFeatureStats(
  sampleSize = 100,
  endPeriod?: string,
  signal?: AbortSignal,
): Promise<FeatureStatsVo> {
  const params = new URLSearchParams({
    sampleSize: String(sampleSize),
  });
  withEndPeriod(params, endPeriod);
  const res = await fetch(`${API_BASE}/api/history/feature-stats?${params}`, {
    cache: "no-store",
    signal,
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
  sampleSize = 30,
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

/** 提交 LLM 推荐异步任务：立即返回 taskId。 */
export async function submitLlmRecommendTask(useNumberTrend = false): Promise<LlmRecommendTask> {
  const res = await fetch(`${API_BASE}/api/llm/recommend-task`, {
    method: "POST",
    cache: "no-store",
    ...(useNumberTrend && {
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ useNumberTrend }),
    }),
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<LlmRecommendTask>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "下发推荐任务失败");
  }
  return json.data;
}

export async function fetchLlmRecommendTask(taskId: string): Promise<LlmRecommendTask> {
  const res = await fetch(
    `${API_BASE}/api/llm/recommend-task/${encodeURIComponent(taskId)}`,
    { cache: "no-store" },
  );
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<LlmRecommendTask>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "查询推荐任务失败");
  }
  return json.data;
}

export async function fetchRunningLlmRecommendTask(): Promise<LlmRecommendTask | null> {
  const res = await fetch(`${API_BASE}/api/llm/recommend-task/running`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<LlmRecommendTask | null>;
  if (json.code !== 200) {
    throw new Error(json.message || "查询进行中任务失败");
  }
  return json.data ?? null;
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

/**
 * 预测/特征结果文件历史列表（DB 驱动，按创建时间倒序）。
 * @param type 类型：RECOMMEND=号码推荐，ANALYSIS=特征预测；为空查全部
 */
export async function fetchPredictFileHistory(
  type?: string,
  limit = 20,
): Promise<PredictFileRecord[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (type) {
    params.set("type", type);
  }
  const res = await fetch(`${API_BASE}/api/llm/file-history?${params}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<PredictFileRecord[]>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "获取历史记录失败");
  }
  return json.data;
}

/** 特征预测结果详情：按文件名回看。 */
export async function fetchAnalysisHistoryDetail(
  fileName: string,
): Promise<LotteryAnalysisResp> {
  const res = await fetch(
    `${API_BASE}/api/llm/analyze/history/${encodeURIComponent(fileName)}`,
    { cache: "no-store" },
  );
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<LotteryAnalysisResp>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "读取特征预测详情失败");
  }
  return json.data;
}
