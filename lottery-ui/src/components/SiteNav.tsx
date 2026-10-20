"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Brain,
  ChartLine,
  ClipboardText,
  GridFour,
  SidebarSimple,
  Sparkle,
} from "@phosphor-icons/react";

const NAV_ITEMS = [
  {
    href: "/number-trend",
    label: "号码走势图",
    mobileLabel: "号码走势",
    match: (p: string) => p.startsWith("/number-trend"),
    Icon: GridFour,
  },
  {
    href: "/history-trend",
    label: "历史开奖趋势",
    mobileLabel: "开奖趋势",
    match: (p: string) => p.startsWith("/history-trend"),
    Icon: ChartLine,
  },
  {
    href: "/llm-analysis",
    label: "特征预测",
    match: (p: string) => p.startsWith("/llm-analysis"),
    Icon: Brain,
  },
  {
    href: "/llm-recommend",
    label: "LLM 推荐",
    match: (p: string) => p.startsWith("/llm-recommend"),
    Icon: Sparkle,
  },
  {
    href: "/review",
    label: "复盘",
    match: (p: string) => p.startsWith("/review"),
    Icon: ClipboardText,
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
          <span className="brand-dot red bottom" />
          <span className="brand-dot red bottom-right" />
        </span>
        <span className="brand-copy">
          <strong>双色球</strong>
          <span>数据研究室</span>
        </span>
      </Link>

      <div className="site-nav-links">
        {NAV_ITEMS.map((item) => {
          const { href, label, match, Icon } = item;
          const active = match(pathname);
          return (
            <Link
              key={href}
              href={href}
              className={`site-nav-link ${active ? "active" : ""}`}
              aria-current={active ? "page" : undefined}
              aria-label={label}
              title={collapsed ? label : undefined}
            >
              <Icon
                size={20}
                weight="regular"
                aria-hidden="true"
              />
              <span className="nav-label-desktop">{label}</span>
              <span className="nav-label-mobile">{"mobileLabel" in item ? item.mobileLabel : label}</span>
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
