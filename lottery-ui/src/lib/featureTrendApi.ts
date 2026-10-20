import type { ApiResult } from "@/types/api-result";
import type { FeatureTrendDetail, FeatureTrendItem, FeatureTrendPlan, FeatureTrendSheet, FeatureTrendSummary } from "@/types/feature-trend-forecast";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8866";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}/api/llm/feature-trends${path}`, { cache: "no-store", ...init });
  const result = await response.json() as ApiResult<T>;
  if (!response.ok || result.code !== 200 || result.data == null) {
    throw new Error(result.message || `请求失败（HTTP ${response.status}）`);
  }
  return result.data;
}

export function fetchFeatureTrendPlan(signal: AbortSignal, endPeriod?: string) {
  const query = endPeriod ? `?${new URLSearchParams({ endPeriod })}` : "";
  return request<FeatureTrendPlan>(`/input${query}`, { signal });
}

export function fetchFeatureTrendHistory(before: string | undefined, signal: AbortSignal) {
  const params = new URLSearchParams({ limit: "12" });
  if (before) params.set("before", before);
  return request<string[]>(`?${params}`, { signal });
}

export function fetchFeatureTrendSummaries(period: string, signal: AbortSignal) {
  return request<FeatureTrendSummary[]>(`/${encodeURIComponent(period)}`, { signal });
}

export function fetchFeatureTrendDetail(period: string, code: string, signal: AbortSignal) {
  return request<FeatureTrendDetail>(`/${encodeURIComponent(period)}/${encodeURIComponent(code)}`, { signal });
}

export function analyzeFeatureTrend(item: FeatureTrendItem, sheets: FeatureTrendSheet[], signal: AbortSignal) {
  return request<FeatureTrendSummary>("", {
    method: "POST", headers: { "Content-Type": "application/json" },
    signal: AbortSignal.any([signal, AbortSignal.timeout(150_000)]),
    body: JSON.stringify({
      featureCode: item.code, input: item.input, images: sheets.map(({ code, dataUrl }) => ({ code, dataUrl })),
    }),
  });
}
