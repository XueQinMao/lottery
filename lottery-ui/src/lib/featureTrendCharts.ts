import { CHART_THEME } from "@/lib/chartTheme";
import type { FeatureTrendInput, FeatureTrendSheet } from "@/types/feature-trend-forecast";

const ROWS_PER_SHEET = 7;
const ROW_HEIGHT = 240;
const SHEET_WIDTH = 1200;

export async function exportFeatureTrendImages(
  input: FeatureTrendInput, blue: boolean, signal: AbortSignal,
): Promise<FeatureTrendSheet[]> {
  const echarts = await import("echarts");
  await document.fonts.ready;
  const sheets: FeatureTrendSheet[] = [];
  const color = blue ? CHART_THEME.ballBlue : CHART_THEME.ballRed;
  for (const group of input.groups) {
    const pageCount = Math.ceil(group.series.length / ROWS_PER_SHEET);
    for (let page = 0; page < pageCount; page++) {
      signal.throwIfAborted();
      const rows = group.series.slice(page * ROWS_PER_SHEET, (page + 1) * ROWS_PER_SHEET);
      const code = `${group.code}-${page + 1}`;
      const label = pageCount === 1 ? group.label : `${group.label} · ${page + 1}/${pageCount}`;
      const height = 100 + ROW_HEIGHT * rows.length;
      const chart = echarts.init(document.createElement("div"), null, {
        renderer: "canvas", width: SHEET_WIDTH, height, devicePixelRatio: 1,
      });
      try {
        chart.setOption({
          animation: false,
          backgroundColor: "#FFFFFF",
          textStyle: { fontFamily: '"Microsoft YaHei", sans-serif', color: CHART_THEME.text },
          title: [{
            text: label,
            subtext: `${input.periods[0]} — ${input.endPeriod} · 左旧右新${group.occurrence ? " · 1=出现，0=未出现" : ""}`,
            left: 30, top: 10, textStyle: { fontSize: 20 }, subtextStyle: { fontSize: 13 },
          }, ...rows.map((row, index) => ({
            text: row.label, left: 35, top: 69 + index * ROW_HEIGHT,
            textStyle: { fontSize: 15, fontWeight: 500 },
          }))],
          grid: rows.map((_, index) => ({
            left: 72, right: 42, top: 100 + index * ROW_HEIGHT, height: ROW_HEIGHT - 82,
          })),
          xAxis: rows.map((_, index) => ({
            type: "category", gridIndex: index, data: input.periods, boundaryGap: false,
            axisLabel: { interval: 0, rotate: 45, fontSize: 10, color: CHART_THEME.muted },
            axisLine: { lineStyle: { color: CHART_THEME.axis } }, axisTick: { show: false },
          })),
          yAxis: rows.map((_, index) => ({
            type: "value", gridIndex: index, min: group.min, max: group.max,
            interval: group.max - group.min <= 8 ? 1 : undefined,
            axisLabel: { fontSize: 11, color: CHART_THEME.muted },
            splitLine: { lineStyle: { color: CHART_THEME.split } },
          })),
          series: rows.map((row, index) => ({
            type: "line", xAxisIndex: index, yAxisIndex: index, data: row.values,
            connectNulls: false, smooth: false, symbol: "circle", symbolSize: 5,
            lineStyle: { width: 2, color }, itemStyle: { color },
            label: { show: !group.occurrence, position: "top", fontSize: 10 },
          })),
        });
        const dataUrl = chart.getDataURL({ type: "png", pixelRatio: 1, backgroundColor: "#FFFFFF" });
        if (!dataUrl.startsWith("data:image/png;base64,")) throw new Error("走势图导出失败");
        sheets.push({ code, label, height, dataUrl });
      } finally {
        chart.dispose();
      }
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  }
  return sheets;
}
