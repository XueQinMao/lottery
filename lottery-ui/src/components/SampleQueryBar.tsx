"use client";

const SAMPLE_OPTIONS = [30, 50, 100];

interface Props {
  sampleSize: number;
  onSampleSizeChange: (size: number) => void;
  endPeriod: string;
  onEndPeriodChange: (period: string) => void;
  /** 点击查询 / 回车时提交（用于截止期号） */
  onApply?: () => void;
}

/**
 * 期数（30/50/100）+ 截止期号。截止期号空=最新；填写如 2026092 表示含该期往前推 N 期。
 */
export default function SampleQueryBar({
  sampleSize,
  onSampleSizeChange,
  endPeriod,
  onEndPeriodChange,
  onApply,
}: Props) {
  return (
    <div className="sample-query-bar">
      <div className="type-tabs">
        {SAMPLE_OPTIONS.map((n) => (
          <button
            key={n}
            type="button"
            className={`type-tab ${sampleSize === n ? "active" : ""}`}
            onClick={() => onSampleSizeChange(n)}
          >
            近 {n} 期
          </button>
        ))}
      </div>
      <div className="period-query">
        <label htmlFor="end-period-input">截止期号</label>
        <input
          id="end-period-input"
          type="text"
          inputMode="numeric"
          placeholder="空=最新，如 2026092"
          value={endPeriod}
          onChange={(e) => onEndPeriodChange(e.target.value.trim())}
          onKeyDown={(e) => {
            if (e.key === "Enter" && onApply) {
              onApply();
            }
          }}
        />
        {onApply && (
          <button type="button" className="type-tab" onClick={onApply}>
            查询
          </button>
        )}
      </div>
    </div>
  );
}
