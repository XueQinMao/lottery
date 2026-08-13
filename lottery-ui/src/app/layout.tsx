import type { Metadata } from "next";
import { Suspense } from "react";
import SiteNav from "@/components/SiteNav";
import "./globals.css";

export const metadata: Metadata = {
  title: "双色球数据分析",
  description: "遗漏趋势与开奖形态统计",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body>
        <Suspense fallback={null}>
          <SiteNav />
        </Suspense>
        {children}
      </body>
    </html>
  );
}
