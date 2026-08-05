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
    <section className="section report-card">
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
    <section className="section report-card">
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
    <section className="section report-card report-span-full">
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

const RED_FORECAST_ROWS: { key: keyof FeatureForecast; label: string }[] = [
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
];

const BLUE_FORECAST_ROWS: { key: keyof FeatureForecast; label: string }[] = [
  { key: "blueOddEven", label: "蓝球奇偶" },
  { key: "blueBigSmall", label: "蓝球大小" },
  { key: "blueBigSmallOddEven", label: "蓝球大小奇偶" },
  { key: "blueRatio012", label: "蓝球012路" },
];

function gapTrendClass(t?: string) {
  switch (t) {
    case "heating":
      return "trend-badge trend-heating";
    case "cooling":
      return "trend-badge trend-cooling";
    case "stable":
      return "trend-badge trend-stable";
    default:
      return "trend-badge";
  }
}

function etaLabel(item?: FeatureForecastItem) {
  if (item?.eta == null) return "-";
  if (item.dueWindow || item.eta <= 0) return "窗口内";
  return `约 ${item.eta} 期后`;
}

function ForecastTable({
  title,
  rows,
  data,
}: {
  title: string;
  rows: { key: keyof FeatureForecast; label: string }[];
  data?: FeatureForecast;
}) {
  return (
    <div className="chart-container">
      <div className="chart-title">
        <span>{title}</span>
      </div>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>形态</th>
              <th>主推</th>
              <th>备选</th>
              <th>冷热</th>
              <th>当前遗漏</th>
              <th>预计接入</th>
              <th>置信度</th>
              <th>依据</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const item = data?.[r.key] as FeatureForecastItem | undefined;
              return (
                <tr key={r.key}>
                  <td>{r.label}</td>
                  <td>
                    <strong>{item?.value || "-"}</strong>
                  </td>
                  <td>{item?.alternatives?.join("、") || "-"}</td>
                  <td>
                    <span className={gapTrendClass(item?.gapTrend)}>
                      {gapTrendLabel(item?.gapTrend)}
                    </span>
                  </td>
                  <td>{item?.currentOmission ?? "-"}</td>
                  <td>{etaLabel(item)}</td>
                  <td>{pct(item?.confidence)}</td>
                  <td className="reason-cell">{item?.reason || "-"}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function FeatureForecastSection({ data }: { data?: FeatureForecast }) {
  return (
    <section className="section report-card">
      <h2>形态推算</h2>
      {data?.basis ? (
        <p className="basis">{data.basis}</p>
      ) : (
        <p className="basis">下一期红球 / 蓝球形态目标（主推值、冷热与接入时机）。</p>
      )}
      <ForecastTable title="红球 11 维" rows={RED_FORECAST_ROWS} data={data} />
      <ForecastTable title="蓝球 4 维" rows={BLUE_FORECAST_ROWS} data={data} />
    </section>
  );
}

function TrendSection({ data }: { data?: TrendAnalysis }) {
  if (!data) return null;
  return (
    <section className="section report-card report-span-full">
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
    <div className="report-bento">
      <div className="report-span-full">
        <FeatureForecastSection data={data.featureForecast} />
      </div>
      <KillSection data={data.killNumbers} />
      <ColdHotSection data={data.coldHotAnalysis} />
      <ThreeZoneSection data={data.predictedThreeZoneRatio} />
      <TrendSection data={data.trendAnalysis} />
    </div>
  );
}
