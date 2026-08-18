"use client";

import type {
  ColdHotAnalysis,
  FeatureForecast,
  FeatureForecastItem,
  KillNumberResult,
  LotteryAnalysisResp,
  ThreeZoneRatioPredict,
  TrendAnalysis,
} from "@/types/llm-analysis";

function pad(n: number) {
  return String(n).padStart(2, "0");
}

function pct(v: number | undefined) {
  if (v == null || Number.isNaN(v)) return "-";
  return `${(v * 100).toFixed(1)}%`;
}

function gapTrendLabel(t?: string) {
  switch (t) {
    case "heating":
      return "走热";
    case "cooling":
      return "走冷";
    case "stable":
      return "平稳";
    case "unknown":
      return "不足";
    default:
      return "-";
  }
}

function hasItems<T>(arr?: T[] | null): arr is T[] {
  return Array.isArray(arr) && arr.length > 0;
}

function BallChip({
  n,
  tone = "red",
}: {
  n: number;
  tone?: "red" | "blue" | "hot" | "warm" | "cold" | "kill" | "rise" | "fall";
}) {
  return <span className={`chip chip-${tone}`}>{pad(n)}</span>;
}

function BallList({
  balls,
  tone,
  empty = "无",
}: {
  balls?: number[];
  tone: "red" | "blue" | "hot" | "warm" | "cold" | "kill" | "rise" | "fall";
  empty?: string;
}) {
  if (!hasItems(balls)) {
    return <span className="muted">{empty}</span>;
  }
  return (
    <div className="chip-row">
      {balls.map((n) => (
        <BallChip key={`${tone}-${n}`} n={n} tone={tone} />
      ))}
    </div>
  );
}

function KillSection({ data }: { data?: KillNumberResult }) {
  if (!data) return null;
  const hasRed = hasItems(data.hardKillRed);
  const hasBlue = hasItems(data.hardKillBlue);
  if (!hasRed && !hasBlue && !data.basis) return null;
  const redBalls = data.hardKillRed?.map((it) => it.ball).filter((n) => n != null);
  const blueBalls = data.hardKillBlue?.map((it) => it.ball).filter((n) => n != null);
  return (
    <section className="section">
      <h2>杀号清单</h2>
      {data.basis && <p className="basis">{data.basis}</p>}
      <div className="chart-container">
        <div className="chart-title">
          <span>红球</span>
          {hasRed && <span className="tag">{data.hardKillRed!.length} 个</span>}
        </div>
        <div className="kv-grid">
          <div>
            <div className="kv-label">硬杀</div>
            <BallList balls={redBalls} tone="kill" />
          </div>
        </div>
      </div>
      <div className="chart-container">
        <div className="chart-title">
          <span>蓝球</span>
          {hasBlue && <span className="tag">{data.hardKillBlue!.length} 个</span>}
        </div>
        <div className="kv-grid">
          <div>
            <div className="kv-label">硬杀</div>
            <BallList balls={blueBalls} tone="blue" />
          </div>
        </div>
      </div>
    </section>
  );
}

function ColdHotSection({ data }: { data?: ColdHotAnalysis }) {
  if (!data) return null;
  return (
    <section className="section">
      <h2>冷热温号码</h2>
      {data.basis && <p className="basis">{data.basis}</p>}
      <div className="chart-container">
        <div className="chart-title">
          <span>红球</span>
        </div>
        <div className="kv-grid">
          <div>
            <div className="kv-label">热号</div>
            <BallList balls={data.redHotBalls} tone="hot" />
          </div>
          <div>
            <div className="kv-label">温号</div>
            <BallList balls={data.redWarmBalls} tone="warm" />
          </div>
          <div>
            <div className="kv-label">冷号</div>
            <BallList balls={data.redColdBalls} tone="cold" />
          </div>
        </div>
      </div>
      <div className="chart-container">
        <div className="chart-title">
          <span>蓝球</span>
        </div>
        <div className="kv-grid">
          <div>
            <div className="kv-label">热号</div>
            <BallList balls={data.blueHotBalls} tone="hot" />
          </div>
          <div>
            <div className="kv-label">温号</div>
            <BallList balls={data.blueWarmBalls} tone="warm" />
          </div>
          <div>
            <div className="kv-label">冷号</div>
            <BallList balls={data.blueColdBalls} tone="cold" />
          </div>
        </div>
      </div>
    </section>
  );
}

function ThreeZoneSection({ data }: { data?: ThreeZoneRatioPredict }) {
  if (!data) return null;
  const candidates = data.candidates;
  return (
    <section className="section">
      <h2>三区比预测</h2>
      {data.lastRatio && (
        <p className="basis">
          最近一期实际三区比：<strong>{data.lastRatio}</strong>
        </p>
      )}
      {data.basis && <p className="basis">{data.basis}</p>}
      {hasItems(candidates) && (
        <div className="chart-container">
          <div className="chart-title">
            <span>Top 候选（按综合概率）</span>
          </div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>三区比</th>
                  <th>综合概率</th>
                  <th>频率先验</th>
                  <th>马尔可夫</th>
                  <th>理由</th>
                </tr>
              </thead>
              <tbody>
                {candidates.map((c) => (
                  <tr key={c.ratio}>
                    <td>
                      <strong>{c.ratio}</strong>
                    </td>
                    <td>{pct(c.probability)}</td>
                    <td>{pct(c.frequencyProb)}</td>
                    <td>{pct(c.markovProb)}</td>
                    <td className="reason-cell">{c.reason || "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}

function FeatureForecastSection({ data }: { data?: FeatureForecast }) {
  if (!data) return null;
  const rows: { label: string; item?: FeatureForecastItem }[] = [
    { label: "奇偶比", item: data.oddEven },
    { label: "大小比", item: data.bigSmall },
    { label: "质合比", item: data.primeComposite },
    { label: "012路比", item: data.ratio012 },
    { label: "跨度", item: data.span },
    { label: "和值区间", item: data.sumRange },
    { label: "和值尾数", item: data.sumTail },
    { label: "三区比", item: data.threeZone },
    { label: "一区个数", item: data.zone1Count },
    { label: "二区个数", item: data.zone2Count },
    { label: "三区个数", item: data.zone3Count },
    { label: "蓝球奇偶", item: data.blueOddEven },
    { label: "蓝球大小", item: data.blueBigSmall },
    { label: "蓝球大小奇偶", item: data.blueBigSmallOddEven },
    { label: "蓝球012路", item: data.blueRatio012 },
  ].filter((r) => r.item?.value);
  if (rows.length === 0 && !data.basis) return null;
  return (
    <section className="section">
      <h2>形态推算</h2>
      {data.basis && <p className="basis">{data.basis}</p>}
      {rows.length > 0 && (
        <div className="chart-container">
          <div className="chart-title">
            <span>下一期目标值 / 区间</span>
          </div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>形态</th>
                  <th>主推</th>
                  <th>备选</th>
                  <th>间隔趋势</th>
                  <th>eta</th>
                  <th>窗口</th>
                  <th>置信度</th>
                  <th>依据</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.label}>
                    <td>{r.label}</td>
                    <td>
                      <strong>{r.item?.value}</strong>
                    </td>
                    <td>{r.item?.alternatives?.join("、") || "-"}</td>
                    <td>{gapTrendLabel(r.item?.gapTrend)}</td>
                    <td>
                      {r.item?.eta != null
                        ? `${r.item.eta}（Ĝ=${r.item.predictedGap ?? "-"}）`
                        : "-"}
                    </td>
                    <td>{r.item?.dueWindow == null ? "-" : r.item.dueWindow ? "是" : "否"}</td>
                    <td>{pct(r.item?.confidence)}</td>
                    <td className="reason-cell">{r.item?.reason || "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}

function TrendSection({ data }: { data?: TrendAnalysis }) {
  if (!data) return null;
  return (
    <section className="section">
      <h2>趋势均线（堆叠 + 斜率）</h2>
      <div className="chart-container">
        <div className="chart-title">
          <span>红球</span>
        </div>
        <div className="kv-grid">
          <div>
            <div className="kv-label">回暖（空头抬头，优先）</div>
            <BallList balls={data.reboundingRedBalls} tone="rise" />
          </div>
          <div>
            <div className="kv-label">上升（多头）</div>
            <BallList balls={data.risingRedBalls} tone="rise" />
          </div>
          <div>
            <div className="kv-label">转弱（多头下行）</div>
            <BallList balls={data.coolingRedBalls} tone="fall" />
          </div>
          <div>
            <div className="kv-label">趋冷（空头未抬头）</div>
            <BallList balls={data.fallingRedBalls} tone="fall" />
          </div>
        </div>
      </div>
      <div className="chart-container">
        <div className="chart-title">
          <span>蓝球</span>
        </div>
        <div className="kv-grid">
          <div>
            <div className="kv-label">回暖（空头抬头，优先）</div>
            <BallList balls={data.reboundingBlueBalls} tone="rise" />
          </div>
          <div>
            <div className="kv-label">上升（多头）</div>
            <BallList balls={data.risingBlueBalls} tone="rise" />
          </div>
          <div>
            <div className="kv-label">转弱（多头下行）</div>
            <BallList balls={data.coolingBlueBalls} tone="fall" />
          </div>
          <div>
            <div className="kv-label">趋冷（空头未抬头）</div>
            <BallList balls={data.fallingBlueBalls} tone="fall" />
          </div>
        </div>
      </div>
    </section>
  );
}

export default function AnalysisReport({ data }: { data: LotteryAnalysisResp }) {
  return (
    <>
      <KillSection data={data.killNumbers} />
      <ColdHotSection data={data.coldHotAnalysis} />
      <FeatureForecastSection data={data.featureForecast} />
      <ThreeZoneSection data={data.predictedThreeZoneRatio} />
      <TrendSection data={data.trendAnalysis} />
    </>
  );
}
