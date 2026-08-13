export type BallType = "red" | "blue";

export interface TrendStats {
  maxOmission: number;
  avgOmission: number;
  currentOmission: number;
  indexMean: number;
  hitCount: number;
  totalPeriods: number;
}

export interface TrendAnalysisVo {
  ballType: BallType;
  ball: number;
  periods: string[];
  omissions: number[];
  indexValues: number[];
  ma5: (number | null)[];
  ma10: (number | null)[];
  ma20: (number | null)[];
  stats: TrendStats;
  arrangement: number;
}

export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}
