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
  reboundingRedBalls?: number[];
  fallingRedBalls?: number[];
  coolingRedBalls?: number[];
  risingBlueBalls?: number[];
  reboundingBlueBalls?: number[];
  fallingBlueBalls?: number[];
  coolingBlueBalls?: number[];
}

export interface FeatureForecastItem {
  value?: string;
  alternatives?: string[];
  confidence?: number;
  reason?: string;
  /** heating / cooling / stable / unknown */
  gapTrend?: string;
  predictedGap?: number;
  currentOmission?: number;
  eta?: number;
  dueWindow?: boolean;
  score?: number;
  recentGaps?: number[];
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

/** 调优/推荐用特征报告：仅含选号约束模块，不含历史直方图 */
export interface LotteryAnalysisResp {
  killNumbers?: KillNumberResult;
  coldHotAnalysis?: ColdHotAnalysis;
  predictedThreeZoneRatio?: ThreeZoneRatioPredict;
  trendAnalysis?: TrendAnalysis;
  featureForecast?: FeatureForecast;
}
