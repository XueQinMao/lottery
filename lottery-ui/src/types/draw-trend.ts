export interface DrawTrendSeries {
  code: string;
  label: string;
  values: (number | null)[];
  average: number;
  total: number;
  sampleCount: number;
}

export interface DrawTrendGroup {
  code: string;
  label: string;
  description: string;
  occurrence: boolean;
  min: number;
  max: number;
  series: DrawTrendSeries[];
  detailLabels?: string[];
}

export interface DrawTrend {
  periods: string[];
  draws: { period: string; reds: number[]; blue: number }[];
  redFeatures: DrawTrendGroup[];
  blueFeatures: DrawTrendGroup[];
  coldHotBasis: string;
}
