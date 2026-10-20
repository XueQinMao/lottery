"use client";

import { useEffect, useState } from "react";

type HistoryFetcher<T> = (sampleSize: number, endPeriod: string, signal: AbortSignal) => Promise<T>;

export function useHistoryQuery<T>(fetcher: HistoryFetcher<T>, sampleSize: number, endPeriod: string) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    setData(null);
    fetcher(sampleSize, endPeriod, controller.signal)
      .then((result) => { if (!controller.signal.aborted) setData(result); })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "加载失败");
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [fetcher, sampleSize, endPeriod, revision]);

  return { data, loading, error, retry: () => setRevision((value) => value + 1) };
}
