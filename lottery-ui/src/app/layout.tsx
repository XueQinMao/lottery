import type { Metadata } from "next";
import { Suspense } from "react";
import "@fontsource-variable/fira-code";
import "@fontsource/fira-sans/400.css";
import "@fontsource/fira-sans/500.css";
import "@fontsource/fira-sans/600.css";
import "@fontsource/fira-sans/700.css";
import AppShell from "@/components/AppShell";
import "./globals.css";

export const metadata: Metadata = {
  title: "双色球数据分析",
  description: "双色球号码走势图、历史开奖趋势与特征统计",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className="antialiased">
        <Suspense fallback={null}>
          <AppShell>{children}</AppShell>
        </Suspense>
      </body>
    </html>
  );
}
