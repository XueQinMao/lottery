"use client";

import { useState } from "react";
import { ArrowClockwise, ClockCounterClockwise, Eye } from "@phosphor-icons/react";
import FeatureNumberPools from "@/components/FeatureNumberPools";
import FeatureTrendBoard from "@/components/FeatureTrendBoard";
import FeatureTrendDetailDialog from "@/components/FeatureTrendDetailDialog";
import { useFeatureTrendForecast } from "@/hooks/useFeatureTrendForecast";
import type { FeatureTrendSummary } from "@/types/feature-trend-forecast";

export default function LlmAnalysisPage() {
  const [detail, setDetail] = useState<FeatureTrendSummary | null>(null);
  const {
    plan, period, summaries, history, hasMore, historyError, historyLoading,
    temperature, temperatureError,
    loading, reviewing, error, errors, active, run, review, refreshHistory, retry,
  } = useFeatureTrendForecast();

  const isLatest = period === plan?.targetPeriod;
  const items = plan?.items ?? summaries.map((summary) => ({
    code: summary.featureCode, label: summary.label, blue: summary.blue,
  }));
  const sourcePeriod = isLatest ? plan?.sourcePeriod : summaries[0]?.sourcePeriod;
  const busy = loading || reviewing || active.length > 0;
  const hasFailures = isLatest && Object.keys(errors).length > 0;

  return (
    <div className="page forecast-page">
      <div className="forecast-layout">
        <div className="forecast-content">
          <section className="forecast-hero" aria-labelledby="forecast-period-title">
            <div>
              <h1 id="forecast-period-title">{period ? `${period} 期特征预测` : "特征预测"}</h1>
              {sourcePeriod && (
                <p className="muted">截至 {sourcePeriod} 期 · 已完成 {summaries.length} / {items.length} 项</p>
              )}
            </div>
            <button type="button" className="primary-btn forecast-run" onClick={() => void run()} disabled={busy} aria-busy={loading}>
              <ArrowClockwise size={16} aria-hidden="true" />
              {loading ? "正在分析…" : hasFailures ? "重试未完成项目" : "分析最新走势"}
            </button>
          </section>
          {loading && <p className="status" role="status">{plan && isLatest ? `已完成 ${summaries.length} / ${items.length} 项，走势图推演进行中…` : "正在读取最新开奖与走势图数据…"}</p>}
          {reviewing && <p className="status" role="status">正在读取该期特征值…</p>}
          {error && <p className="status error" role="alert">{error}</p>}
          {period && !reviewing && <FeatureNumberPools sourcePeriod={sourcePeriod} temperature={temperature}
            temperatureError={temperatureError} />}
          {items.length > 0 && period && (
            <FeatureTrendBoard items={items} summaries={summaries} errors={errors} active={active}
              retryDisabled={busy || !sourcePeriod} onDetail={setDetail} onRetry={(code) => void retry(code)} />
          )}
          {!busy && !error && items.length === 0 && <p className="status">这期暂无已保存的推算。</p>}
        </div>

        <div className="forecast-sidebar">
          <aside className="history-panel forecast-history" aria-label="历史预测">
            <div className="history-head">
              <h2><ClockCounterClockwise size={17} aria-hidden="true" />历史预测</h2>
            </div>
            {historyError && <p className="status error" role="alert">{historyError}</p>}
            {!history.length && !historyError && (
              <p className="history-empty muted" role="status">{historyLoading ? "正在读取期号…" : "暂无历史预测"}</p>
            )}
            {history.length > 0 && (
              <ul className="history-list forecast-history-list">
                {history.map((target) => (
                  <li key={target}>
                    <button type="button" className={`history-item ${period === target ? "active" : ""}`}
                      onClick={() => void review(target)} aria-current={period === target ? "true" : undefined} disabled={busy}>
                      <span className="forecast-period-number">{target}_特征预测</span>
                      <span className="history-action"><Eye size={14} aria-hidden="true" />{period === target ? "当前查看" : "查看"}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {(hasMore || historyError) && (
              <button className="ghost-btn forecast-more" type="button" disabled={historyLoading}
                onClick={() => void refreshHistory(historyError ? undefined : history.at(-1))}>
                {historyLoading ? "加载中…" : historyError ? "重试加载" : "更早期号"}
              </button>
            )}
          </aside>
        </div>
      </div>

      {detail && <FeatureTrendDetailDialog key={`${detail.targetPeriod}-${detail.featureCode}`} summary={detail}
        onClose={() => setDetail(null)} />}
    </div>
  );
}
