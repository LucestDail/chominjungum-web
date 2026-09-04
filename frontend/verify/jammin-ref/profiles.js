/**
 * 받아쓰기 한 글자( hangulSet ) 레이아웃 수치 — addTest / syncWordLineBreak 등에 흩어져 있던 값을 한곳에 모음.
 */
export const PROFILES = {
  editor: {
    lineBreakCount: 8,
    rowHeight: 170,
    cellWidth: 100,
    cellHeight: 100,
    background: { src: "hangul/32.svg", height: 90, top: 2, left: 13 },
    cho: { height: 32.5, top: 0, zIndex: 1 },
    jung: { height: 59.5, top: 0 },
    jong: { height: 33, top: 59 },
    specialHeights: {
      "32": 114,
      "33": 114,
      "63": 114,
      "46": 142,
      default: 136,
    },
  },
  compact: {
    lineBreakCount: 8,
    rowHeight: 80,
    cellWidth: 80,
    cellHeight: 100,
    background: { src: "hangul/32.svg", height: 70, top: 2, left: 13 },
    cho: { height: 25, top: 0, zIndex: 1 },
    jung: { height: 46, top: 0 },
    jong: { height: 25.5, top: 48 },
    specialHeights: {
      "32": 70,
      "33": 70,
      "63": 70,
      "46": 87,
      default: 70,
    },
  },
};
