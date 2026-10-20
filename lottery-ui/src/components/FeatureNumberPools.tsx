import type { ColdHotAnalysis } from "@/types/llm-analysis";

const bands = [
  { key: "cold", label: "冷", red: "redColdBalls", blue: "blueColdBalls" },
  { key: "warm", label: "温", red: "redWarmBalls", blue: "blueWarmBalls" },
  { key: "hot", label: "热", red: "redHotBalls", blue: "blueHotBalls" },
] as const;

interface Props {
  sourcePeriod?: string;
  temperature?: ColdHotAnalysis;
  temperatureError: string;
}

export default function FeatureNumberPools({ sourcePeriod, temperature, temperatureError }: Props) {
  return (
    <section className="forecast-number-pools" aria-labelledby="forecast-pools-title">
      <header>
        <h2 id="forecast-pools-title">冷温热号码</h2>
        {sourcePeriod && <span className="muted">截至 {sourcePeriod} 期</span>}
      </header>
      {temperature ? (
        <div className="forecast-number-colors">
          {(["red", "blue"] as const).map((color) => (
            <section className="forecast-number-color" key={color} aria-label={color === "red" ? "红球冷温热" : "蓝球冷温热"}>
              <h3 data-color={color}>{color === "red" ? "红球" : "蓝球"}</h3>
              <dl>
                {bands.map((band) => (
                  <div className="forecast-number-band" key={band.key}>
                    <dt>{band.label}</dt>
                    <dd className="chip-row">
                      {temperature[band[color]]?.length ? temperature[band[color]]?.map((ball) => (
                        <span className={`chip chip-${band.key}`} key={ball}>{String(ball).padStart(2, "0")}</span>
                      )) : <span className="muted">无</span>}
                    </dd>
                  </div>
                ))}
              </dl>
            </section>
          ))}
        </div>
      ) : <p className="muted">{temperatureError || "暂无冷温热号码"}</p>}
      <div className="forecast-kill-placeholder">
        <h3>杀号</h3><span className="muted">待展示</span>
      </div>
    </section>
  );
}
