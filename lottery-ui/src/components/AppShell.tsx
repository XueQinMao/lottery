"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import SiteNav from "@/components/SiteNav";

const SIDEBAR_KEY = "lottery-ui.sidebar-collapsed";

const PAGES: { match: (pathname: string) => boolean; title: string; sub: string }[] =
  [
    {
      match: (p) => p === "/",
      title: "均线分析",
      sub: "切换近 30/50/100 期；截止期号空=最新，如 2026092 表示含该期往前推",
    },
    {
      match: (p) => p.startsWith("/pattern-trend"),
      title: "形态指数",
      sub: "指数 = 实际出现次数 − 理论出现次数（n × p）；命中 +(1-p)，未命中 −p",
    },
    {
      match: (p) => p === "/feature-stats/ratio",
      title: "质合比 / 奇偶比",
      sub: "质合比仅红球；奇偶比红蓝分开。Y 轴为个数，虚线为样本均值",
    },
    {
      match: (p) => p.startsWith("/feature-stats"),
      title: "和值 / 差值",
      sub: "X 轴为开奖期号，虚线为样本均值（仅红球）",
    },
    {
      match: (p) => p.startsWith("/llm-analysis"),
      title: "LLM 分析",
      sub: "基于最近样本的杀号、冷热与形态推算",
    },
    {
      match: (p) => p.startsWith("/llm-recommend"),
      title: "LLM推荐",
      sub: "特征推荐不传预选号码；缓存调优按评分最高或随机取号后再调优",
    },
  ];

export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);
  const meta =
    PAGES.find((page) => page.match(pathname)) ?? {
      title: "双色球数据分析",
      sub: "",
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
            <h1>{meta.title}</h1>
            {meta.sub ? <p className="topbar-sub">{meta.sub}</p> : null}
          </div>
        </header>
        <main id="main" className="workspace-body">
          {children}
        </main>
      </div>
    </div>
  );
}
