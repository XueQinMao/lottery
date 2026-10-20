"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  analyzeFeatureTrend, fetchFeatureTrendHistory, fetchFeatureTrendPlan, fetchFeatureTrendSummaries,
} from "@/lib/featureTrendApi";
import { exportFeatureTrendImages } from "@/lib/featureTrendCharts";
import type { FeatureTrendItem, FeatureTrendPlan, FeatureTrendSummary } from "@/types/feature-trend-forecast";

const PARALLEL_FEATURES = 3;
const HISTORY_PAGE_SIZE = 12;

function message(failure: unknown, fallback: string) {
  if (failure instanceof Error && failure.name === "TimeoutError") return "模型响应超时，请重试";
  return failure instanceof Error ? failure.message : fallback;
}

async function submitFeature(item: FeatureTrendItem, signal: AbortSignal) {
  const sheets = await exportFeatureTrendImages(item.input, item.blue, signal);
  signal.throwIfAborted();
  const result = await analyzeFeatureTrend(item, sheets, signal);
  signal.throwIfAborted();
  return result;
}

export function useFeatureTrendForecast() {
  const [plan, setPlan] = useState<FeatureTrendPlan | null>(null);
  const [historicalPlan, setHistoricalPlan] = useState<FeatureTrendPlan | null>(null);
  const [temperatureError, setTemperatureError] = useState("");
  const [period, setPeriod] = useState("");
  const [summaries, setSummaries] = useState<FeatureTrendSummary[]>([]);
  const [history, setHistory] = useState<string[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [historyError, setHistoryError] = useState("");
  const [historyLoading, setHistoryLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [reviewing, setReviewing] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [active, setActive] = useState<string[]>([]);
  const request = useRef<AbortController | null>(null);
  const historyRequest = useRef<AbortController | null>(null);

  const refreshHistory = useCallback(async (before?: string) => {
    historyRequest.current?.abort();
    const controller = new AbortController();
    historyRequest.current = controller;
    setHistoryLoading(true);
    setHistoryError("");
    try {
      const page = await fetchFeatureTrendHistory(before, controller.signal);
      if (controller.signal.aborted) return;
      setHistory((previous) => before ? [...new Set([...previous, ...page])] : page);
      setHasMore(page.length === HISTORY_PAGE_SIZE);
    } catch (failure) {
      if (!controller.signal.aborted) setHistoryError(message(failure, "历史推算加载失败"));
    } finally {
      if (!controller.signal.aborted) setHistoryLoading(false);
    }
  }, []);

  const run = useCallback(async () => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    const { signal } = controller;
    setLoading(true);
    setError("");
    setErrors({});
    setActive([]);
    try {
      const latest = await fetchFeatureTrendPlan(signal);
      const saved = await fetchFeatureTrendSummaries(latest.targetPeriod, signal);
      signal.throwIfAborted();
      const existing = saved.filter((item) => item.sourcePeriod === latest.sourcePeriod);
      setPlan(latest);
      setHistoricalPlan(null);
      setTemperatureError("");
      setPeriod(latest.targetPeriod);
      setSummaries(existing);
      const completed = new Set(existing.map((item) => item.featureCode));
      const queue = latest.items.filter((item) => !completed.has(item.code));
      const worker = async () => {
        while (queue.length && !signal.aborted) {
          const item = queue.shift();
          if (!item) break;
          setActive((previous) => [...previous, item.code]);
          try {
            const result = await submitFeature(item, signal);
            setSummaries((previous) => [...previous, result]);
          } catch (failure) {
            if (!signal.aborted) setErrors((previous) => ({ ...previous, [item.code]: message(failure, "特征推算失败") }));
          } finally {
            if (!signal.aborted) setActive((previous) => previous.filter((code) => code !== item.code));
          }
        }
      };
      await Promise.all(Array.from({ length: Math.min(PARALLEL_FEATURES, queue.length) }, worker));
      if (!signal.aborted) void refreshHistory();
    } catch (failure) {
      if (!signal.aborted) setError(message(failure, "分析加载失败"));
    } finally {
      if (!signal.aborted) setLoading(false);
    }
  }, [refreshHistory]);

  useEffect(() => {
    void run();
    void refreshHistory();
    return () => {
      request.current?.abort();
      historyRequest.current?.abort();
    };
  }, [run, refreshHistory]);

  const review = async (selected: string) => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setReviewing(true);
    setError("");
    setErrors({});
    try {
      const result = await fetchFeatureTrendSummaries(selected, controller.signal);
      if (controller.signal.aborted) return;
      setPeriod(selected);
      setSummaries(result);
      setHistoricalPlan(null);
      setTemperatureError("");
      const source = result[0]?.sourcePeriod;
      if (source && selected !== plan?.targetPeriod) {
        try {
          const saved = await fetchFeatureTrendPlan(controller.signal, source);
          if (!controller.signal.aborted && saved.targetPeriod === selected) setHistoricalPlan(saved);
        } catch (failure) {
          if (!controller.signal.aborted) setTemperatureError(message(failure, "冷温热号码加载失败"));
        }
      }
    } catch (failure) {
      if (!controller.signal.aborted) setError(message(failure, "推算记录加载失败"));
    } finally {
      if (!controller.signal.aborted) setReviewing(false);
    }
  };

  const retry = async (code: string) => {
    if (loading || reviewing || active.length || summaries.some((item) => item.featureCode === code)) return;
    const source = plan?.targetPeriod === period ? plan.sourcePeriod : summaries[0]?.sourcePeriod;
    if (!source) return;
    const controller = new AbortController();
    request.current = controller;
    const { signal } = controller;
    setActive([code]);
    setErrors((previous) => {
      const next = { ...previous };
      delete next[code];
      return next;
    });
    try {
      const prepared = plan?.sourcePeriod === source ? plan : historicalPlan;
      const input = prepared?.sourcePeriod === source ? prepared : await fetchFeatureTrendPlan(signal, source);
      const item = input.items.find((entry) => entry.code === code);
      if (input.targetPeriod !== period || !item) throw new Error("本期样本已变化，请重新加载后重试");
      const result = await submitFeature(item, signal);
      if (result.targetPeriod !== period) throw new Error("目标期号已变化，请重新加载记录");
      setSummaries((previous) => [...previous.filter((entry) => entry.featureCode !== code), result]);
      void refreshHistory();
    } catch (failure) {
      if (!signal.aborted) setErrors((previous) => ({ ...previous, [code]: message(failure, "特征推算失败") }));
    } finally {
      if (!signal.aborted) setActive([]);
    }
  };

  return {
    plan, period, summaries, history, hasMore, historyError, historyLoading,
    temperature: (period === plan?.targetPeriod ? plan : historicalPlan)?.coldHotAnalysis,
    temperatureError,
    loading, reviewing, error, errors, active, run, review, refreshHistory, retry,
  };
}
