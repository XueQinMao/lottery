import type { FeatureForecast } from "@/types/llm-analysis";

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

export interface AdjustedTicket {
  id?: string;
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
  danBalls?: number[];
  danBasis?: string;
  singleTickets?: SingleTicket[];
  complexTicket?: ComplexTicket;
}

export interface LotteryAdjustResp {
  adjustedTickets?: AdjustedTicket[];
  finalRecommendation?: FinalRecommendation;
  conclusion?: string;
  featureForecast?: FeatureForecast;
  /** 与 adjustedTickets 按下标对齐 */
  adjustedTicketHits?: FeatureHitSummary[];
  /** 与 finalRecommendation.singleTickets 按下标对齐 */
  finalSingleHits?: FeatureHitSummary[];
}

export interface AdjustHistoryFile {
  fileName: string;
  lastModified?: number;
  size?: number;
}
