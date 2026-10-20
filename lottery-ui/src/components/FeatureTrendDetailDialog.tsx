"use client";

import { useEffect, useRef, useState } from "react";
import { X } from "@phosphor-icons/react";
import { fetchFeatureTrendDetail } from "@/lib/featureTrendApi";
import { exportFeatureTrendImages } from "@/lib/featureTrendCharts";
import type { FeatureTrendDetail, FeatureTrendSheet, FeatureTrendSummary } from "@/types/feature-trend-forecast";

interface Props {
  summary: FeatureTrendSummary;
  onClose: () => void;
}

export default function FeatureTrendDetailDialog({ summary, onClose }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const request = useRef<AbortController | null>(null);
  const [detail, setDetail] = useState<FeatureTrendDetail | null>(null);
  const [images, setImages] = useState<FeatureTrendSheet[]>([]);
  const [error, setError] = useState("");
  const [imageError, setImageError] = useState("");
  const [imageLoading, setImageLoading] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!dialogRef.current?.open) dialogRef.current?.showModal();
    const controller = new AbortController();
    request.current = controller;
    void fetchFeatureTrendDetail(summary.targetPeriod, summary.featureCode, controller.signal)
      .then((result) => { if (!controller.signal.aborted) setDetail(result); })
      .catch((failure) => {
        if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "详情加载失败");
      });
    return () => controller.abort();
  }, [summary.targetPeriod, summary.featureCode, attempt]);

  const loadImages = async () => {
    const signal = request.current?.signal;
    if (!detail || !signal) return;
    setImageError("");
    setImageLoading(true);
    try {
      const result = await exportFeatureTrendImages(detail.evidence.input, summary.blue, signal);
      if (!signal.aborted) setImages(result);
    } catch (failure) {
      if (!signal.aborted) setImageError(failure instanceof Error ? failure.message : "走势图生成失败");
    } finally {
      if (!signal.aborted) setImageLoading(false);
    }
  };

  return (
    <dialog className="feature-detail-dialog" ref={dialogRef} onClose={onClose} aria-labelledby="feature-detail-title">
      <div className="feature-detail-header">
        <div>
          <p className="muted">{summary.targetPeriod} 期 · {summary.blue ? "蓝球" : "红球"}</p>
          <h2 id="feature-detail-title">{summary.label}详情</h2>
        </div>
        <button className="ghost-btn feature-detail-close" type="button" onClick={() => dialogRef.current?.close()} aria-label="关闭详情">
          <X size={20} aria-hidden="true" />
        </button>
      </div>
      {error ? (
        <div className="status error" role="alert">
          {error}
          <button className="ghost-btn" type="button" onClick={() => { setError(""); setAttempt((value) => value + 1); }}>重试</button>
        </div>
      ) : detail ? (
        <div className="feature-detail-body">
          <p className="muted">{detail.evidence.input.periods.at(0)} — {summary.sourcePeriod} · {summary.model}</p>
          <section>
            <h3>走势观察</h3>
            <p>{detail.evidence.inference.observation}</p>
          </section>
          {detail.evidence.inference.candidates.map((candidate) => (
            <section className="feature-detail-candidate" key={candidate.value}>
              <h3>{candidate.value}</h3>
              <p>{candidate.reason}</p>
              <p className="muted">{candidate.caution}</p>
              <p className="muted">参考期号 {candidate.evidencePeriods.join("、")}</p>
            </section>
          ))}
          <section>
            <h3>相反证据</h3>
            <p>{detail.evidence.inference.counterEvidence}</p>
          </section>
          <div className="feature-detail-actions">
            <a className="ghost-btn" href={`data:application/json;charset=utf-8,${encodeURIComponent(JSON.stringify(detail.evidence.input, null, 2))}`}
              download={`trend-${summary.targetPeriod}-${summary.featureCode}.json`}>下载逐期数据</a>
            {images.length === 0 && (
              <button className="ghost-btn" type="button" onClick={() => void loadImages()} disabled={imageLoading}>
                {imageLoading ? "生成中…" : "查看与下载走势图"}
              </button>
            )}
          </div>
          {imageError && <p className="status error" role="alert">{imageError}</p>}
          {images.map((image) => (
            <details className="zone-trend-image" key={image.code}>
              <summary>{image.label}</summary>
              <a className="ghost-btn" href={image.dataUrl} download={`trend-${summary.targetPeriod}-${image.code}.png`}>下载图片</a>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={image.dataUrl} alt={image.label + "走势图"} width={1200} height={image.height} />
            </details>
          ))}
        </div>
      ) : <p className="status" role="status">正在读取分析详情…</p>}
    </dialog>
  );
}
