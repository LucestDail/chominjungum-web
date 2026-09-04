/** jammin 원본 레이아웃 수치(복사본) — 대조 기준. */
export const PROFILES: Record<
  'editor' | 'compact',
  {
    lineBreakCount: number;
    rowHeight: number;
    cellWidth: number;
    cellHeight: number;
    background: { src: string; height: number; top: number; left: number };
    cho: { height: number; top: number; zIndex: number };
    jung: { height: number; top: number };
    jong: { height: number; top: number };
    specialHeights: Record<string, number>;
  }
>;
