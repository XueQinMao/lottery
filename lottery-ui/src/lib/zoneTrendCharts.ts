import type { DrawTrend } from "@/types/draw-trend";
import type { ZoneTrendImage, ZoneTrendSeries, ZoneTrendSnapshot } from "@/types/zone-trend-analysis";
import { CHART_THEME } from "@/lib/chartTheme";

export function zoneTrendSnapshot(data: DrawTrend): ZoneTrendSnapshot {
  if (data.periods.length !== 30) throw new Error("三区走势试点需要完整的最近 30 期开奖");
  const extract = (code: string, length: number): ZoneTrendSeries[] => {
    const group = data.redFeatures.find((item) => item.code === code);
    if (!group || group.series.length !== length) throw new Error("三区特征数据不完整，请刷新重试");
    return group.series.map((series) => ({
      code: series.code,
      values: series.values.map((value) => {
        if (value === null || !Number.isInteger(value)) throw new Error("三区逐期数据存在缺失");
        return value;
      }),
    }));
  };
  return {
    endPeriod: data.periods[data.periods.length - 1],
    periods: [...data.periods],
    counts: extract("zones", 3),
    ratios: extract("threeZone", 28),
  };
}

const COUNT_LABELS: Record<string, string> = {
  zone1: "一区个数 · 1–11", zone2: "二区个数 · 12–22", zone3: "三区个数 · 23–33",
};

/** Render fixed-size full-window sheets; screen size and visible chart zoom cannot change model input. */
export async function exportZoneTrendImages(snapshot: ZoneTrendSnapshot): Promise<ZoneTrendImage[]> {
  const echarts = await import("echarts");
  await document.fonts.ready;
  const sheets = [{ code: "counts", rows: snapshot.counts, occurrence: false },
    ...Array.from({ length: 4 }, (_, index) => ({
      code: `ratios-${index + 1}`, rows: snapshot.ratios.slice(index * 7, index * 7 + 7), occurrence: true,
    }))];
  const result: ZoneTrendImage[] = [];
  for (const sheet of sheets) {
    const height = sheet.occurrence ? 1700 : 880;
    const cellHeight = (height - 100) / sheet.rows.length;
    const chart = echarts.init(document.createElement("div"), null, {
      renderer: "canvas", width: 1200, height, devicePixelRatio: 1,
    });
    try {
      chart.setOption({
        animation: false,
        backgroundColor: "#FFFFFF",
        textStyle: { fontFamily: '"Microsoft YaHei", sans-serif', color: CHART_THEME.text },
        title: [{
          text: sheet.occurrence ? `三区比 · 第 ${sheet.code.slice(-1)} / 4 页` : "三区个数 · 每期合计 6",
          subtext: `${snapshot.periods[0]} — ${snapshot.endPeriod} · 30期 · 左旧右新${sheet.occurrence ? " · 1=出现，0=未出现" : ""}`,
          left: 30, top: 10, textStyle: { fontSize: 20 }, subtextStyle: { fontSize: 13 },
        }, ...sheet.rows.map((row, index) => ({
          text: sheet.occurrence ? `三区比 ${row.code}` : COUNT_LABELS[row.code],
          left: 35, top: 72 + index * cellHeight, textStyle: { fontSize: 15, fontWeight: 500 },
        }))],
        grid: sheet.rows.map((_, index) => ({
          left: 65, right: 40, top: 103 + index * cellHeight, height: cellHeight - 90,
        })),
        xAxis: sheet.rows.map((_, index) => ({
          type: "category", gridIndex: index, data: snapshot.periods, boundaryGap: false,
          axisLabel: { interval: 0, rotate: 45, fontSize: 10, color: CHART_THEME.muted },
          axisLine: { lineStyle: { color: CHART_THEME.axis } }, axisTick: { show: false },
        })),
        yAxis: sheet.rows.map((_, index) => ({
          type: "value", gridIndex: index, min: 0, max: sheet.occurrence ? 1 : 6, interval: 1,
          axisLabel: { fontSize: 12, color: CHART_THEME.muted },
          splitLine: { lineStyle: { color: CHART_THEME.split } },
        })),
        series: sheet.rows.map((row, index) => ({
          type: "line", xAxisIndex: index, yAxisIndex: index, data: row.values,
          smooth: false, symbol: "circle", symbolSize: sheet.occurrence ? 5 : 7,
          lineStyle: { width: 2, color: sheet.occurrence ? CHART_THEME.ballBlue : CHART_THEME.ballRed },
          itemStyle: { color: sheet.occurrence ? CHART_THEME.ballBlue : CHART_THEME.ballRed },
          label: { show: !sheet.occurrence, position: "top", fontSize: 11 },
        })),
      });
      const dataUrl = chart.getDataURL({ type: "png", pixelRatio: 1, backgroundColor: "#FFFFFF" });
      if (!dataUrl.startsWith("data:image/png;base64,")) throw new Error("走势图图片生成失败，请重试");
      result.push({ code: sheet.code, dataUrl });
    } finally {
      chart.dispose();
    }
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
  return result;
}
