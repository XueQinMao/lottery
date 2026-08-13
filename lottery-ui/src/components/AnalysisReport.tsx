"use client";

import type {
  BankerCandidate,
  ColdHotAnalysis,
  CountMap,
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

function hasItems<T>(arr?: T[] | null): arr is T[] {
  return Array.isArray(arr) && arr.length > 0;
}

function hasMap(map?: CountMap | null) {
  return !!map && Object.keys(map).length > 0;
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

function CountBars({ title, map }: { title: string; map?: CountMap }) {
  if (!hasMap(map) || !map) return null;
  const entries = Object.entries(map).sort((a, b) => b[1] - a[1]);
  const max = Math.max(...entries.map(([, v]) => v), 1);
  return (
    <div className="chart-container">
      <div className="chart-title">
        <span>{title}</span>
      </div>
      <div className="bar-list">
        {entries.map(([k, v]) => (
          <div key={k} className="bar-row">
            <span className="bar-label">{k}</span>
            <div className="bar-track">
              <div
                className="bar-fill"
                style={{ width: `${(v / max) * 100}%` }}
              />
            </div>
            <span className="bar-value">{v}</span>
          </div>
        ))}
      </div>
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

function TrendSection({ data }: { data?: TrendAnalysis }) {
  if (!data) return null;
  return (
    <section className="section">
      <h2>趋势均线</h2>
      <div className="chart-container">
        <div className="chart-title">
          <span>红球</span>
        </div>
        <div className="kv-grid">
          <div>
            <div className="kv-label">上升（多头，趋热）</div>
            <BallList balls={data.risingRedBalls} tone="rise" />
          </div>
          <div>
            <div className="kv-label">下降（空头，趋冷）</div>
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
            <div className="kv-label">上升（多头，趋热）</div>
            <BallList balls={data.risingBlueBalls} tone="rise" />
          </div>
          <div>
            <div className="kv-label">下降（空头，趋冷）</div>
            <BallList balls={data.fallingBlueBalls} tone="fall" />
          </div>
        </div>
      </div>
    </section>
  );
}

function BankerList({
  title,
  items,
}: {
  title: string;
  items?: BankerCandidate[];
}) {
  if (!hasItems(items)) return null;
  return (
    <div className="chart-container">
      <div className="chart-title">
        <span>{title}</span>
      </div>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>号码</th>
              <th>次数</th>
              <th>频率</th>
            </tr>
          </thead>
          <tbody>
            {items.slice(0, 10).map((it) => (
              <tr key={`${title}-${it.balls}`}>
                <td>{it.balls}</td>
                <td>{it.count}</td>
                <td>{pct(it.frequency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function AnalysisReport({ data }: { data: LotteryAnalysisResp }) {
  const ov = data.sampleOverview;
  const hasDist =
    hasMap(data.oddEvenRatio) ||
    hasMap(data.bigSmallRatio) ||
    hasMap(data.primeCompositeRatio) ||
    hasMap(data.ratio012) ||
    hasMap(data.span) ||
    hasMap(data.sumRange) ||
    hasMap(data.threeZoneRatio);

  return (
    <>
      {ov && (
        <div className="stats-panel">
          {ov.totalCount != null && (
            <div className="stat-card">
              <div className="label">样本期数</div>
              <div className="value">{ov.totalCount}</div>
            </div>
          )}
          {ov.avgSum != null && (
            <div className="stat-card">
              <div className="label">平均和值</div>
              <div className="value red">{ov.avgSum.toFixed(1)}</div>
            </div>
          )}
          {ov.avgSpan != null && (
            <div className="stat-card">
              <div className="label">平均跨度</div>
              <div className="value green">{ov.avgSpan.toFixed(1)}</div>
            </div>
          )}
          {ov.avgOddEven && (
            <div className="stat-card">
              <div className="label">平均奇偶</div>
              <div className="value yellow">{ov.avgOddEven}</div>
            </div>
          )}
          {ov.avgBigSmall && (
            <div className="stat-card">
              <div className="label">平均大小</div>
              <div className="value blue">{ov.avgBigSmall}</div>
            </div>
          )}
        </div>
      )}

      {data.conclusion && (
        <div className="chart-container">
          <div className="chart-title">
            <span>综合结论</span>
          </div>
          <p className="basis">{data.conclusion}</p>
        </div>
      )}

      <KillSection data={data.killNumbers} />
      <ColdHotSection data={data.coldHotAnalysis} />
      <ThreeZoneSection data={data.predictedThreeZoneRatio} />
      <TrendSection data={data.trendAnalysis} />

      {hasDist && (
        <section className="section">
          <h2>形态分布</h2>
          <CountBars title="奇偶比" map={data.oddEvenRatio} />
          <CountBars title="大小比" map={data.bigSmallRatio} />
          <CountBars title="质合比" map={data.primeCompositeRatio} />
          <CountBars title="012路比" map={data.ratio012} />
          <CountBars title="跨度" map={data.span} />
          <CountBars title="和值区间" map={data.sumRange} />
          <CountBars title="三区比" map={data.threeZoneRatio} />
          <CountBars title="一区个数" map={data.zone1Count} />
          <CountBars title="二区个数" map={data.zone2Count} />
          <CountBars title="三区个数" map={data.zone3Count} />
        </section>
      )}

      {data.banker && (
        <section className="section">
          <h2>胆码</h2>
          <BankerList title="1胆" items={data.banker.oneBanker} />
          <BankerList title="2胆" items={data.banker.twoBanker} />
          <BankerList title="3胆" items={data.banker.threeBanker} />
        </section>
      )}
    </>
  );
}
