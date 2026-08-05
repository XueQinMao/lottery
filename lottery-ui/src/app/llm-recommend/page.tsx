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
} from "@/types/llm-recommend";
import type { PredictFileRecord } from "@/types/predict-file-record";
import { PREDICT_FILE_TYPE } from "@/types/predict-file-record";

const COUNT_OPTIONS = [1, 2, 3, 5];
const POLL_INTERVAL_MS = 2000;
const POLL_TIMEOUT_MS = 5 * 60 * 1000;

type RecommendMode = "feature" | "cache";

export default function LlmRecommendPage() {
  const [mode, setMode] = useState<RecommendMode>("feature");
  const [count, setCount] = useState(2);
  const [isTopN, setIsTopN] = useState(true);
  const [userRequirement, setUserRequirement] = useState("");
  const [data, setData] = useState<LotteryAdjustResp | null>(null);
  const [files, setFiles] = useState<PredictFileRecord[]>([]);
  const [fileLimit, setFileLimit] = useState(HISTORY_PAGE_SIZE);
  const [hasMore, setHasMore] = useState(false);
  const [activeFileName, setActiveFileName] = useState<string | null>(null);
  const [fromHistory, setFromHistory] = useState(false);
  const [loading, setLoading] = useState(false);
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
  const startPollingRef = useRef<(taskId: string) => void>(() => {});

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
    async (file: PredictFileRecord, options?: { scroll?: boolean; fromHistory?: boolean }) => {
      const requestId = ++requestIdRef.current;
      setDetailLoading(true);
      setError(null);
      setFromHistory(options?.fromHistory !== false);
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
      setLoading(false);
      if (task.status === "FAILED") {
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
        setError("推荐已完成，但未找到结果文件");
        return;
      }
      await openHistory(match, { fromHistory: false });
    },
    [loadFiles, openHistory, stopPolling],
  );

  const startPolling = useCallback(
    (taskId: string) => {
      stopPolling();
      setLoading(true);
      setError(null);
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
          const task = await fetchLlmRecommendTask(taskId);
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
          startPollingRef.current(running.taskId);
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
    async (
      nextMode: RecommendMode,
      size: number,
      topN: boolean,
      extraHint: string,
    ) => {
      skipAutoOpenRef.current = true;
      requestIdRef.current += 1;
      setLoading(true);
      setError(null);
      setFromHistory(false);
      try {
        const task = await submitLlmRecommendTask({
          mode: nextMode === "feature" ? "FEATURE" : "CACHE",
          count: size,
          isTopN: topN,
          userRequirement: extraHint,
        });
        if (task.status === "SUCCESS" || task.status === "FAILED") {
          await applyFinishedTask(task);
          return;
        }
        startPolling(task.taskId);
      } catch (e) {
        stopPolling();
        setLoading(false);
        setError(e instanceof Error ? e.message : "下发推荐任务失败");
      }
    },
    [applyFinishedTask, startPolling, stopPolling],
  );

  const review = useCallback(
    (file: PredictFileRecord) => {
      skipAutoOpenRef.current = true;
      return openHistory(file);
    },
    [openHistory],
  );

  const activeFile = files.find((item) => item.fileName === activeFileName) ?? null;

  return (
    <div className="page recommend-page">
      <div className="recommend-workspace">
        <div className="filter-card recommend-toolbar-card">
          <div className="recommend-toolbar">
            <div className="recommend-toolbar-row">
              <div className="recommend-controls">
              <div className="recommend-control-group">
                <span className="recommend-control-label" id="recommend-mode-label">
                  生成方式
                </span>
                <div
                  className="type-tabs"
                  role="group"
                  aria-labelledby="recommend-mode-label"
                >
                  <button
                    type="button"
                    className={`type-tab ${mode === "feature" ? "active" : ""}`}
                    onClick={() => setMode("feature")}
                    aria-pressed={mode === "feature"}
                  >
                    特征推荐
                  </button>
                  <button
                    type="button"
                    className={`type-tab ${mode === "cache" ? "active" : ""}`}
                    onClick={() => setMode("cache")}
                    aria-pressed={mode === "cache"}
                  >
                    缓存调优
                  </button>
                </div>
              </div>
              <div className="recommend-control-group">
                <span className="recommend-control-label" id="recommend-count-label">
                  组数
                </span>
                <div
                  className="type-tabs"
                  role="group"
                  aria-labelledby="recommend-count-label"
                >
                  {COUNT_OPTIONS.map((n) => (
                    <button
                      key={n}
                      type="button"
                      className={`type-tab ${count === n ? "active" : ""}`}
                      onClick={() => setCount(n)}
                      aria-pressed={count === n}
                    >
                      {n} 组
                    </button>
                  ))}
                </div>
              </div>
              {mode === "cache" ? (
                <div className="recommend-control-group">
                  <span className="recommend-control-label" id="recommend-pick-label">
                    预选
                  </span>
                  <div
                    className="type-tabs"
                    role="group"
                    aria-labelledby="recommend-pick-label"
                  >
                    <button
                      type="button"
                      className={`type-tab ${isTopN ? "active" : ""}`}
                      onClick={() => setIsTopN(true)}
                      aria-pressed={isTopN}
                    >
                      评分最高
                    </button>
                    <button
                      type="button"
                      className={`type-tab ${!isTopN ? "active" : ""}`}
                      onClick={() => setIsTopN(false)}
                      aria-pressed={!isTopN}
                    >
                      随机抽取
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
              <button
                type="button"
                className="primary-btn"
                onClick={() => void generate(mode, count, isTopN, userRequirement)}
                disabled={loading}
                aria-busy={loading}
              >
                <Sparkle size={16} weight="fill" aria-hidden="true" />
                {loading ? "已下发" : "生成推荐"}
              </button>
            </div>
            <label className="recommend-hint-field">
              <span className="recommend-control-label" id="recommend-hint-label">
                附加要求（可选）
              </span>
              <textarea
                id="recommend-hint"
                className="recommend-hint-input"
                aria-labelledby="recommend-hint-label"
                placeholder="例如：红球和值大于100。要求会与安全网求交，和值仍限制在 90-130。"
                maxLength={800}
                rows={2}
                value={userRequirement}
                onChange={(e) => setUserRequirement(e.target.value)}
              />
            </label>
          </div>
          <p className="info-bar">
            {mode === "feature"
              ? "特征推荐：不传预选号码（drawRecords 为空），模型按特征报告直接生成所选组数。"
              : isTopN
                ? "缓存调优：从缓存取评分最高的所选组数，再交给模型调优。"
                : "缓存调优：从缓存随机抽取所选组数，再交给模型调优。"}
            结果写入后台 JSON，可从最近推荐回看。附加要求会拼进模型提示词，但不能突破和值 90-130、跨度 16-28 等硬边界。
          </p>
        </div>

        <RecommendHistory
          files={files}
          activeFileName={activeFileName}
          loading={listLoading}
          listError={listError}
          disabled={detailLoading}
          hasMore={hasMore}
          loadingMore={loadingMore}
          onSelect={(file) => void review(file)}
          onLoadMore={() => void loadMore()}
        />

        <div className="recommend-main">
          {loading && !data && (
            <div className="status" role="status" aria-atomic="true">
              任务已下发，请耐心等待
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
          {!detailLoading && !error && data && (
            <div id="recommend-report">
              {fromHistory && activeFile ? (
                <p className="history-banner" role="status" aria-atomic="true">
                  正在回看 {activeFile.fileName}
                </p>
              ) : null}
              <RecommendReport data={data} />
            </div>
          )}
          {(booting || listLoading) && !data && !error && !detailLoading && (
            <div className="status" role="status" aria-atomic="true">
              正在加载最近推荐...
            </div>
          )}
          {!loading && !booting && !listLoading && !error && !data && (
            <div className="status">
              选择生成方式和组数后点击「生成推荐」，或从最近推荐回看。
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
