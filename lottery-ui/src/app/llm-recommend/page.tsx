"use client";

import { useCallback, useEffect, useState } from "react";
import { Sparkle } from "@phosphor-icons/react";
import RecommendHistory from "@/components/RecommendHistory";
import RecommendReport from "@/components/RecommendReport";
import {
  fetchLlmCacheRecommend,
  fetchLlmFeatureRecommend,
  fetchLlmRecommendHistory,
  fetchLlmRecommendHistoryDetail,
} from "@/lib/api";
import type {
  AdjustHistoryFile,
  LotteryAdjustResp,
} from "@/types/llm-recommend";

const COUNT_OPTIONS = [1, 2, 3, 5];

type RecommendMode = "feature" | "cache";

export default function LlmRecommendPage() {
  const [mode, setMode] = useState<RecommendMode>("feature");
  const [count, setCount] = useState(2);
  const [isTopN, setIsTopN] = useState(true);
  const [data, setData] = useState<LotteryAdjustResp | null>(null);
  const [files, setFiles] = useState<AdjustHistoryFile[]>([]);
  const [activeFileName, setActiveFileName] = useState<string | null>(null);
  const [fromHistory, setFromHistory] = useState(false);
  const [loading, setLoading] = useState(false);
  const [listLoading, setListLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [listError, setListError] = useState<string | null>(null);

  const loadFiles = useCallback(async (silent = false) => {
    if (!silent) {
      setListLoading(true);
    }
    setListError(null);
    try {
      const result = await fetchLlmRecommendHistory(20);
      setFiles(result);
      return result;
    } catch (e) {
      setFiles([]);
      setListError(e instanceof Error ? e.message : "获取推荐记录失败");
      return [];
    } finally {
      if (!silent) {
        setListLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadFiles();
  }, [loadFiles]);

  const generate = useCallback(
    async (nextMode: RecommendMode, size: number, topN: boolean) => {
      setLoading(true);
      setError(null);
      setFromHistory(false);
      try {
        const result =
          nextMode === "feature"
            ? await fetchLlmFeatureRecommend(size)
            : await fetchLlmCacheRecommend(size, topN);
        setData(result);
        const nextFiles = await loadFiles(true);
        setActiveFileName(nextFiles[0]?.fileName ?? null);
      } catch (e) {
        setData(null);
        setActiveFileName(null);
        setError(e instanceof Error ? e.message : "推荐失败");
      } finally {
        setLoading(false);
      }
    },
    [loadFiles],
  );

  const review = useCallback(async (file: AdjustHistoryFile) => {
    setDetailLoading(true);
    setError(null);
    setFromHistory(true);
    setActiveFileName(file.fileName);
    try {
      const result = await fetchLlmRecommendHistoryDetail(file.fileName);
      setData(result);
      const report = document.getElementById("recommend-report");
      const reduceMotion = window.matchMedia(
        "(prefers-reduced-motion: reduce)",
      ).matches;
      report?.scrollIntoView({
        behavior: reduceMotion ? "auto" : "smooth",
        block: "start",
      });
    } catch (e) {
      setData(null);
      setError(e instanceof Error ? e.message : "读取推荐详情失败");
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const busy = loading || detailLoading;
  const activeFile = files.find((item) => item.fileName === activeFileName) ?? null;

  return (
    <div className="page recommend-page">
      <div className="recommend-workspace">
        <div className="filter-card recommend-toolbar-card">
          <div className="recommend-toolbar">
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
                    disabled={busy}
                    aria-pressed={mode === "feature"}
                  >
                    特征推荐
                  </button>
                  <button
                    type="button"
                    className={`type-tab ${mode === "cache" ? "active" : ""}`}
                    onClick={() => setMode("cache")}
                    disabled={busy}
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
                      disabled={busy}
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
                      disabled={busy}
                      aria-pressed={isTopN}
                    >
                      评分最高
                    </button>
                    <button
                      type="button"
                      className={`type-tab ${!isTopN ? "active" : ""}`}
                      onClick={() => setIsTopN(false)}
                      disabled={busy}
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
              onClick={() => void generate(mode, count, isTopN)}
              disabled={busy}
              aria-busy={loading}
            >
              <Sparkle size={16} weight="fill" aria-hidden="true" />
              {loading ? "生成中..." : "生成推荐"}
            </button>
          </div>
          <p className="info-bar">
            {mode === "feature"
              ? "特征推荐：不传预选号码（drawRecords 为空），模型按特征报告直接生成所选组数。"
              : isTopN
                ? "缓存调优：从缓存取评分最高的所选组数，再交给模型调优。"
                : "缓存调优：从缓存随机抽取所选组数，再交给模型调优。"}
            结果写入后台 JSON，可从最近推荐回看。
          </p>
        </div>

        <RecommendHistory
          files={files}
          activeFileName={activeFileName}
          loading={listLoading}
          listError={listError}
          disabled={busy}
          onSelect={(file) => void review(file)}
        />

        <div className="recommend-main">
          {loading && (
            <div className="status" role="status" aria-atomic="true">
              {mode === "feature"
                ? "大模型正在按特征报告生成号码组，通常需要数十秒，请稍候..."
                : "大模型正在基于预选号码调优，通常需要数十秒，请稍候..."}
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
          {!busy && !error && data && (
            <div id="recommend-report">
              {fromHistory && activeFile ? (
                <p className="history-banner" role="status" aria-atomic="true">
                  正在回看 {activeFile.fileName}
                </p>
              ) : null}
              <RecommendReport data={data} />
            </div>
          )}
          {!busy && !error && !data && (
            <div className="status">
              选择生成方式和组数后点击「生成推荐」，或从最近推荐回看。
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
