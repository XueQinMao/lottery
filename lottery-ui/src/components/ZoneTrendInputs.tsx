import type { ZoneTrendImage, ZoneTrendSnapshot } from "@/types/zone-trend-analysis";

export default function ZoneTrendInputs({ snapshot, images }: { snapshot: ZoneTrendSnapshot; images: ZoneTrendImage[] }) {
  return (
    <details className="section report-card zone-trend-inputs">
      <summary>查看模型输入 · 3 个个数图 + 28 个比例图</summary>
      <p className="basis">{snapshot.periods[0]} — {snapshot.endPeriod}，共 30 期。以下图片与逐期数据一起提交给模型。</p>
      <div className="zone-trend-downloads">
        {images.map((image, index) => (
          <a key={image.code} className="ghost-btn" href={image.dataUrl} download={`zone-trend-${snapshot.endPeriod}-${image.code}.png`}>
            下载{index === 0 ? "三区个数图" : `三区比第 ${index} 页`}
          </a>
        ))}
        <a className="ghost-btn" href={`data:application/json;charset=utf-8,${encodeURIComponent(JSON.stringify(snapshot, null, 2))}`}
          download={`zone-trend-${snapshot.endPeriod}-data.json`}>下载逐期数据</a>
      </div>
      {images.map((image, index) => (
        <details key={image.code} className="zone-trend-image">
          <summary>{index === 0 ? "预览三区个数图" : `预览三区比第 ${index} 页`}</summary>
          {/* Canvas exports already contain their intrinsic dimensions; no image optimization is needed. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={image.dataUrl} alt={index === 0 ? "三个区的逐期红球个数折线图" : `三区比出现折线图第${index}页，每页7种比例`} width={1200} height={index === 0 ? 880 : 1700} />
        </details>
      ))}
    </details>
  );
}
