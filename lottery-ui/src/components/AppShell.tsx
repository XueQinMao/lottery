"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import SiteNav from "@/components/SiteNav";

const SIDEBAR_KEY = "lottery-ui.sidebar-collapsed";

const PAGES: { match: (pathname: string) => boolean; title: string; sub: string; showHeading?: boolean }[] =
  [
    {
      match: (p) => p.startsWith("/number-trend"),
      title: "号码走势图",
      sub: "",
      showHeading: false,
    },
    {
      match: (p) => p.startsWith("/history-trend"),
      title: "历史开奖趋势",
      sub: "",
      showHeading: false,
    },
    {
      match: (p) => p.startsWith("/llm-analysis"),
      title: "特征预测",
      sub: "",
      showHeading: false,
    },
    {
      match: (p) => p.startsWith("/llm-recommend"),
      title: "LLM 推荐",
      sub: "",
      showHeading: false,
    },
    {
      match: (p) => p.startsWith("/review"),
      title: "复盘",
      sub: "",
      showHeading: false,
    },
  ];

export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);
  const meta =
    PAGES.find((page) => page.match(pathname)) ?? {
      title: "双色球数据分析",
      sub: "",
      showHeading: true,
    };

  useEffect(() => {
    try {
      setCollapsed(localStorage.getItem(SIDEBAR_KEY) === "1");
    } catch {
      /* ignore */
    }
  }, []);

  const toggleCollapsed = () => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(SIDEBAR_KEY, next ? "1" : "0");
      } catch {
        /* ignore */
      }
      return next;
    });
  };

  return (
    <div className={`app-shell ${collapsed ? "is-collapsed" : ""}`}>
      <a href="#main" className="skip-link">
        跳到主要内容
      </a>
      <SiteNav collapsed={collapsed} onToggle={toggleCollapsed} />
      <div className="workspace">
        <header className="topbar">
          <div>
            <span>数据分析</span><span className="breadcrumb-divider" aria-hidden="true">/</span><span>{meta.title}</span>
          </div>
        </header>
        <main id="main" className={`workspace-body${pathname.startsWith("/number-trend") ? " workspace-body-wide" : ""}`}>
          {meta.showHeading !== false && (
            <div className="page-heading">
              <h1>{meta.title}</h1>
              {meta.sub ? <p>{meta.sub}</p> : null}
            </div>
          )}
          {children}
        </main>
      </div>
    </div>
  );
}
