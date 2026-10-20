export interface ZoneTrendSeries {
  code: string;
  values: number[];
}

export interface ZoneTrendSnapshot {
  endPeriod: string;
  periods: string[];
  counts: ZoneTrendSeries[];
  ratios: ZoneTrendSeries[];
}

export interface ZoneTrendImage {
  code: string;
  dataUrl: string;
}

export interface ZoneTrendAnalysis {
  model: string;
  generatedAt: string;
  fileName: string;
  input: ZoneTrendSnapshot;
  images: { code: string; sha256: string; width: number; height: number }[];
  inference: {
    countTrends: {
      zone: number;
      observation: string;
      nextInference: string;
      evidencePeriods: string[];
    }[];
    ratioTrend: string;
    candidates: {
      ratio: string;
      reason: string;
      caution: string;
      evidencePeriods: string[];
    }[];
    counterEvidence: string;
  };
}
