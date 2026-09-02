/**
 * 글리프 렌더 프로필 — jammin `static/js/jammin-packages/hangul-worksheet/profiles.js` 의 수치를 그대로 옮긴 것.
 * ⚠️ 이 값을 바꾸면 학습지 레이아웃이 원본과 달라진다.
 */

export interface GlyphPartStyle {
  height: number;
  top: number;
  zIndex?: number;
}

export interface WorksheetProfile {
  lineBreakCount: number;
  rowHeight: number;
  cellWidth: number;
  cellHeight: number;
  background: { src: string; height: number; top: number; left: number };
  cho: GlyphPartStyle;
  jung: GlyphPartStyle;
  jong: GlyphPartStyle;
  /** 특수문자·숫자 코드별 높이. 없으면 default. */
  specialHeights: Record<string, number>;
}

export type ProfileKey = 'editor' | 'compact';

export const PROFILES: Record<ProfileKey, WorksheetProfile> = {
  editor: {
    lineBreakCount: 8,
    rowHeight: 170,
    cellWidth: 100,
    cellHeight: 100,
    background: { src: 'hangul/32.svg', height: 90, top: 2, left: 13 },
    cho: { height: 32.5, top: 0, zIndex: 1 },
    jung: { height: 59.5, top: 0 },
    jong: { height: 33, top: 59 },
    specialHeights: { '32': 114, '33': 114, '63': 114, '46': 142, default: 136 },
  },
  compact: {
    lineBreakCount: 8,
    rowHeight: 80,
    cellWidth: 80,
    cellHeight: 100,
    background: { src: 'hangul/32.svg', height: 70, top: 2, left: 13 },
    cho: { height: 25, top: 0, zIndex: 1 },
    jung: { height: 46, top: 0 },
    jong: { height: 25.5, top: 48 },
    specialHeights: { '32': 70, '33': 70, '63': 70, '46': 87, default: 70 },
  },
};

/** jammin `specialHeight(code, profile)` */
export function specialHeight(code: number, profile: WorksheetProfile): number {
  return profile.specialHeights[String(code)] ?? profile.specialHeights.default!;
}
