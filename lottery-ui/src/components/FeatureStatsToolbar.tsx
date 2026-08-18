"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import SampleQueryBar from "@/components/SampleQueryBar";

interface Props {
  sampleSize: number;
  onSampleSizeChange: (size: number) => void;
  endPeriod: string;
  onEndPeriodChange: (period: string) => void;
  onApplyPeriod: () => void;
}

export default function FeatureStatsToolbar({
  sampleSize,
  onSampleSizeChange,
  endPeriod,
  onEndPeriodChange,
  onApplyPeriod,
}: Props) {
  const pathname = usePathname();

  return (
    <div className="feature-toolbar">
      <div className="type-tabs">
        <Link
          href="/feature-stats"
          className={`type-tab ${pathname === "/feature-stats" ? "active" : ""}`}
        >
          和值 / 差值
        </Link>
        <Link
          href="/feature-stats/ratio"
          className={`type-tab ${pathname === "/feature-stats/ratio" ? "active" : ""}`}
        >
          质合比 / 奇偶比
        </Link>
      </div>
      <SampleQueryBar
        sampleSize={sampleSize}
        onSampleSizeChange={onSampleSizeChange}
        endPeriod={endPeriod}
        onEndPeriodChange={onEndPeriodChange}
        onApply={onApplyPeriod}
      />
    </div>
  );
}
