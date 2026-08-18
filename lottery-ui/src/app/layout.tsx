import type { Metadata } from "next";
import { Suspense } from "react";
import { Fira_Code, Fira_Sans } from "next/font/google";
import AppShell from "@/components/AppShell";
import "./globals.css";

const firaSans = Fira_Sans({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-fira-sans",
});

const firaCode = Fira_Code({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-fira-code",
});

export const metadata: Metadata = {
  title: "双色球数据分析",
  description: "遗漏趋势、形态指数与开奖形态统计",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className={`${firaSans.variable} ${firaCode.variable} antialiased`}>
        <Suspense fallback={null}>
          <AppShell>{children}</AppShell>
        </Suspense>
      </body>
    </html>
  );
}
