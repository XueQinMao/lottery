"use client";

import { type FormEvent, useEffect, useState } from "react";
import { ArrowsClockwise } from "@phosphor-icons/react";
import { fetchDrawReview, syncDrawHistory } from "@/lib/api";
import type { DrawReview } from "@/types/draw-review";
import ReviewReport from "./ReviewReport";
import styles from "./review.module.css";

export default function ReviewPage() {
  const [periodInput, setPeriodInput] = useState("");
  const [query, setQuery] = useState({ period: "", revision: 0 });
  const [review, setReview] = useState<DrawReview | null>(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState("");
  const [formError, setFormError] = useState("");
  const [syncError, setSyncError] = useState("");

  useEffect(() => {
    const request = new AbortController();
    fetchDrawReview(query.period, request.signal)
      .then(setReview)
      .catch(() => {
        if (!request.signal.aborted) setError("复盘加载失败，请重试。");
      })
      .finally(() => {
        if (!request.signal.aborted) setLoading(false);
      });
    return () => request.abort();
  }, [query]);

  function load(period: string) {
    setLoading(true);
    setError("");
    setQuery((previous) => ({ period, revision: previous.revision + 1 }));
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const period = periodInput.trim();
    if (period && (!/^[1-9][0-9]{6}$/.test(period) || period.endsWith("000"))) {
      setFormError("请输入 7 位开奖期号，留空查询最新一期。");
      return;
    }
    setFormError("");
    setSyncError("");
    load(period);
  }

  async function sync() {
    setSyncing(true);
    setSyncError("");
    try {
      await syncDrawHistory();
      setPeriodInput("");
      setFormError("");
      load("");
    } catch {
      setSyncError("同步开奖失败，请稍后重试。");
    } finally {
      setSyncing(false);
    }
  }

  return (
    <div className="page">
      <div className="filter-card">
        <div className={styles.toolbar}>
          <form className="period-query" onSubmit={search}>
            <label htmlFor="review-period">开奖期号</label>
            <input id="review-period" inputMode="numeric" autoComplete="off" placeholder="最新一期"
              maxLength={7} value={periodInput} aria-invalid={!!formError}
              aria-describedby={formError ? "review-period-error" : undefined}
              onChange={(event) => { setPeriodInput(event.target.value); setFormError(""); }} />
            <button type="submit" className="ghost-btn" disabled={syncing}>查询</button>
            {query.period && (
              <button type="button" className="type-tab" disabled={syncing}
                onClick={() => { setPeriodInput(""); setFormError(""); load(""); }}>回到最新</button>
            )}
          </form>
          <button type="button" className={`primary-btn ${styles.sync}`} disabled={syncing} onClick={() => void sync()}>
            <ArrowsClockwise size={16} aria-hidden="true" />
            {syncing ? "正在同步…" : "同步开奖"}
          </button>
        </div>
        {formError && <p id="review-period-error" className="draw-trend-validation" role="alert">{formError}</p>}
        {syncError && <p className="draw-trend-validation" role="alert">{syncError}</p>}
      </div>
      {loading ? <div className="status" role="status">正在加载开奖复盘…</div>
        : error ? (
          <div className="status error" role="alert">
            {error} <button type="button" className="type-tab" onClick={() => load(query.period)}>重新加载</button>
          </div>
        ) : review ? <ReviewReport review={review} />
          : <div className="status">{query.period ? `${query.period} 期尚无开奖记录，可同步开奖后再查询。` : "暂无开奖记录，可先同步开奖。"}</div>}
    </div>
  );
}
