"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Brain,
  ChartLine,
  SidebarSimple,
  Sparkle,
  SquaresFour,
  Table,
} from "@phosphor-icons/react";

const NAV_ITEMS = [
  {
    href: "/",
    label: "均线分析",
    match: (p: string) => p === "/",
    Icon: ChartLine,
  },
  {
    href: "/pattern-trend",
    label: "形态指数",
    match: (p: string) => p.startsWith("/pattern-trend"),
    Icon: SquaresFour,
  },
  {
    href: "/feature-stats",
    label: "形态统计",
    match: (p: string) => p.startsWith("/feature-stats"),
    Icon: Table,
  },
  {
    href: "/llm-analysis",
    label: "LLM 分析",
    match: (p: string) => p.startsWith("/llm-analysis"),
    Icon: Brain,
  },
  {
    href: "/llm-recommend",
    label: "LLM推荐",
    match: (p: string) => p.startsWith("/llm-recommend"),
    Icon: Sparkle,
  },
];

interface Props {
  collapsed: boolean;
  onToggle: () => void;
}

export default function SiteNav({ collapsed, onToggle }: Props) {
  const pathname = usePathname();

  return (
    <nav id="site-nav" className="site-nav" aria-label="主导航">
      <Link href="/" className="brand" title="双色球数据分析">
        <span className="brand-mark" aria-hidden="true">
          <span className="brand-dot red" />
          <span className="brand-dot blue" />
        </span>
        <span className="brand-copy">
          <strong>双色球</strong>
          <span>数据分析</span>
        </span>
      </Link>

      <div className="site-nav-links">
        {NAV_ITEMS.map(({ href, label, match, Icon }) => {
          const active = match(pathname);
          return (
            <Link
              key={href}
              href={href}
              className={`site-nav-link ${active ? "active" : ""}`}
              aria-current={active ? "page" : undefined}
              title={collapsed ? label : undefined}
            >
              <Icon
                size={20}
                weight={active ? "fill" : "regular"}
                aria-hidden="true"
              />
              <span>{label}</span>
            </Link>
          );
        })}
      </div>

      <button
        type="button"
        className="nav-toggle"
        onClick={onToggle}
        aria-expanded={!collapsed}
        aria-controls="site-nav"
        title={collapsed ? "展开菜单" : "收起菜单"}
      >
        <SidebarSimple size={20} weight="regular" aria-hidden="true" />
        <span className="nav-toggle-label">
          {collapsed ? "展开菜单" : "收起菜单"}
        </span>
      </button>
    </nav>
  );
}
