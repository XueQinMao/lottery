"use client";

import { fetchDrawTrends, fetchFeatureStats } from "@/lib/api";
import { mergeFeatureStats } from "@/lib/mergeFeatureStats";
import { useHistoryQuery } from "@/hooks/useHistoryQuery";

async function fetchHistoryTrends(sampleSize: number, endPeriod: string, signal: AbortSignal) {
  const [trends, stats] = await Promise.allSettled([
    fetchDrawTrends(sampleSize, endPeriod, signal),
    fetchFeatureStats(sampleSize, endPeriod, signal),
  ]);
  if (trends.status === "rejected") throw trends.reason;
  if (!trends.value.periods.length) return trends.value;
  if (stats.status === "rejected") throw stats.reason;
  return mergeFeatureStats(trends.value, stats.value);
}

export function useDrawTrends(sampleSize: number, endPeriod: string) {
  return useHistoryQuery(fetchHistoryTrends, sampleSize, endPeriod);
}
