import type { ZoneTrendImage } from "@/types/zone-trend-analysis";
import type { ColdHotAnalysis } from "@/types/llm-analysis";

export interface FeatureTrendInput {
  endPeriod: string;
  periods: string[];
  groups: {
    code: string;
    label: string;
    description: string;
    occurrence: boolean;
    min: number;
    max: number;
    series: { code: string; label: string; values: (number | null)[] }[];
  }[];
}

export interface FeatureTrendItem {
  code: string;
  label: string;
  blue: boolean;
  input: FeatureTrendInput;
}

export interface FeatureTrendPlan {
  sourcePeriod: string;
  targetPeriod: string;
  items: FeatureTrendItem[];
  coldHotAnalysis?: ColdHotAnalysis;
}

export interface FeatureTrendSummary {
  sourcePeriod: string;
  targetPeriod: string;
  featureCode: string;
  label: string;
  blue: boolean;
  model: string;
  values: string[];
  generatedAt: string;
}

export interface FeatureTrendDetail {
  summary: FeatureTrendSummary;
  evidence: {
    input: FeatureTrendInput;
    images: { code: string; sha256: string; width: number; height: number }[];
    inference: {
      observation: string;
      candidates: { value: string; reason: string; caution: string; evidencePeriods: string[] }[];
      counterEvidence: string;
    };
  };
}

export interface FeatureTrendSheet extends ZoneTrendImage {
  label: string;
  height: number;
}
