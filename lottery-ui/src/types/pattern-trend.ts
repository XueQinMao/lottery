export type PatternFeature =
  | "oddEven"
  | "bigSmall"
  | "primeComp"
  | "ratio012"
  | "span"
  | "sumRange"
  | "sumTail"
  | "threeZone"
  | "zone1Count"
  | "zone2Count"
  | "zone3Count"
  | "blueOddEven"
  | "blueBigSmall"
  | "blueBigSmallOddEven"
  | "blueRatio012";

export interface PatternTrendStats {
  maxOmission: number;
  avgOmission: number;
  currentOmission: number;
  hitCount: number;
  totalPeriods: number;
  theoreticalProb: number;
  theoreticalHits: number;
  index: number;
}

export interface PatternRatioOption {
  ratio: string;
  hitCount: number;
  theoreticalProb: number;
  theoreticalHits: number;
  index: number;
}

export interface PatternTrendVo {
  feature: PatternFeature;
  featureLabel: string;
  ratio: string;
  periods: string[];
  hits: boolean[];
  omissions: number[];
  indexValues: number[];
  latestPeriod: string;
  latestWinning: string;
  latestRatio: string;
  stats: PatternTrendStats;
  ratioOptions: PatternRatioOption[];
}
