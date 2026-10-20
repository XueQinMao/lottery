import type { ZoneTrendAnalysis } from "@/types/zone-trend-analysis";

const ZONES = ["一区 · 1–11", "二区 · 12–22", "三区 · 23–33"];

export default function ZoneTrendReport({ data }: { data: ZoneTrendAnalysis }) {
  const { input, inference } = data;
  return (
    <div className="report-bento zone-trend-report">
      <section className="section report-card report-span-full">
        <div className="section-heading"><h2>三区走势联合分析</h2><span className="tag">图像 + 逐期数据</span></div>
        <p className="basis">{input.periods[0]} — {input.endPeriod} · 最近 {input.periods.length} 期 · {data.model}</p>
        <p className="muted">目标：截止 {input.endPeriod} 后的下一期开奖。以下是走势候选，不代表必然开奖结果。</p>
        <h3>三区比走势</h3>
        <p className="basis">{inference.ratioTrend}</p>
        <h3>下期联合候选</h3>
        <p className="muted">每行是一组完整的三区组合；各区候选个数由该比例直接展开。</p>
        {inference.candidates.length ? (
          <div className="table-wrap"><table className="data-table">
            <thead><tr><th>三区比</th><th>一区个数</th><th>二区个数</th><th>三区个数</th><th>走势依据</th><th>反例 / 失效条件</th></tr></thead>
            <tbody>{inference.candidates.map((candidate) => (
              <tr key={candidate.ratio}>
                <td><strong>{candidate.ratio}</strong></td>
                {candidate.ratio.split(":").map((count, index) => <td key={index}>{count}</td>)}
                <td className="reason-cell">{candidate.reason}<p className="muted">参考：{candidate.evidencePeriods.join("、")}</p></td>
                <td className="reason-cell">{candidate.caution}</td>
              </tr>
            ))}</tbody>
          </table></div>
        ) : <p className="basis">本次观察未形成明确的下期组合倾向。</p>}
      </section>
      {inference.countTrends.map((trend) => (
        <section className="section report-card" key={trend.zone}>
          <h2>{ZONES[trend.zone - 1]}</h2>
          <h3>个数走势观察</h3><p className="basis">{trend.observation}</p>
          <h3>下期推演</h3><p className="basis">{trend.nextInference}</p>
          <p className="muted">参考期号：{trend.evidencePeriods.join("、")}</p>
        </section>
      ))}
      <section className="section report-card">
        <h2>反向证据与局限</h2><p className="basis">{inference.counterEvidence}</p>
        <p className="muted">三区个数与三区比来自同一组开奖数据，本次作为同一个结构联合推演。</p>
      </section>
      <section className="section report-card report-span-full">
        <details><summary>核对本次分析的逐期特征数据</summary>
          <div className="table-wrap"><table className="data-table">
            <thead><tr><th>期号（旧 → 新）</th><th>一区</th><th>二区</th><th>三区</th><th>三区比</th></tr></thead>
            <tbody>{input.periods.map((period, index) => {
              const counts = input.counts.map((series) => series.values[index]);
              return <tr key={period}><td>{period}</td>{counts.map((n, i) => <td key={i}>{n}</td>)}<td>{counts.join(":")}</td></tr>;
            })}</tbody>
          </table></div>
        </details>
      </section>
    </div>
  );
}
