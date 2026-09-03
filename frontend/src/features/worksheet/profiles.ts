/**
 * 학습지 레이아웃 프로필.
 *
 * 글리프 **내부** 배치(초/중/종 위치)는 `hangul-metrics.ts` 가 비율로 완전히 결정한다.
 * 여기 남는 것은 종이 위 배치뿐이다 — 한 칸을 얼마나 크게, 한 줄에 몇 자, 줄 간격은 얼마.
 *
 * jammin `profiles.js` 에서 가져온 것은 **한 줄 8자**(`lineBreakCount`) 하나이고,
 * 칸 크기는 원본 픽셀 수치를 역산한 "전체 글자 상자 ≈93px"(editor)에 맞췄다.
 */

export type ProfileKey = 'editor' | 'compact';

export interface WorksheetProfile {
  /** 한 줄에 들어가는 글자 수 — jammin 원본 규약(8자) */
  lineBreakCount: number;
  /** 한 칸(글자 상자)의 변 길이(px) */
  cellSize: number;
  /** 칸 사이 가로 간격(px) */
  cellGap: number;
  /** 줄 사이 세로 간격(px) */
  rowGap: number;
}

export const PROFILES: Record<ProfileKey, WorksheetProfile> = {
  /** 넓게 — 저학년·연습용 */
  editor: { lineBreakCount: 8, cellSize: 92, cellGap: 8, rowGap: 26 },
  /** 좁게 — 문항이 많을 때 */
  compact: { lineBreakCount: 8, cellSize: 70, cellGap: 6, rowGap: 14 },
};

export const PROFILE_LABELS: Record<ProfileKey, string> = {
  editor: '넓게',
  compact: '좁게',
};
