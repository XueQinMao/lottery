import type { FeatureStatsVo } from "@/types/feature-stats";
import type { LotteryAnalysisResp } from "@/types/llm-analysis";
import type {
  LotteryAdjustResp,
  AdjustHistoryFile,
  LlmRecommendTask,
  LlmRecommendTaskMode,
} from "@/types/llm-recommend";
import type { PredictFileRecord } from "@/types/predict-file-record";
import type { PatternFeature, PatternTrendVo } from "@/types/pattern-trend";
import type { ColdHotTrendVo } from "@/types/cold-hot-trend";
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

/** 特征推荐：POST /adjust，不传 drawRecords，仅按 count 生成。 */
export async function fetchLlmFeatureRecommend(
  count = 2,
  userRequirement?: string,
): Promise<LotteryAdjustResp> {
  const body: { count: number; userRequirement?: string } = { count };
  const hint = userRequirement?.trim();
  if (hint) {
    body.userRequirement = hint;
  }
  const res = await fetch(`${API_BASE}/api/llm/adjust`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
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

/** 缓存调优：GET /adjust/{count}/{isTopN}。true=评分最高，false=随机抽取。 */
export async function fetchLlmCacheRecommend(
  count = 2,
  isTopN = false,
  userRequirement?: string,
): Promise<LotteryAdjustResp> {
  const params = new URLSearchParams();
  const hint = userRequirement?.trim();
  if (hint) {
    params.set("userRequirement", hint);
  }
  const query = params.toString();
  const res = await fetch(
    `${API_BASE}/api/llm/adjust/${count}/${isTopN}${query ? `?${query}` : ""}`,
    {
      cache: "no-store",
    },
  );
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<LotteryAdjustResp>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "LLM 推荐失败");
  }
  return json.data;
}

/** 提交 LLM 推荐异步任务：立即返回 taskId。 */
export async function submitLlmRecommendTask(input: {
  mode: LlmRecommendTaskMode;
  count?: number;
  isTopN?: boolean;
  userRequirement?: string;
}): Promise<LlmRecommendTask> {
  const body: {
    mode: LlmRecommendTaskMode;
    count?: number;
    isTopN?: boolean;
    userRequirement?: string;
  } = { mode: input.mode, count: input.count };
  if (input.mode === "CACHE") {
    body.isTopN = input.isTopN ?? true;
  }
  const hint = input.userRequirement?.trim();
  if (hint) {
    body.userRequirement = hint;
  }
  const res = await fetch(`${API_BASE}/api/llm/recommend-task`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    cache: "no-store",
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

export async function fetchColdHotTrend(
  sampleSize = 100,
  endPeriod?: string,
): Promise<ColdHotTrendVo> {
  const params = new URLSearchParams({
    sampleSize: String(sampleSize),
  });
  withEndPeriod(params, endPeriod);
  const res = await fetch(`${API_BASE}/api/history/cold-hot-trend?${params}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`请求失败: HTTP ${res.status}`);
  }
  const json = (await res.json()) as ApiResult<ColdHotTrendVo>;
  if (json.code !== 200 || !json.data) {
    throw new Error(json.message || "冷温热趋势分析失败");
  }
  return json.data;
}
