import type { DrawReview, FeatureReview, RecommendationReview, TicketResult } from "@/types/draw-review";
import styles from "./review.module.css";
import LearningProgress from "./LearningProgress";

const FEATURE_STATUS = {
  MAIN_HIT: ["主推命中", "main"],
  ALTERNATE_HIT: ["备选命中", "alt"],
  MISS: ["未命中", "miss"],
  NOT_PREDICTED: ["未预测", ""],
  NO_ACTUAL: ["历史样本不足", ""],
};

const TICKET_LABEL = {
  OPTIMIZED: "优化号码", RECOMMENDED: "推荐号码", FULL_MATCH: "全特征匹配",
  SINGLE: "单式", COMPLEX: "复式",
};

const REPORT_TIME = new Intl.DateTimeFormat("zh-CN", {
  timeZone: "Asia/Shanghai", dateStyle: "medium", timeStyle: "short", hour12: false,
});

function prizeLabel(level: number) {
  return ["未中奖", "一等奖", "二等奖", "三等奖", "四等奖", "五等奖", "六等奖"][level] ?? "—";
}

function Balls({ reds, blues, redHits = reds, blueHits = blues }: {
  reds: number[]; blues: number[]; redHits?: number[]; blueHits?: number[];
}) {
  return (
    <div className={styles.balls}>
      {([["red", reds, redHits, "红球"], ["blue", blues, blueHits, "蓝球"]] as const)
        .filter(([, values]) => values.length > 0).map(([color, values, hits, label]) => (
        <div key={color} className={styles.ballGroup} role="group" aria-label={label}>
          {values.map((number) => (
            <span className={`draw-summary-ball ${color} ${hits.includes(number) ? styles.hitBall : styles.dimBall}`}
              key={number} aria-label={`${label} ${number}${hits.includes(number) ? "，命中" : ""}`}>
              {String(number).padStart(2, "0")}
            </span>
          ))}
        </div>
      ))}
    </div>
  );
}

function FeatureTable({ items, blue }: { items: FeatureReview[]; blue: boolean }) {
  const label = blue ? "蓝球特征" : "红球特征";
  return (
    <div className={styles.featureGroup}>
      <h3><span className={`draw-trend-dot ${blue ? "blue" : "red"}`} />{label}</h3>
      <div className="table-wrap">
        <table className={`data-table ${styles.featureTable}`} aria-label={label}>
          <thead><tr><th>特征</th><th>开奖实际值</th><th>主推</th><th>备选</th><th>结果</th></tr></thead>
          <tbody>
            {items.map((item) => {
              const [status, tone] = FEATURE_STATUS[item.status];
              return (
                <tr key={item.code}>
                  <td>{item.label}</td>
                  <td className={styles.value}>{item.actual ?? "—"}</td>
                  <td className={styles.value}>
                    <span className={item.matchedRank === 1 ? styles.matched : undefined}>{item.candidates[0] ?? "—"}</span>
                  </td>
                  <td className={styles.value}>
                    <div className={styles.alternatives}>
                      {item.candidates.length < 2 ? "—" : item.candidates.slice(1).map((value, index) => (
                        <span key={value} className={item.matchedRank === index + 2 ? styles.matched : undefined}>{value}</span>
                      ))}
                    </div>
                  </td>
                  <td><span className={`hit-kpi ${tone}`}>{status}</span></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function TicketLine({ result, winningBlue, prefix }: {
  result: TicketResult; winningBlue: number; prefix?: string;
}) {
  return (
    <div className={styles.ticketLine}>
      <div className={styles.ticketNumbers}>
        {prefix && <span className={styles.muted}>{prefix}</span>}
        <Balls reds={result.reds} blues={result.blues} redHits={result.redHits}
          blueHits={result.blueHit ? [winningBlue] : []} />
      </div>
      <div className={styles.ticketStats}>
        <span>红球 {result.redHits.length} 中</span>
        <span>{result.blueHit ? "蓝球命中" : "蓝球未中"}</span>
        {result.totalBets > 1 && <span>{result.winningBets} / {result.totalBets} 注中奖</span>}
        <span className={`hit-kpi ${result.bestPrize ? "main" : "miss"}`}>
          {result.bestPrize > 0 && result.totalBets > 1 ? "最高 " : ""}{prizeLabel(result.bestPrize)}
        </span>
      </div>
      {result.totalBets > 1 && result.prizes.length > 0 && (
        <div className={styles.prizes}>
          {result.prizes.map(({ level, bets }) => <span className="hit-kpi alt" key={level}>{prizeLabel(level)} {bets} 注</span>)}
        </div>
      )}
    </div>
  );
}

function Recommendation({ report, winningBlue, open }: {
  report: RecommendationReview; winningBlue: number; open: boolean;
}) {
  const created = new Date(report.generatedAt);
  const trend = report.redTrend;
  return (
    <details className={styles.recommendation} open={open}>
      <summary>
        <span className={styles.reportTime}>{REPORT_TIME.format(created)}</span>
        <span className={styles.muted}>{report.tickets.length} 组 · {report.totalBets} 注 · {report.winningBets} 注中奖</span>
        <span className={`hit-kpi ${report.bestPrize ? "main" : "miss"}`}>
          {report.bestPrize > 0 ? "最高 " : ""}{prizeLabel(report.bestPrize)}
        </span>
      </summary>
      <div className={styles.tickets}>
        {trend && (
          <section className={styles.ticket} aria-label="走势图红球候选复盘">
            <h3>走势图红球候选</h3>
            {trend.candidates.length > 0 ? (
              <div className={styles.ticketLine}>
                <Balls reds={trend.candidates} blues={[]} redHits={trend.hits} />
                <span className={styles.muted}>候选池命中 {trend.hits.length} / {trend.candidates.length} 个</span>
              </div>
            ) : <p className={styles.muted}>本次走势图暂无红球候选。</p>}
          </section>
        )}
        {report.tickets.map((ticket, index) => (
          <article className={styles.ticket} key={index}>
            <h3>{TICKET_LABEL[ticket.kind]}{ticket.label ? ` · ${ticket.label}` : ""}</h3>
            {ticket.original && <TicketLine result={ticket.original} winningBlue={winningBlue} prefix="优化前" />}
            <TicketLine result={ticket.result} winningBlue={winningBlue} prefix={ticket.original ? "优化后" : undefined} />
            {trend && trend.candidates.length > 0 && ticket.kind === "RECOMMENDED" && (
              <TicketTrendHits result={ticket.result} candidates={trend.candidates} />
            )}
          </article>
        ))}
      </div>
    </details>
  );
}

function TicketTrendHits({ result, candidates }: { result: TicketResult; candidates: number[] }) {
  const selected = result.reds.filter((number) => candidates.includes(number));
  const hits = selected.filter((number) => result.redHits.includes(number));
  return (
    <div className={styles.ticketLine}>
      <span className={styles.muted}>选用走势候选 {selected.length} 个 · 命中 {hits.length} 个</span>
      {selected.length > 0 && <Balls reds={selected} blues={[]} redHits={hits} />}
    </div>
  );
}

export default function ReviewReport({ review }: { review: DrawReview }) {
  const { draw, features, featureSummary: counts, recommendations } = review;
  return (
    <>
      <section className={`filter-card ${styles.draw}`} aria-labelledby="review-draw">
        <div>
          <h2 id="review-draw">第 {draw.period} 期开奖号码</h2>
          <time className={styles.muted} dateTime={draw.openDate}>{draw.openDate}</time>
        </div>
        <Balls reds={draw.reds} blues={[draw.blue]} />
      </section>
      <LearningProgress review={review} />
      <section className={`filter-card ${styles.section}`} aria-labelledby="review-features">
        <div className={styles.sectionHeader}>
          <h2 id="review-features">特征预测复盘</h2>
          <div className="hit-summary">
            <span className="hit-kpi main">主推命中 {counts.mainHits}</span>
            <span className="hit-kpi alt">备选命中 {counts.alternateHits}</span>
            <span className="hit-kpi miss">未命中 {counts.misses}</span>
            <span className="hit-kpi">未预测 {counts.notPredicted}</span>
            {counts.noActual > 0 && <span className="hit-kpi">历史样本不足 {counts.noActual}</span>}
          </div>
        </div>
        {counts.notPredicted === features.length && <p className={styles.muted}>本期没有开奖前生成的特征预测。</p>}
        <FeatureTable items={features.filter((entry) => !entry.blue)} blue={false} />
        <FeatureTable items={features.filter((entry) => entry.blue)} blue />
      </section>
      <section className={`filter-card ${styles.section}`} aria-labelledby="review-tickets">
        <h2 id="review-tickets">号码推荐复盘</h2>
        {recommendations.length === 0 && <p className={styles.muted}>本期没有可复盘的开奖前号码推荐。</p>}
        {recommendations.map((report, index) => (
          <Recommendation key={report.fileName} report={report} winningBlue={draw.blue} open={index === 0} />
        ))}
      </section>
    </>
  );
}
