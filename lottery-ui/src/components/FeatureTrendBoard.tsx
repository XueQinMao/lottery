import { ArrowClockwise, CaretRight } from "@phosphor-icons/react";
import type { FeatureTrendItem, FeatureTrendSummary } from "@/types/feature-trend-forecast";

interface Props {
  items: Pick<FeatureTrendItem, "code" | "label" | "blue">[];
  summaries: FeatureTrendSummary[];
  errors: Record<string, string>;
  active: string[];
  retryDisabled: boolean;
  onDetail: (summary: FeatureTrendSummary) => void;
  onRetry: (code: string) => void;
}

const TEMPERATURE_ZONES = ["总体", "一区", "二区", "三区"];

function ForecastValue({ code, value }: { code: string; value: string }) {
  if (code === "redTemperature") {
    return (
      <dl className="forecast-temperature">
        {value.split("|").map((band, index) => (
          <div key={TEMPERATURE_ZONES[index]}><dt>{TEMPERATURE_ZONES[index]}</dt><dd>{band}</dd></div>
        ))}
      </dl>
    );
  }
  if (code === "redEndings") {
    return <span className="forecast-endings">{value.split(":").map((count, index) =>
      Number(count) > 0 ? <span key={index}>{index}尾 ×{count}</span> : null)}</span>;
  }
  return <span>{value}</span>;
}

export default function FeatureTrendBoard({ items, summaries, errors, active, retryDisabled, onDetail, onRetry }: Props) {
  const byCode = new Map(summaries.map((summary) => [summary.featureCode, summary]));
  return (
    <div className="forecast-board">
      {[false, true].map((blue) => {
        const group = items.filter((item) => item.blue === blue);
        if (!group.length) return null;
        return (
          <section className={`forecast-group ${blue ? "is-blue" : "is-red"}`} key={String(blue)}>
            <h2><span className="forecast-ball" aria-hidden="true" />{blue ? "蓝球特征" : "红球特征"}</h2>
            <div className="forecast-row-labels" aria-hidden="true"><span>特征</span><span>预测结果</span><span>操作</span></div>
            <ul className="forecast-result-list">
              {group.map((item) => {
                const summary = byCode.get(item.code);
                const error = errors[item.code];
                const running = active.includes(item.code);
                return (
                  <li className="forecast-result-row" key={item.code}>
                    <div className="forecast-feature-name">
                      <h3>{item.label}</h3>
                      {item.code === "redTemperature" && <small>冷 : 温 : 热</small>}
                      {item.code === "redSize" && <small>小 : 大</small>}
                    </div>
                    <div className="forecast-result-value">
                      {summary?.values.length ? (
                        <ol className="forecast-values" aria-label="候选值">
                          {summary.values.map((value) => <li key={value}><ForecastValue code={item.code} value={value} /></li>)}
                        </ol>
                      ) : summary ? <p className="muted">未形成明确候选</p>
                        : error ? <p className="forecast-item-error">{error}</p>
                          : <p className="muted">{running ? "分析中…" : "待分析"}</p>}
                    </div>
                    {summary ? (
                      <button className="forecast-detail-link" type="button" onClick={() => onDetail(summary)}
                        aria-label={`查看${blue ? "蓝球" : "红球"}${item.label}详情`}>
                        详情<CaretRight size={12} aria-hidden="true" />
                      </button>
                    ) : (
                      <button className="forecast-detail-link" type="button" onClick={() => onRetry(item.code)}
                        disabled={retryDisabled} aria-busy={running} aria-label={`${error ? "重试" : "单独分析"}${item.label}`}>
                        <ArrowClockwise size={14} aria-hidden="true" />{error ? "重试" : "单独分析"}
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
