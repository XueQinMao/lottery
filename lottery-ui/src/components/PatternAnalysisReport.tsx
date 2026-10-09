import type { DrawRecord, PatternFeatureAnalysis } from "@/types/llm-analysis";

function balls(values: number[]) {
  return values.map((value) => String(value).padStart(2, "0")).join("、");
}

export default function PatternAnalysisReport({
  features,
  records,
}: {
  features?: PatternFeatureAnalysis[];
  records?: DrawRecord[];
}) {
  if (!features?.length && !records?.length) return null;
  return (
    <>
      {features?.map((feature) => (
        <section className="section report-card" key={feature.code}>
          <h2>{feature.label}</h2>
          <p className="muted">
            {feature.source === "llm-pattern" ? `最近${feature.sampleSize ?? 10}期大模型形态观察` : feature.source === "java" ? "Java指数分析" : "历史报告"}
            {feature.status === "UNAVAILABLE" ? " · 暂不可用" : ""}
          </p>
          <h3>一、形态规律总结</h3>
          <p className="basis" style={{ whiteSpace: "pre-wrap" }}>{feature.patternSummary || "暂无描述"}</p>
          {feature.status === "UNAVAILABLE" ? (
            <p className="basis" role="status">分析失败：{feature.failureReason || "历史报告未记录失败详情，请重新分析。"}</p>
          ) : (
            <>
              <h3>二、下期{feature.label}推演</h3>
              <p className="basis" style={{ whiteSpace: "pre-wrap" }}>{feature.nextPeriodInference || "暂无推演"}</p>
            </>
          )}
          {feature.status === "OK" && (
            <div className="kv-grid">
              <div>
                <div className="kv-label">倾向出现池</div>
                <strong>{feature.likelyValues?.join("、") || "无明确倾向"}</strong>
                <p className="basis">{feature.likelyReason}</p>
              </div>
              <div>
                <div className="kv-label">暂不优先池</div>
                <strong>{feature.unlikelyValues?.join("、") || "无明确倾向"}</strong>
                <p className="basis">{feature.unlikelyReason}</p>
              </div>
            </div>
          )}
          {feature.observations?.length ? (
            <details>
              <summary>查看逐期特征数据</summary>
              <p className="muted">
                {feature.code === "tail" ? "尾数顺序：0尾至9尾，每期合计6。" : feature.code === "coldWarmHot" ? "比例顺序：热:温:冷；使用该期开奖前30期分类。" : feature.code === "blueColdWarmHot" ? "该期蓝球所属热、温、冷档位；使用该期开奖前30期分类。" : `第1期最旧，第${feature.sampleSize ?? feature.observations.length}期最新。`}
              </p>
              <div className="table-wrap">
                <table className="data-table">
                  <thead><tr><th>序号</th><th>期号</th><th>红球</th><th>特征值</th></tr></thead>
                  <tbody>{feature.observations.map((row) => (
                    <tr key={row.period}>
                      <td>{row.position}</td><td>{row.period}</td><td>{balls(row.redBalls)}</td><td>{row.value}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            </details>
          ) : null}
        </section>
      ))}
      {records?.length ? (
        <section className="section report-card report-span-full">
          <h2>最近{records.length}期开奖</h2>
          <details>
            <summary>查看开奖明细（最旧 → 最新）</summary>
            <div className="table-wrap">
              <table className="data-table">
                <thead><tr><th>期号</th><th>红球</th><th>蓝球</th></tr></thead>
                <tbody>{records.map((row) => (
                  <tr key={row.period}><td>{row.period}</td><td>{balls(row.redBalls)}</td><td>{String(row.blueBall).padStart(2, "0")}</td></tr>
                ))}</tbody>
              </table>
            </div>
          </details>
        </section>
      ) : null}
    </>
  );
}
