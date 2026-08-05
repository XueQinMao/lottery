"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export default function FeatureStatsToolbar() {
  const pathname = usePathname();

  return (
    <div className="type-tabs">
      <Link
        href="/feature-stats"
        scroll={false}
        className={`type-tab ${pathname === "/feature-stats" ? "active" : ""}`}
      >
        和值 / 差值
      </Link>
      <Link
        href="/feature-stats/ratio"
        scroll={false}
        className={`type-tab ${pathname === "/feature-stats/ratio" ? "active" : ""}`}
      >
        质合比 / 奇偶比
      </Link>
    </div>
  );
}
