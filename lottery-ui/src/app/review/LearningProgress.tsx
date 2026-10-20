import type { DrawReview, LearningNumberScore } from "@/types/draw-review";
import styles from "./review.module.css";

const STATUSES = {
  BASELINE: "正式版",
  WAITING: "等待配对",
  RUNNING: "试验中",
  PROMOTED: "已通过",
  REJECTED: "未通过",
};

function CandidateResult({ values, rank, actual }: { values: string[] | null; rank: number | null; actual: string | null }) {
  if (!values?.length) return <span>—</span>;
  const result = actual === null ? "待核验" : rank ? `第 ${rank} 个命中` : "未命中";
  return <span>{values.join(" · ")}（{result}）</span>;
}

function NumberResult({ score }: { score: LearningNumberScore | null }) {
  if (!score) return <span>—</span>;
  return <span>{score.averageRedHits.toFixed(3)} 红球/注 · 蓝球中 {score.blueHitBets} 注 · 中奖 {score.winningBets}/{score.bets} 注</span>;
}

export default function LearningProgress({ review }: { review: DrawReview }) {
  const report = review.learning;
  const experiments = report?.experiments.filter((item) => item.scope === "numbers"
    || item.trialVersion > 0 || item.formalVersion > 1) ?? [];
  const features = new Map(review.features.map((feature) => [feature.code, feature]));
  function featureLabel(code: string) {
    const feature = features.get(code);
    return feature ? `${feature.blue ? "蓝球" : "红球"}${feature.label}` : code;
  }

  return (
    <section className={`filter-card ${styles.section}`} aria-labelledby="review-learning">
      <h2 id="review-learning">策略学习进度</h2>
      {!report && <p className={styles.muted}>该期尚未归档，请同步开奖。</p>}
      {report && experiments.length === 0 && <p className={styles.muted}>本期已归档，正在积累启动试验的历史样本。</p>}
      {experiments.length > 0 && (
        <div className="table-wrap">
          <table className={`data-table ${styles.learningTable}`} aria-label="策略学习进度">
            <thead><tr><th>策略</th><th>版本与进度</th><th>当期正式版</th><th>当期试验版</th><th>随机参照</th></tr></thead>
            <tbody>
              {experiments.map((item) => {
                const numbers = item.scope === "numbers";
                const actual = features.get(item.scope)?.actual ?? null;
                const change = numbers ? (item.change ? `${featureLabel(item.change)}降权` : "原始特征权重")
                  : item.change === "SWAP_TOP_TWO" ? "交换前两位" : "使用模型顺序";
                return (
                  <tr key={item.scope}>
                    <td>{numbers ? "号码推荐" : featureLabel(item.scope)}<br /><span className={styles.muted}>{change}</span></td>
                    <td>
                      正式版 V{item.formalVersion}
                      {item.trialVersion > 0 && (
                        <>
                          <br />试验版 V{item.trialVersion} · {STATUSES[item.status]}
                          <br /><span className={styles.muted}>{item.pairedPeriods}/{report?.requiredPeriods} 期 · {item.wins} 胜 {item.losses} 负{item.pairedPeriods > 0 ? ` · 平均差 ${item.averageLift.toFixed(3)}` : ""}</span>
                        </>
                      )}
                    </td>
                    <td>{numbers ? <NumberResult score={item.outcome?.formalNumbers ?? null} />
                      : <CandidateResult values={item.outcome?.formalCandidates ?? null} rank={item.outcome?.formalRank ?? null} actual={actual} />}</td>
                    <td>{numbers ? <NumberResult score={item.outcome?.trialNumbers ?? null} />
                      : <CandidateResult values={item.outcome?.trialCandidates ?? null} rank={item.outcome?.trialRank ?? null} actual={actual} />}</td>
                    <td><NumberResult score={item.outcome?.randomNumbers ?? null} /></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
