"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export default function SiteNav() {
  const pathname = usePathname();
  const onTrend = pathname === "/";
  const onPattern = pathname.startsWith("/pattern-trend");
  const onFeature = pathname.startsWith("/feature-stats");
  const onLlm = pathname.startsWith("/llm-analysis");

  return (
    <nav className="site-nav">
      <Link href="/" className={`site-nav-link ${onTrend ? "active" : ""}`}>
        遗漏趋势
      </Link>
      <Link
        href="/pattern-trend"
        className={`site-nav-link ${onPattern ? "active" : ""}`}
      >
        形态指数
      </Link>
      <Link
        href="/feature-stats"
        className={`site-nav-link ${onFeature ? "active" : ""}`}
      >
        形态统计
      </Link>
      <Link
        href="/llm-analysis"
        className={`site-nav-link ${onLlm ? "active" : ""}`}
      >
        LLM 分析
      </Link>
    </nav>
  );
}
