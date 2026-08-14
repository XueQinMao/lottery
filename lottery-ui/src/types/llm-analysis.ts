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

export interface FeatureForecastItem {
  value?: string;
  alternatives?: string[];
  confidence?: number;
  reason?: string;
}

export interface FeatureForecast {
  oddEven?: FeatureForecastItem;
  bigSmall?: FeatureForecastItem;
  primeComposite?: FeatureForecastItem;
  ratio012?: FeatureForecastItem;
  span?: FeatureForecastItem;
  sumRange?: FeatureForecastItem;
  sumTail?: FeatureForecastItem;
  threeZone?: FeatureForecastItem;
  zone1Count?: FeatureForecastItem;
  zone2Count?: FeatureForecastItem;
  zone3Count?: FeatureForecastItem;
  blueOddEven?: FeatureForecastItem;
  blueBigSmall?: FeatureForecastItem;
  blueBigSmallOddEven?: FeatureForecastItem;
  blueRatio012?: FeatureForecastItem;
  basis?: string;
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
  sumTail?: CountMap;
  sumDigit?: CountMap;
  threeZoneRatio?: CountMap;
  zone1Count?: CountMap;
  zone2Count?: CountMap;
  zone3Count?: CountMap;
  killNumbers?: KillNumberResult;
  coldHotAnalysis?: ColdHotAnalysis;
  predictedThreeZoneRatio?: ThreeZoneRatioPredict;
  trendAnalysis?: TrendAnalysis;
  featureForecast?: FeatureForecast;
  conclusion?: string;
}
