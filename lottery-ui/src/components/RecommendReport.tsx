"use client";

import type { FeatureForecast } from "@/types/llm-analysis";
import type {
  AdjustedTicket,
  ComplexTicket,
  FeatureHit,
  FeatureHitSummary,
  FinalRecommendation,
  LotteryAdjustResp,
  SingleTicket,
} from "@/types/llm-recommend";

function pad(n: number) {
  return String(n).padStart(2, "0");
}

function hasItems<T>(arr?: T[] | null): arr is T[] {
  return Array.isArray(arr) && arr.length > 0;
}

function BallChip({
  n,
  tone = "red",
}: {
  n: number;
  tone?: "red" | "blue" | "dan";
}) {
  return <span className={`chip chip-${tone}`}>{pad(n)}</span>;
}

function BallList({
  reds,
  blue,
  blues,
}: {
  reds?: number[];
  blue?: number;
  blues?: number[];
}) {
  return (
    <div className="chip-row recommend-balls">
      {hasItems(reds)
        ? reds.map((n) => <BallChip key={`r-${n}`} n={n} tone="red" />)
        : null}
      {blue != null ? <BallChip key={`b-${blue}`} n={blue} tone="blue" /> : null}
      {hasItems(blues)
        ? blues.map((n) => <BallChip key={`bs-${n}`} n={n} tone="blue" />)
        : null}
    </div>
  );
}

function HitChip({ hit }: { hit: FeatureHit }) {
  const tone = (hit.hitType ?? "MISS").toLowerCase();
  const extra =
    hit.hitType === "MAIN"
      ? hit.actual || "-"
      : hit.hitType === "ALT"
        ? `${hit.actual || "-"} · 主推 ${hit.mainValue || "-"}`
        : `${hit.actual || "-"} ≠ ${hit.mainValue || "-"}`;
  const altText = hasItems(hit.alternatives)
    ? `备选 ${hit.alternatives.join("、")}`
    : "";
  return (
    <span
      className={`hit-chip hit-chip-${tone}`}
      title={[hit.label, extra, altText].filter(Boolean).join("；")}
    >
      <strong>{hit.label}</strong>
      <span>{extra}</span>
    </span>
  );
}

function HitGroup({
  title,
  items,
  tone,
}: {
  title: string;
  items: FeatureHit[];
  tone: "main" | "alt" | "miss";
}) {
  return (
    <div className={`hit-group hit-group-${tone}`}>
      <div className="kv-label">
        {title}
        <span className="hit-count">{items.length}</span>
      </div>
      {items.length === 0 ? (
        <span className="muted">无</span>
      ) : (
        <div className="chip-row">
          {items.map((hit) => (
            <HitChip key={hit.code || hit.label} hit={hit} />
          ))}
        </div>
      )}
    </div>
  );
}

function FeatureHitBoard({ summary }: { summary?: FeatureHitSummary }) {
  if (!summary || !hasItems(summary.hits)) {
    return <p className="muted">暂无形态对照（需有效的 6 红 + 1 蓝）。</p>;
  }
  const main = summary.hits.filter((h) => h.hitType === "MAIN");
  const alt = summary.hits.filter((h) => h.hitType === "ALT");
  const miss = summary.hits.filter((h) => h.hitType === "MISS");
  return (
    <div className="hit-board">
      <div className="hit-summary" aria-label="形态命中汇总">
        <span className="hit-kpi main">
          主推 {summary.mainHitCount ?? main.length}
        </span>
        <span className="hit-kpi alt">
          备选 {summary.altHitCount ?? alt.length}
        </span>
        <span className="hit-kpi miss">
          未中 {summary.missCount ?? miss.length}
        </span>
      </div>
      <div className="hit-groups">
        <HitGroup title="命中主推" items={main} tone="main" />
        <HitGroup title="命中备选" items={alt} tone="alt" />
        <HitGroup title="未命中" items={miss} tone="miss" />
      </div>
    </div>
  );
}

const FORECAST_ROWS: { key: keyof FeatureForecast; label: string }[] = [
  { key: "oddEven", label: "奇偶比" },
  { key: "bigSmall", label: "大小比" },
  { key: "primeComposite", label: "质合比" },
  { key: "ratio012", label: "012路比" },
  { key: "span", label: "跨度" },
  { key: "sumRange", label: "和值区间" },
  { key: "sumTail", label: "和值尾数" },
  { key: "threeZone", label: "三区比" },
  { key: "zone1Count", label: "一区个数" },
  { key: "zone2Count", label: "二区个数" },
  { key: "zone3Count", label: "三区个数" },
  { key: "blueOddEven", label: "蓝球奇偶" },
  { key: "blueBigSmall", label: "蓝球大小" },
  { key: "blueBigSmallOddEven", label: "蓝球大小奇偶" },
  { key: "blueRatio012", label: "蓝球012路" },
];

function ForecastStrip({ data }: { data?: FeatureForecast }) {
  if (!data) return null;
  return (
    <section className="section report-card report-span-full">
      <h2>形态目标（对照基准）</h2>
      {data.basis ? <p className="basis">{data.basis}</p> : null}
      <div className="chart-container">
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>形态</th>
                <th>主推</th>
                <th>备选</th>
              </tr>
            </thead>
            <tbody>
              {FORECAST_ROWS.map((row) => {
                const item = data[row.key];
                if (!item || typeof item === "string") return null;
                return (
                  <tr key={row.key}>
                    <td>{row.label}</td>
                    <td>
                      <strong>{item.value || "-"}</strong>
                    </td>
                    <td>{item.alternatives?.join("、") || "-"}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function SingleCard({
  title,
  reds,
  blue,
  reason,
  summary,
}: {
  title: string;
  reds?: number[];
  blue?: number;
  reason?: string;
  summary?: FeatureHitSummary;
}) {
  return (
    <div className="chart-container ticket-card">
      <div className="chart-title">
        <span>{title}</span>
      </div>
      <BallList reds={reds} blue={blue} />
      {reason ? <p className="basis">{reason}</p> : null}
      <FeatureHitBoard summary={summary} />
    </div>
  );
}

function ComplexCard({ ticket }: { ticket?: ComplexTicket }) {
  if (!ticket) return null;
  return (
    <div className="chart-container ticket-card">
      <div className="chart-title">
        <span>{ticket.name || "复式"}</span>
        {ticket.totalBets != null ? (
          <span className="tag">{ticket.totalBets} 注</span>
        ) : null}
      </div>
      <BallList reds={ticket.redBalls} blues={ticket.blueBalls} />
      {ticket.basis ? <p className="basis">{ticket.basis}</p> : null}
      <p className="muted">复式红球非 6 个，不按单式形态维对照。</p>
    </div>
  );
}

function FinalPackage({
  data,
  hits,
}: {
  data?: FinalRecommendation;
  hits?: FeatureHitSummary[];
}) {
  if (!data) return null;
  return (
    <section className="section report-card report-span-full">
      <h2>最终推荐包</h2>
      <div className="chart-container">
        <div className="chart-title">
          <span>胆码</span>
          <span className="tag">3 个</span>
        </div>
        <div className="chip-row recommend-balls">
          {hasItems(data.danBalls)
            ? data.danBalls.map((n) => <BallChip key={`dan-${n}`} n={n} tone="dan" />)
            : <span className="muted">无</span>}
        </div>
        {data.danBasis ? <p className="basis">{data.danBasis}</p> : null}
      </div>
      <div className="ticket-grid">
        {data.singleTickets?.map((t: SingleTicket, i) => (
          <SingleCard
            key={t.name || `final-${i}`}
            title={t.name || `最终单式 ${i + 1}`}
            reds={t.redBalls}
            blue={t.blueBall}
            reason={t.basis}
            summary={hits?.[i]}
          />
        ))}
        <ComplexCard ticket={data.complexTicket} />
      </div>
    </section>
  );
}

export default function RecommendReport({ data }: { data: LotteryAdjustResp }) {
  return (
    <div className="report-bento">
      {data.conclusion ? (
        <section className="section report-card report-span-full">
          <h2>综合说明</h2>
          <p className="basis">{data.conclusion}</p>
        </section>
      ) : null}
      <FinalPackage
        data={data.finalRecommendation}
        hits={data.finalSingleHits}
      />
      {hasItems(data.adjustedTickets) ? (
        <section className="section report-card report-span-full">
          <h2>推荐号码组（{data.adjustedTickets.length}）</h2>
          <div className="ticket-grid">
            {data.adjustedTickets.map((t: AdjustedTicket, i) => (
              <SingleCard
                key={t.id || `adj-${i}`}
                title={t.id || `推荐组 ${i + 1}`}
                reds={t.adjustedRedBalls}
                blue={t.adjustedBlueBall}
                reason={t.reason}
                summary={data.adjustedTicketHits?.[i]}
              />
            ))}
          </div>
        </section>
      ) : null}
      <ForecastStrip data={data.featureForecast} />
    </div>
  );
}
