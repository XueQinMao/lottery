"use client";

import { useCallback, useEffect, useState } from "react";
import AnalysisReport from "@/components/AnalysisReport";
import RecommendHistory from "@/components/RecommendHistory";
import {
  fetchAnalysisHistoryDetail,
  fetchAnalyzeLatest,
  fetchPredictFileHistory,
} from "@/lib/api";
import type { LotteryAnalysisResp } from "@/types/llm-analysis";
import type { PredictFileRecord } from "@/types/predict-file-record";
import { PREDICT_FILE_TYPE } from "@/types/predict-file-record";

const SAMPLE_OPTIONS = [100];

export default function LlmAnalysisPage() {
  const [sampleSize, setSampleSize] = useState(100);
  const [data, setData] = useState<LotteryAnalysisResp | null>(null);
  const [files, setFiles] = useState<PredictFileRecord[]>([]);
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
      const result = await fetchPredictFileHistory(PREDICT_FILE_TYPE.ANALYSIS, 20);
      setFiles(result);
      return result;
    } catch (e) {
      setFiles([]);
      setListError(e instanceof Error ? e.message : "获取特征预测记录失败");
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

  const load = useCallback(async (size: number) => {
    setLoading(true);
    setError(null);
    setFromHistory(false);
    try {
      const result = await fetchAnalyzeLatest(size);
      setData(result);
      const nextFiles = await loadFiles(true);
      setActiveFileName(nextFiles[0]?.fileName ?? null);
    } catch (e) {
      setData(null);
      setError(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }, [loadFiles]);

  useEffect(() => {
    void load(sampleSize);
  }, [load, sampleSize]);

  const review = useCallback(async (file: PredictFileRecord) => {
    setDetailLoading(true);
    setError(null);
    setFromHistory(true);
    setActiveFileName(file.fileName);
    try {
      const result = await fetchAnalysisHistoryDetail(file.fileName);
      setData(result);
      const report = document.getElementById("analysis-report");
      const reduceMotion = window.matchMedia(
        "(prefers-reduced-motion: reduce)",
      ).matches;
      report?.scrollIntoView({
        behavior: reduceMotion ? "auto" : "smooth",
        block: "start",
      });
    } catch (e) {
      setData(null);
      setError(e instanceof Error ? e.message : "读取特征预测详情失败");
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
                <span className="recommend-control-label" id="analysis-sample-label">
                  样本期数
                </span>
                <div
                  className="type-tabs"
                  role="group"
                  aria-labelledby="analysis-sample-label"
                >
                  {SAMPLE_OPTIONS.map((n) => (
                    <button
                      key={n}
                      type="button"
                      className={`type-tab ${sampleSize === n ? "active" : ""}`}
                      onClick={() => setSampleSize(n)}
                      disabled={busy}
                      aria-pressed={sampleSize === n}
                    >
                      近 {n} 期
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <button
              type="button"
              className="primary-btn"
              onClick={() => void load(sampleSize)}
              disabled={busy}
              aria-busy={loading}
            >
              重新分析
            </button>
          </div>
          <p className="info-bar">
            特征预测：基于历史开奖统计与形态推算生成特征报告，结果写入后台 JSON，可从最近分析回看。
          </p>
        </div>

        <RecommendHistory
          files={files}
          activeFileName={activeFileName}
          title="最近分析"
          ariaLabel="最近分析"
          emptyHint="暂无特征预测记录。分析后会写入后台目录，可点文件名回看。"
          loading={listLoading}
          listError={listError}
          disabled={busy}
          onSelect={(file) => void review(file)}
        />

        <div className="recommend-main">
          {loading && (
            <div className="status" role="status" aria-atomic="true">
              正在生成特征预测，请稍候...
            </div>
          )}
          {detailLoading && (
            <div className="status" role="status" aria-atomic="true">
              正在读取特征预测详情...
            </div>
          )}
          {error && (
            <div className="status error" role="alert">
              {error}
            </div>
          )}
          {!busy && !error && data && (
            <div id="analysis-report">
              {fromHistory && activeFile ? (
                <p className="history-banner" role="status" aria-atomic="true">
                  正在回看 {activeFile.fileName}
                </p>
              ) : null}
              <AnalysisReport data={data} />
            </div>
          )}
          {!busy && !error && !data && (
            <div className="status">
              选择样本期数后点击「重新分析」，或从最近分析回看。
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
