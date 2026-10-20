import type { NumberTrendRow } from "@/types/number-trend";

const MAX_RED_NUMBER = 33;
const MAX_CANDIDATES = 8;
const MAX_DIAGONAL_STEP = 3;

type PatternKind = "horizontal" | "repeat" | "diagonal";

interface Point {
  row: number;
  number: number;
}

export interface RedPatternSegment {
  from: Point;
  to: Point;
  kind: PatternKind;
}

interface Candidate {
  number: number;
  reasons: string[];
  projections: RedPatternSegment[];
}

function pointKey(point: Point) {
  return `${point.row}:${point.number}`;
}

function segmentKey(from: Point, to: Point) {
  return `${pointKey(from)}-${pointKey(to)}`;
}

export function buildRedTrendPatterns(rows: NumberTrendRow[], rankedNumbers?: number[]) {
  const hits = rows.map((row) => new Set(row.reds));
  const last = hits.length - 1;
  const segments = new Map<string, RedPatternSegment>();
  const anchors = new Set<string>();
  const projections = new Map<number, Map<string, { segment: RedPatternSegment; reason: string }>>();
  const diagonalEnds = new Set<string>();

  const connect = (from: Point, to: Point, kind: PatternKind) => {
    segments.set(segmentKey(from, to), { from, to, kind });
    if (kind !== "diagonal") {
      anchors.add(pointKey(from));
      anchors.add(pointKey(to));
    }
  };

  const project = (from: Point, number: number, kind: PatternKind, reason: string) => {
    if (number < 1 || number > MAX_RED_NUMBER) return;
    const to = { row: last + 1, number };
    const evidence = projections.get(number) ?? new Map();
    evidence.set(segmentKey(from, to), { segment: { from, to, kind }, reason });
    projections.set(number, evidence);
  };

  for (const [row, numbers] of hits.entries()) {
    for (const number of numbers) {
      const current = { row, number };
      const gap = hits[row - 1]?.has(number) ? 1 : hits[row - 2]?.has(number) ? 2 : 0;
      if (gap) {
        connect({ row: row - gap, number }, current, "repeat");
        if (row + gap === last + 1) project(current, number, "repeat", `红球 ${number} 的重号间隔延续`);
      }
      if (numbers.has(number + 1)) connect(current, { row, number: number + 1 }, "horizontal");
      if (row === last && numbers.has(number + 1) && !numbers.has(number - 1)) {
        project(current, number - 1, "horizontal", `连号从 ${number} 向左延伸`);
      }
      if (row === last && numbers.has(number - 1) && !numbers.has(number + 1)) {
        project(current, number + 1, "horizontal", `连号从 ${number} 向右延伸`);
      }
    }
  }

  // 只有接入其他线段的同期开出号码才补画中间隔一格的横线。
  for (const [row, numbers] of hits.entries()) {
    for (const number of numbers) {
      const from = { row, number };
      const to = { row, number: number + 2 };
      if (!numbers.has(number + 1) && numbers.has(to.number)
          && (anchors.has(pointKey(from)) || anchors.has(pointKey(to)))) {
        segments.set(segmentKey(from, to), { from, to, kind: "horizontal" });
      }
    }
  }

  // 三期等步长斜线或折返确认后，再绘制对应的两条线段。
  for (let row = 2; row <= last; row++) {
    for (const middle of hits[row - 1]) {
      for (const start of hits[row - 2]) {
        const step = middle - start;
        if (step === 0 || Math.abs(step) > MAX_DIAGONAL_STEP) continue;
        for (const end of [middle + step, start]) {
          if (!hits[row].has(end)) continue;
          const from = { row: row - 2, number: start };
          const turningPoint = { row: row - 1, number: middle };
          const to = { row, number: end };
          connect(from, turningPoint, "diagonal");
          connect(turningPoint, to, "diagonal");
          diagonalEnds.add(segmentKey(turningPoint, to));
          if (row === last) {
            const kind = end === start ? "斜线折返" : "斜线延续";
            project(to, end + step, "diagonal", `${start} → ${middle} → ${end} 的${kind}`);
          }
        }
      }
    }
  }

  for (let row = 1; row <= last; row++) {
    const isLatestPair = row === last;
    for (const number of hits[row - 1]) {
      const from = { row: row - 1, number };
      for (const step of [-1, 1]) {
        if (!hits[row].has(number + step)) continue;
        const to = { row, number: number + step };
        if (!isLatestPair && !anchors.has(pointKey(from)) && !anchors.has(pointKey(to))) continue;
        connect(from, to, "diagonal");
        if (isLatestPair && !diagonalEnds.has(segmentKey(from, to))) {
          project(to, to.number + step, "diagonal", `${number} → ${to.number} 的邻号斜线延续`);
        }
      }
    }
  }

  const candidates: Candidate[] = [...projections].map(([number, evidence]) => ({
    number,
    reasons: [...new Set([...evidence.values()].map((item) => item.reason))],
    projections: [...evidence.values()].map((item) => item.segment),
  }));
  if (rankedNumbers) {
    const details = new Map(candidates.map((candidate) => [candidate.number, candidate]));
    return {
      segments: [...segments.values()],
      candidates: rankedNumbers.map((number) => details.get(number) ?? { number, reasons: [], projections: [] }),
    };
  }
  candidates.sort((a, b) => b.reasons.length - a.reasons.length || a.number - b.number);

  return { segments: [...segments.values()], candidates: candidates.slice(0, MAX_CANDIDATES) };
}
