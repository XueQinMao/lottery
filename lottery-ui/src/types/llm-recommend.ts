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
  singleTickets?: SingleTicket[];
  complexTicket?: ComplexTicket;
}

export type LlmRecommendTaskMode = "FEATURE" | "CACHE";

export type LlmRecommendTaskStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAILED";

export interface LlmRecommendTask {
  taskId: string;
  status: LlmRecommendTaskStatus;
  mode?: LlmRecommendTaskMode;
  message?: string;
  fileName?: string;
}

export interface LotteryAdjustResp {
  adjustedTickets?: AdjustedTicket[];
  finalRecommendation?: FinalRecommendation;
  conclusion?: string;
  fileName?: string;
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
