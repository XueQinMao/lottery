import type { ApiResult, BallType, TrendAnalysisVo } from "@/types/trend";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8866";

export async function fetchTrend(
  ballType: BallType,
  ball: number,
  sampleSize = 100,
): Promise<TrendAnalysisVo> {
  const params = new URLSearchParams({
    ballType,
    ball: String(ball),
    sampleSize: String(sampleSize),
  });
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
