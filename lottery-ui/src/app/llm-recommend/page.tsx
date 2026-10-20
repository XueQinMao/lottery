"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Sparkle } from "@phosphor-icons/react";
import RecommendHistory, {
  HISTORY_MAX_LIMIT,
  HISTORY_PAGE_SIZE,
} from "@/components/RecommendHistory";
import RecommendReport from "@/components/RecommendReport";
import {
  fetchPredictFileHistory,
  fetchLlmRecommendHistoryDetail,
  submitLlmRecommendTask,
  fetchLlmRecommendTask,
  fetchRunningLlmRecommendTask,
} from "@/lib/api";
import type {
  LotteryAdjustResp,
  LlmRecommendTask,
  LlmRecommendTaskPhase,
} from "@/types/llm-recommend";
import type { PredictFileRecord } from "@/types/predict-file-record";
import { PREDICT_FILE_TYPE } from "@/types/predict-file-record";

const POLL_INTERVAL_MS = 2000;
const POLL_TIMEOUT_MS = 5 * 60 * 1000;

export default function LlmRecommendPage() {
  const [data, setData] = useState<LotteryAdjustResp | null>(null);
  const [files, setFiles] = useState<PredictFileRecord[]>([]);
  const [fileLimit, setFileLimit] = useState(HISTORY_PAGE_SIZE);
  const [hasMore, setHasMore] = useState(false);
  const [activeFileName, setActiveFileName] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [useNumberTrend, setUseNumberTrend] = useState(false);
  const [phase, setPhase] = useState<LlmRecommendTaskPhase>();
  const [listLoading, setListLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const [booting, setBooting] = useState(true);
  const skipAutoOpenRef = useRef(false);
  const requestIdRef = useRef(0);
  const pollTimerRef = useRef<number | null>(null);
  const pollStartedAtRef = useRef(0);
  const pollInFlightRef = useRef(false);
  const startPollingRef = useRef<(task: LlmRecommendTask) => void>(() => {});

  const loadFiles = useCallback(async (silent = false, limit = HISTORY_PAGE_SIZE) => {
    if (!silent) {
      setListLoading(true);
    }
    setListError(null);
    try {
      const result = await fetchPredictFileHistory(PREDICT_FILE_TYPE.RECOMMEND, limit);
      setFiles(result);
      setHasMore(result.length >= limit && limit < HISTORY_MAX_LIMIT);
      return result;
    } catch (e) {
      setFiles([]);
      setHasMore(false);
      setListError(e instanceof Error ? e.message : "获取推荐记录失败");
      return [];
    } finally {
      if (!silent) {
        setListLoading(false);
      }
    }
  }, []);

  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) {
      return;
    }
    setLoadingMore(true);
    try {
      const nextLimit = Math.min(fileLimit + HISTORY_PAGE_SIZE, HISTORY_MAX_LIMIT);
      await loadFiles(true, nextLimit);
      setFileLimit(nextLimit);
    } finally {
      setLoadingMore(false);
    }
  }, [fileLimit, hasMore, loadFiles, loadingMore]);

  const openHistory = useCallback(
    async (file: PredictFileRecord, options?: { scroll?: boolean }) => {
      const requestId = ++requestIdRef.current;
      setDetailLoading(true);
      setError(null);
      setActiveFileName(file.fileName);
      try {
        const result = await fetchLlmRecommendHistoryDetail(file.fileName);
        if (requestId !== requestIdRef.current) {
          return;
        }
        setData(result);
        if (options?.scroll === false) {
          return;
        }
        const report = document.getElementById("recommend-report");
        const reduceMotion = window.matchMedia(
          "(prefers-reduced-motion: reduce)",
        ).matches;
        report?.scrollIntoView({
          behavior: reduceMotion ? "auto" : "smooth",
          block: "start",
        });
      } catch (e) {
        if (requestId !== requestIdRef.current) {
          return;
        }
        setData(null);
        setError(e instanceof Error ? e.message : "读取推荐详情失败");
      } finally {
        if (requestId === requestIdRef.current) {
          setDetailLoading(false);
        }
      }
    },
    [],
  );

  const stopPolling = useCallback(() => {
    if (pollTimerRef.current != null) {
      window.clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    pollInFlightRef.current = false;
  }, []);

  const applyFinishedTask = useCallback(
    async (task: LlmRecommendTask) => {
      stopPolling();
      if (task.status === "FAILED") {
        setLoading(false);
        setError(task.message || "推荐失败");
        return;
      }
      setFileLimit(HISTORY_PAGE_SIZE);
      const nextFiles = await loadFiles(true, HISTORY_PAGE_SIZE);
      const match =
        nextFiles.find((item) => item.fileName === task.fileName) ??
        (task.fileName
          ? {
              id: 0,
              type: PREDICT_FILE_TYPE.RECOMMEND,
              fileName: task.fileName,
            }
          : nextFiles[0]);
      if (!match) {
        setLoading(false);
        setError("推荐已完成，但未找到结果文件");
        return;
      }
      await openHistory(match);
      setLoading(false);
    },
    [loadFiles, openHistory, stopPolling],
  );

  const startPolling = useCallback(
    (initialTask: LlmRecommendTask) => {
      stopPolling();
      setLoading(true);
      setError(null);
      setPhase(initialTask.phase);
      pollStartedAtRef.current = Date.now();

      const tick = async () => {
        if (pollInFlightRef.current) {
          return;
        }
        if (Date.now() - pollStartedAtRef.current >= POLL_TIMEOUT_MS) {
          stopPolling();
          setLoading(false);
          setError("等待超时，任务可能仍在后台执行，请稍后从最近推荐查看");
          return;
        }
        pollInFlightRef.current = true;
        try {
          const task = await fetchLlmRecommendTask(initialTask.taskId);
          setPhase(task.phase);
          if (task.status === "SUCCESS" || task.status === "FAILED") {
            await applyFinishedTask(task);
          }
        } catch (e) {
          if (Date.now() - pollStartedAtRef.current >= POLL_TIMEOUT_MS) {
            stopPolling();
            setLoading(false);
            setError(e instanceof Error ? e.message : "查询推荐任务失败");
          }
        } finally {
          pollInFlightRef.current = false;
        }
      };

      void tick();
      pollTimerRef.current = window.setInterval(() => {
        void tick();
      }, POLL_INTERVAL_MS);
    },
    [applyFinishedTask, stopPolling],
  );

  startPollingRef.current = startPolling;

  useEffect(() => {
    let cancelled = false;
    const init = async () => {
      try {
        let running: LlmRecommendTask | null = null;
        try {
          running = await fetchRunningLlmRecommendTask();
        } catch {
          running = null;
        }
        if (cancelled) {
          return;
        }
        const active =
          running &&
          (running.status === "PENDING" || running.status === "RUNNING");
        if (active && running) {
          skipAutoOpenRef.current = true;
          setUseNumberTrend(running.useNumberTrend === true);
          startPollingRef.current(running);
        }
        const result = await loadFiles();
        const first = result[0];
        if (cancelled || skipAutoOpenRef.current || !first) {
          return;
        }
        await openHistory(first, { scroll: false });
      } finally {
        if (!cancelled) {
          setBooting(false);
        }
      }
    };
    void init();
    return () => {
      cancelled = true;
      stopPolling();
    };
  }, [loadFiles, openHistory, stopPolling]);

  const generate = useCallback(
    async () => {
      skipAutoOpenRef.current = true;
      requestIdRef.current += 1;
      setLoading(true);
      setPhase(undefined);
      setError(null);
      setDetailLoading(false);
      try {
        const task = await submitLlmRecommendTask(useNumberTrend);
        if (task.status === "SUCCESS" || task.status === "FAILED") {
          await applyFinishedTask(task);
          return;
        }
        startPolling(task);
      } catch (e) {
        stopPolling();
        setLoading(false);
        setError(e instanceof Error ? e.message : "下发推荐任务失败");
      }
    },
    [applyFinishedTask, startPolling, stopPolling, useNumberTrend],
  );

  const review = useCallback(
    (file: PredictFileRecord) => {
      skipAutoOpenRef.current = true;
      return openHistory(file);
    },
    [openHistory],
  );

  const progressText = phase === "ANALYZING_NUMBER_TREND"
    ? "读取下期红球候选…"
    : phase === "GENERATING_RECOMMENDATION" ? "生成推荐中…" : "提交推荐任务中…";

  return (
    <div className="page recommend-page">
      <div className="recommend-workspace">
        <div className="recommend-main">
          <section className="forecast-hero" aria-labelledby="recommend-title">
            <div className="recommend-heading">
              <h1 id="recommend-title">LLM 推荐</h1>
              <p className="muted">结合历史特征与分析结果，探索下一期的号码组合。</p>
            </div>
            <div className="recommend-controls">
              <label className="recommend-trend-option">
                <input type="checkbox" checked={useNumberTrend} disabled={booting || loading}
                  onChange={(event) => setUseNumberTrend(event.target.checked)} />
                参考走势图红球候选
              </label>
              <button type="button" className="primary-btn forecast-run" onClick={() => void generate()}
                disabled={booting || loading} aria-busy={loading}>
                <Sparkle size={16} weight="fill" aria-hidden="true" />
                {loading ? (useNumberTrend ? progressText : "生成中…") : "生成推荐"}
              </button>
            </div>
          </section>
          {loading && !detailLoading && (
            <div className="status" role="status" aria-atomic="true">
              {useNumberTrend ? progressText : "任务已下发，请耐心等待"}
            </div>
          )}
          {detailLoading && (
            <div className="status" role="status" aria-atomic="true">
              正在读取推荐详情...
            </div>
          )}
          {error && (
            <div className="status error" role="alert">
              {error}
            </div>
          )}
          {!loading && !detailLoading && !error && data && (
            <div id="recommend-report">
              <RecommendReport key={activeFileName} data={data} />
            </div>
          )}
          {!loading && (booting || listLoading) && !data && !error && !detailLoading && (
            <div className="status" role="status" aria-atomic="true">
              正在加载最近推荐...
            </div>
          )}
          {!loading && !booting && !listLoading && !detailLoading && !error && !data && (
            <div className="status">
              点击「生成推荐」，生成 5 组单式和 1 组复式。
            </div>
          )}
        </div>
        <RecommendHistory
          files={files}
          activeFileName={activeFileName}
          loading={listLoading}
          listError={listError}
          disabled={loading || detailLoading}
          hasMore={hasMore}
          loadingMore={loadingMore}
          onSelect={(file) => void review(file)}
          onLoadMore={() => void loadMore()}
        />
      </div>
    </div>
  );
}
