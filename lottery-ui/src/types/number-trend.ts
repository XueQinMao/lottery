export interface NumberTrendRow {
  period: string;
  openDate: string;
  reds: number[];
  blue: number;
  redOmissions: number[];
  blueOmissions: number[];
}

export interface NumberTrendStats {
  number: number;
  hitCount: number;
  averageOmission: number | null;
  maxOmission: number;
  maxConsecutive: number;
  currentOmission: number;
}

export interface NumberTrend {
  rows: NumberTrendRow[];
  redStats: NumberTrendStats[];
  blueStats: NumberTrendStats[];
  redCandidates?: number[];
}

export type NumberTrendMode = "all" | "red" | "blue";
