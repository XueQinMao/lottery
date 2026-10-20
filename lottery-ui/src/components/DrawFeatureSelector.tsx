import type { DrawTrendGroup } from "@/types/draw-trend";

const CATEGORIES = [
  { code: "basic", label: "基础特征" },
  { code: "ratio", label: "结构比例" },
  { code: "position", label: "位置与分区" },
] as const;

function categoryOf(code: string) {
  if (["threeZone", "oddEvenRatio", "roadRatio", "primeRatio"].includes(code)) return "ratio";
  if (["zones", "intervals", "headRoad", "tailRoad", "tailCounts"].includes(code) || /^zone\dColdHot$/.test(code)) return "position";
  return "basic";
}

export default function DrawFeatureSelector({ groups, selected, onSelect }: {
  groups: DrawTrendGroup[]; selected: string; onSelect: (code: string) => void;
}) {
  const category = categoryOf(selected);
  return (
    <div className="filter-card draw-feature-selector">
      <div className="draw-feature-categories" aria-label="特征分类">
        {CATEGORIES.filter((item) => groups.some((group) => categoryOf(group.code) === item.code)).map((item) => (
          <button key={item.code} type="button" className={`draw-feature-category ${category === item.code ? "active" : ""}`}
            aria-pressed={category === item.code}
            onClick={() => onSelect(groups.find((group) => categoryOf(group.code) === item.code)!.code)}>{item.label}</button>
        ))}
      </div>
      <div className="draw-trend-feature-tabs" aria-label="统计特征">
        {groups.filter((group) => categoryOf(group.code) === category).map((group) => (
          <button key={group.code} type="button" className={`draw-trend-feature-tab ${selected === group.code ? "active" : ""}`}
            aria-pressed={selected === group.code} onClick={() => onSelect(group.code)}>
            {group.label}<span>{group.series.length}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
