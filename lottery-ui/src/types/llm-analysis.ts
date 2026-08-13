export interface KillItem {
  ball: number;
  score: number;
  reason?: string;
}

export interface KillNumberResult {
  hardKillRed?: KillItem[];
  hardKillBlue?: KillItem[];
  basis?: string;
}

export interface ColdHotAnalysis {
  redHotBalls?: number[];
  redWarmBalls?: number[];
  redColdBalls?: number[];
  blueHotBalls?: number[];
  blueWarmBalls?: number[];
  blueColdBalls?: number[];
  basis?: string;
}

export interface ThreeZoneCandidate {
  ratio: string;
  probability: number;
  frequencyProb: number;
  markovProb: number;
  reason?: string;
}

export interface ThreeZoneRatioPredict {
  candidates?: ThreeZoneCandidate[];
  lastRatio?: string;
  basis?: string;
}

export interface TrendAnalysis {
  risingRedBalls?: number[];
  fallingRedBalls?: number[];
  risingBlueBalls?: number[];
  fallingBlueBalls?: number[];
}

export interface SampleOverview {
  totalCount?: number;
  avgSum?: number;
  avgSpan?: number;
  avgOddEven?: string;
  avgBigSmall?: string;
}

export interface BankerCandidate {
  balls: string;
  count: number;
  frequency: number;
}

export interface BankerAnalysis {
  oneBanker?: BankerCandidate[];
  twoBanker?: BankerCandidate[];
  threeBanker?: BankerCandidate[];
}

export interface CountMap {
  [key: string]: number;
}

export interface LotteryAnalysisResp {
  sampleOverview?: SampleOverview;
  oddEvenRatio?: CountMap;
  bigSmallRatio?: CountMap;
  primeCompositeRatio?: CountMap;
  ratio012?: CountMap;
  span?: CountMap;
  sumRange?: CountMap;
  sumDigit?: CountMap;
  threeZoneRatio?: CountMap;
  zone1Count?: CountMap;
  zone2Count?: CountMap;
  zone3Count?: CountMap;
  banker?: BankerAnalysis;
  killNumbers?: KillNumberResult;
  coldHotAnalysis?: ColdHotAnalysis;
  predictedThreeZoneRatio?: ThreeZoneRatioPredict;
  trendAnalysis?: TrendAnalysis;
  conclusion?: string;
}
