export type FeatureStatus = "MAIN_HIT" | "ALTERNATE_HIT" | "MISS" | "NOT_PREDICTED" | "NO_ACTUAL";

export interface FeatureReview {
  code: string;
  label: string;
  blue: boolean;
  actual: string | null;
  candidates: string[];
  matchedRank: number | null;
  status: FeatureStatus;
}

export interface TicketResult {
  reds: number[];
  blues: number[];
  redHits: number[];
  blueHit: boolean;
  totalBets: number;
  winningBets: number;
  bestPrize: number;
  prizes: { level: number; bets: number }[];
}

export interface RecommendationReview {
  fileName: string;
  generatedAt: string;
  totalBets: number;
  winningBets: number;
  bestPrize: number;
  redTrend?: { candidates: number[]; hits: number[] } | null;
  tickets: {
    kind: "OPTIMIZED" | "RECOMMENDED" | "FULL_MATCH" | "SINGLE" | "COMPLEX";
    label: string | null;
    result: TicketResult;
    original: TicketResult | null;
  }[];
}

export interface DrawReview {
  draw: { period: string; openDate: string; reds: number[]; blue: number };
  featureSummary: {
    mainHits: number;
    alternateHits: number;
    misses: number;
    notPredicted: number;
    noActual: number;
  };
  features: FeatureReview[];
  recommendations: RecommendationReview[];
  learning?: LearningReport | null;
}

export interface LearningReport {
  requiredPeriods: number;
  experiments: {
    scope: string;
    change: string | null;
    formalVersion: number;
    trialVersion: number;
    pairedPeriods: number;
    wins: number;
    losses: number;
    averageLift: number;
    status: "BASELINE" | "WAITING" | "RUNNING" | "PROMOTED" | "REJECTED";
    outcome: {
      formalCandidates: string[] | null;
      trialCandidates: string[] | null;
      formalRank: number | null;
      trialRank: number | null;
      formalNumbers: LearningNumberScore | null;
      trialNumbers: LearningNumberScore | null;
      randomNumbers: LearningNumberScore | null;
    } | null;
  }[];
}

export interface LearningNumberScore {
  bets: number;
  averageRedHits: number;
  blueHitBets: number;
  winningBets: number;
  bestPrize: number;
}
