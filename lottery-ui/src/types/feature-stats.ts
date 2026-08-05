export interface FeatureStatsVo {
  periods: string[];
  sumValues: number[];
  sumAvg: number;
  /** 本期和值 − 上一期和值；首期无对照为 null */
  sumDeltaValues: (number | null)[];
  sumDeltaAvg: number;
  spanValues: number[];
  spanAvg: number;
  primeCounts: number[];
  primeRatios: string[];
  primeAvg: number;
  redOddCounts: number[];
  redOddEvenRatios: string[];
  redOddAvg: number;
  blueOddFlags: number[];
  blueOddEvenLabels: string[];
  blueOddAvg: number;
}
