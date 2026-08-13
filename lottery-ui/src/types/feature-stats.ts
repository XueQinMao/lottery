export interface FeatureStatsVo {
  periods: string[];
  sumValues: number[];
  sumAvg: number;
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
