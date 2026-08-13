"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const SAMPLE_OPTIONS = [30, 50, 100];

interface Props {
  sampleSize: number;
  onSampleSizeChange: (size: number) => void;
}

export default function FeatureStatsToolbar({
  sampleSize,
  onSampleSizeChange,
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
      <div className="type-tabs">
        {SAMPLE_OPTIONS.map((n) => (
          <button
            key={n}
            type="button"
            className={`type-tab ${sampleSize === n ? "active" : ""}`}
            onClick={() => onSampleSizeChange(n)}
          >
            近 {n} 期
          </button>
        ))}
      </div>
    </div>
  );
}
