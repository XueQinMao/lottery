interface Draw {
  period: string;
  reds: number[];
  blue: number;
}

export default function DrawSummary({ draw, firstPeriod, count }: {
  draw: Draw; firstPeriod: string; count: number;
}) {
  return (
    <div className="draw-summary" aria-label="展示区间与末期开奖">
      <span className="draw-summary-label">末期开奖</span>
      <span className="draw-summary-period">{draw.period} 期</span>
      <div className="draw-summary-balls" aria-label="开奖号码">
        {draw.reds.map((number) => <span className="draw-summary-ball red" key={number}>{String(number).padStart(2, "0")}</span>)}
        <span className="draw-summary-ball blue">{String(draw.blue).padStart(2, "0")}</span>
      </div>
      <span className="draw-summary-range">{firstPeriod} — {draw.period} · 实际 {count} 期</span>
    </div>
  );
}
