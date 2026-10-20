"use client";

import { useEffect, useRef, useState } from "react";
import { Check, Copy, X } from "@phosphor-icons/react";
import type {
  AdjustedTicket,
  FeatureCoverage,
  FeatureHitSummary,
  LotteryAdjustResp,
} from "@/types/llm-recommend";

interface TicketRow {
  title: string;
  category?: AdjustedTicket["category"];
  reds: number[];
  blues: number[];
  reason?: string;
  original?: { reds: number[]; blues: number[] };
  bets?: number;
  hits?: FeatureHitSummary;
  coverage?: FeatureCoverage[];
}

const HIT_LABEL = { MAIN: "命中主推", ALT: "命中备选", MISS: "未命中" };
const TICKET_LABEL = { OPTIMIZED: "优化号码", RECOMMENDED: "推荐号码", FULL_MATCH: "全特征命中" };
const pad = (number: number) => String(number).padStart(2, "0");

function CombinedBalls({ reds, blues }: Pick<TicketRow, "reds" | "blues">) {
  return (
    <div className="recommend-number-groups">
      <div className="chip-row" role="group" aria-label="红球">
        {reds.map((number) => <span className="chip chip-red" key={number}>{pad(number)}</span>)}
      </div>
      <div className="chip-row" role="group" aria-label="蓝球">
        {blues.map((number) => <span className="chip chip-blue" key={number}>{pad(number)}</span>)}
      </div>
    </div>
  );
}

function CopyTicketButton({ ticket }: { ticket: TicketRow }) {
  const [feedback, setFeedback] = useState<"copied" | "failed" | null>(null);
  const text = [ticket.reds, ticket.blues].map((balls) => balls.map(pad).join(" ")).filter(Boolean).join(" + ");

  useEffect(() => {
    if (!feedback) return;
    const timer = window.setTimeout(() => setFeedback(null), 2200);
    return () => window.clearTimeout(timer);
  }, [feedback]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setFeedback("copied");
    } catch {
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.left = "-9999px";
      document.body.append(textarea);
      textarea.select();
      try {
        setFeedback(document.execCommand("copy") ? "copied" : "failed");
      } catch {
        setFeedback("failed");
      } finally {
        textarea.remove();
      }
    }
  };

  return (
    <button type="button" className={`copy-ticket-btn ${feedback === "copied" ? "is-copied" : ""}`}
      onClick={() => void copy()} disabled={!text}>
      {feedback === "copied" ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}
      <span aria-live="polite">{feedback === "copied" ? "已复制" : feedback === "failed" ? "复制失败" : "复制"}</span>
    </button>
  );
}

function FeatureDetails({ ticket }: { ticket: TicketRow }) {
  const hits = ticket.hits?.hits;
  if (hits?.length) {
    return (
      <section>
        <h3>特征命中</h3>
        <div className="hit-summary">
          <span className="hit-kpi main">主推 {ticket.hits?.mainHitCount ?? 0}</span>
          <span className="hit-kpi alt">备选 {ticket.hits?.altHitCount ?? 0}</span>
          <span className="hit-kpi miss">未中 {ticket.hits?.missCount ?? 0}</span>
        </div>
        <div className="table-wrap">
          <table className="data-table recommend-features-table">
            <thead><tr><th>特征</th><th>实际值</th><th>主推 / 备选</th><th>结果</th></tr></thead>
            <tbody>
              {hits.map((hit) => (
                <tr key={hit.code}>
                  <th scope="row">{hit.label}</th>
                  <td>{hit.actual}</td>
                  <td>{[hit.mainValue, ...(hit.alternatives ?? [])].filter(Boolean).join("、")}</td>
                  <td>{HIT_LABEL[hit.hitType ?? "MISS"]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    );
  }
  if (ticket.coverage?.length) {
    return (
      <section>
        <h3>复式组合覆盖</h3>
        <p className="muted">逐注对照预测值，同一注可匹配多项特征。</p>
        <div className="table-wrap">
          <table className="data-table recommend-features-table">
            <thead><tr><th>特征</th><th>主推 / 备选</th><th>主推注数</th><th>备选注数</th></tr></thead>
            <tbody>
              {ticket.coverage.map((feature) => (
                <tr key={feature.code}>
                  <th scope="row">{feature.label}</th>
                  <td>{[feature.mainValue, ...feature.alternatives].join("、")}</td>
                  <td>{feature.mainHitBets}</td>
                  <td>{feature.altHitBets}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    );
  }
  return <p className="muted">暂无特征对照记录。</p>;
}

function TicketDetail({ ticket, onClose }: { ticket: TicketRow; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (!dialog.current?.open) dialog.current?.showModal();
  }, []);
  return (
    <dialog className="feature-detail-dialog" ref={dialog} onClose={onClose} aria-labelledby="recommend-detail-title">
      <div className="feature-detail-header">
        <h2 id="recommend-detail-title">{ticket.title} · 推荐详情</h2>
        <button className="ghost-btn feature-detail-close" type="button" aria-label="关闭详情"
          onClick={() => dialog.current?.close()}><X size={20} aria-hidden="true" /></button>
      </div>
      <div className="feature-detail-body">
        <CombinedBalls reds={ticket.reds} blues={ticket.blues} />
        {ticket.original && <section>
          <h3>{ticket.category === "OPTIMIZED" ? "优化前号码" : "缓存原号码"}</h3>
          <CombinedBalls {...ticket.original} />
        </section>}
        <section><h3>推荐理由</h3><p>{ticket.reason || "暂无推荐理由。"}</p></section>
        <FeatureDetails ticket={ticket} />
      </div>
    </dialog>
  );
}

function TicketListItem({ ticket, onDetail }: { ticket: TicketRow; onDetail: () => void }) {
  const label = ticket.category ? TICKET_LABEL[ticket.category] : ticket.original && "缓存调优";
  return (
    <li className="recommend-ticket-row">
      <div className="recommend-ticket-label">
        <strong className={ticket.bets != null ? "whitespace-nowrap" : undefined}>
          {ticket.title}
          {ticket.bets != null && <span className="muted font-normal">（{ticket.bets} 注）</span>}
        </strong>
        {label && <span className="recommend-ticket-badge" data-category={ticket.category}>{label}</span>}
      </div>
      <CombinedBalls reds={ticket.reds} blues={ticket.blues} />
      <div className="recommend-ticket-actions">
        <CopyTicketButton ticket={ticket} />
        <button className="copy-ticket-btn" type="button" onClick={onDetail}>查看详情</button>
      </div>
    </li>
  );
}

export default function RecommendReport({ data }: { data: LotteryAdjustResp }) {
  const [selected, setSelected] = useState<TicketRow | null>(null);

  const adjusted = (data.adjustedTickets ?? []).map((ticket, index): TicketRow => ({
    title: `第 ${index + 1} 组`,
    category: ticket.category,
    reds: ticket.adjustedRedBalls ?? [],
    blues: ticket.adjustedBlueBall == null ? [] : [ticket.adjustedBlueBall],
    reason: ticket.reason,
    original: ticket.originalRedBalls?.length && ticket.originalBlueBall != null
      ? { reds: ticket.originalRedBalls, blues: [ticket.originalBlueBall] } : undefined,
    hits: data.adjustedTicketHits?.[index],
  }));
  const singles = adjusted.concat((data.finalRecommendation?.singleTickets ?? []).map((ticket, index) => ({
    title: ticket.name || `第 ${adjusted.length + index + 1} 组`,
    reds: ticket.redBalls ?? [],
    blues: ticket.blueBall == null ? [] : [ticket.blueBall],
    reason: ticket.basis,
    hits: data.finalSingleHits?.[index],
  })));
  const complex = data.finalRecommendation?.complexTicket;
  const trendImages = data.numberTrendImages ?? [];
  const trendCandidates = data.numberTrendObservations ?? [];
  const redCandidates = trendCandidates.filter((item) => item.ball === "RED").map((item) => pad(item.number));
  const blueCandidates = trendCandidates.filter((item) => item.ball === "BLUE").map((item) => pad(item.number));
  const complexRow = complex && {
    title: "复式",
    reds: complex.redBalls ?? [],
    blues: complex.blueBalls ?? [],
    reason: complex.basis,
    bets: complex.totalBets,
    coverage: data.complexHits,
  };

  return (
    <section className="recommend-results" aria-label="推荐结果">
      <header className="recommend-results-header">
        <h2>推荐结果</h2>
        <p className="muted">{[data.targetPeriod && `${data.targetPeriod} 期`, `${singles.length} 组单式`, complexRow && "1 组复式"].filter(Boolean).join(" · ")}</p>
      </header>
      {trendImages.length > 0 ? (
        <details className="recommend-trend-images">
          <summary>查看模型输入截图</summary>
          {trendImages.map(({ code, dataUrl }) => {
            const label = code === "red" ? "红球走势图" : "蓝球走势图";
            return (
              <figure className="recommend-trend-image" key={code}>
                <figcaption>
                  <span>{label}</span>
                  <a className="copy-ticket-btn" href={dataUrl}
                    download={`number-trend-${data.sourcePeriod ?? data.targetPeriod ?? "input"}-${code}.png`}>
                    下载 PNG
                  </a>
                </figcaption>
                <div className="recommend-trend-image-scroll" tabIndex={0} role="region" aria-label={label}>
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={dataUrl} alt={label} loading="lazy" />
                </div>
              </figure>
            );
          })}
        </details>
      ) : null}
      {data.numberTrendObservations && (
        trendCandidates.length > 0 ? (
          <details className="status">
            <summary>查看走势图候选</summary>
            <ul className="recommend-trend-candidates">
              <li>红球 {redCandidates.join("、") || "暂无候选"}</li>
              {blueCandidates.length > 0 && <li>蓝球 {blueCandidates.join("、")}</li>}
            </ul>
          </details>
        ) : <div className="status">号码走势图暂无下期红球候选，本次按特征生成。</div>
      )}
      <ul className="recommend-ticket-list" aria-label="单式号码">
        {singles.map((ticket, index) => <TicketListItem key={index} ticket={ticket} onDetail={() => setSelected(ticket)} />)}
      </ul>
      {complexRow && <ul className="recommend-ticket-list recommend-complex-list" aria-label="复式号码">
        <TicketListItem ticket={complexRow} onDetail={() => setSelected(complexRow)} />
      </ul>}
      {selected && <TicketDetail ticket={selected} onClose={() => setSelected(null)} />}
    </section>
  );
}
