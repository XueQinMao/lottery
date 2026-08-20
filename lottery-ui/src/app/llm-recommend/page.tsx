"use client";

import { useCallback, useEffect, useState } from "react";
import { Sparkle } from "@phosphor-icons/react";
import RecommendHistory from "@/components/RecommendHistory";
import RecommendReport from "@/components/RecommendReport";
import {
  fetchLlmRecommend,
  fetchLlmRecommendHistory,
  fetchLlmRecommendHistoryDetail,
} from "@/lib/api";
import type {
  AdjustHistoryFile,
  LotteryAdjustResp,
} from "@/types/llm-recommend";

const COUNT_OPTIONS = [1, 2, 3, 5];

export default function LlmRecommendPage() {
  const [count, setCount] = useState(2);
  const [isTopN, setIsTopN] = useState(false);
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
    async (size: number, topN: boolean) => {
      setLoading(true);
      setError(null);
      setFromHistory(false);
      try {
        const result = await fetchLlmRecommend(size, topN);
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
              <div className="type-tabs" role="group" aria-label="推荐组数">
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
              <div className="type-tabs" role="group" aria-label="预选方式">
                <button
                  type="button"
                  className={`type-tab ${!isTopN ? "active" : ""}`}
                  onClick={() => setIsTopN(false)}
                  disabled={busy}
                  aria-pressed={!isTopN}
                >
                  百分位抽样
                </button>
                <button
                  type="button"
                  className={`type-tab ${isTopN ? "active" : ""}`}
                  onClick={() => setIsTopN(true)}
                  disabled={busy}
                  aria-pressed={isTopN}
                >
                  缓存 TopN
                </button>
              </div>
            </div>
            <button
              type="button"
              className="primary-btn"
              onClick={() => void generate(count, isTopN)}
              disabled={busy}
              aria-busy={loading}
            >
              <Sparkle size={16} weight="fill" aria-hidden="true" />
              {loading ? "生成中..." : "生成推荐"}
            </button>
          </div>
          <p className="info-bar">
            生成走缓存调优接口，结果写入后台 JSON。最近推荐先拉文件名，点击后再请求详情。
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
              大模型正在基于预选号码调优，通常需要数十秒，请稍候...
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
            <div className="status">选择组数后点击「生成推荐」，或从最近推荐回看。</div>
          )}
        </div>
      </div>
    </div>
  );
}
