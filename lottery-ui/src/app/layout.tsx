import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "双色球遗漏趋势分析",
  description: "基于 LotteryTrendUtils 的遗漏指数与均线趋势图",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
