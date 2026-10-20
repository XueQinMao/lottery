import type { FeatureForecast, PatternFeatureAnalysis } from "@/types/llm-analysis";

export type FeatureHitType = "MAIN" | "ALT" | "MISS";

export interface FeatureHit {
  code?: string;
  label?: string;
  actual?: string;
  mainValue?: string;
  alternatives?: string[];
  hitType?: FeatureHitType;
}

export interface FeatureHitSummary {
  hits?: FeatureHit[];
  mainHitCount?: number;
  altHitCount?: number;
  missCount?: number;
}

export interface FeatureCoverage {
  code: string;
  label: string;
  mainValue: string;
  alternatives: string[];
  mainHitBets: number;
  altHitBets: number;
}

export interface AdjustedTicket {
  id?: string;
  category?: "OPTIMIZED" | "RECOMMENDED" | "FULL_MATCH";
  originalRedBalls?: number[];
  originalBlueBall?: number;
  adjustedRedBalls?: number[];
  adjustedBlueBall?: number;
  reason?: string;
}

export interface SingleTicket {
  name?: string;
  redBalls?: number[];
  blueBall?: number;
  totalBets?: number;
  basis?: string;
}

export interface ComplexTicket {
  name?: string;
  redBalls?: number[];
  blueBalls?: number[];
  totalBets?: number;
  basis?: string;
}

export interface FinalRecommendation {
  singleTickets?: SingleTicket[];
  complexTicket?: ComplexTicket;
}

export type LlmRecommendTaskStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAILED";
export type LlmRecommendTaskPhase = "ANALYZING_NUMBER_TREND" | "GENERATING_RECOMMENDATION";

export interface LlmRecommendTask {
  taskId: string;
  status: LlmRecommendTaskStatus;
  useNumberTrend?: boolean;
  phase?: LlmRecommendTaskPhase;
  message?: string;
  fileName?: string;
}

export interface NumberTrendObservation {
  ball: "RED" | "BLUE";
  number: number;
}

export interface LotteryAdjustResp {
  adjustedTickets?: AdjustedTicket[];
  finalRecommendation?: FinalRecommendation;
  conclusion?: string;
  fileName?: string;
  featureForecast?: FeatureForecast;
  targetPeriod?: string;
  sourcePeriod?: string;
  complexHits?: FeatureCoverage[];
  featureAnalysis?: PatternFeatureAnalysis[];
  numberTrendObservations?: NumberTrendObservation[];
  numberTrendImages?: { code: "red" | "blue"; dataUrl: string }[];
  /** 与 adjustedTickets 按下标对齐 */
  adjustedTicketHits?: FeatureHitSummary[];
  /** 与 finalRecommendation.singleTickets 按下标对齐 */
  finalSingleHits?: FeatureHitSummary[];
}
