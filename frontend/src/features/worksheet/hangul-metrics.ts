/**
 * 한 글자 상자의 좌표계 — SVG 자산 실측값.
 *
 * ## 근거
 *
 * `public/hangul/*.svg` 는 **전체 글자 상자를 부위별로 크롭한 조각**이다.
 * 유니코드 그룹과 viewBox 가 정확히 1:1 로 대응한다(83개 전수 확인):
 *
 * | 부위 | 코드 범위 | viewBox |
 * |---|---|---|
 * | 초성 19개 | 4352~4370 | `419.5 x 218.3` |
 * | 중성 21개 | 4449~4469 | `623.6 x 400.9` |
 * | 종성 27개 | 4520~4546 | `623.6 x 221.1` |
 * | 특수·숫자 | 32~63 | `598.28` 폭 (높이는 글자마다) |
 * | 빈 칸 | 000000 | `623.62 x 623.62` |
 *
 * 전체 상자는 정사각 **623.6 x 623.6** 이고,
 *   중성(400.9) + 종성(221.1) = 622.0 ≈ 623.6 → 세로를 둘이 나눠 갖는다.
 *   초성은 중성 영역의 좌상단(419.5 x 218.3)에 놓인다.
 *
 * ## jammin 원본과의 관계
 *
 * jammin `profiles.js` 의 픽셀 수치(cho 32.5 / jung 59.5 / jong 33, jong top 59)를
 * 위 비율로 역산하면 전부 **전체 상자 ≈93px** 로 수렴한다:
 *   32.5 / (218.3/623.6) = 92.9 · 59.5 / (400.9/623.6) = 92.6 · 33 / (221.1/623.6) = 93.1
 * 즉 원본 수치는 이 비율을 손으로 계산해 박아둔 것이다.
 * 여기서는 비율을 그대로 두고 상자 크기만 바꾼다 — 어떤 크기에서도 위치가 어긋나지 않는다.
 */

/** 전체 글자 상자(정사각). 모든 배치는 이 좌표계 안에서 계산한다. */
export const BOX = 623.6;

/** 부위별 캔버스 크기 — SVG viewBox 실측 */
export const CANVAS = {
  cho: { w: 419.5, h: 218.3 },
  jung: { w: 623.6, h: 400.9 },
  jong: { w: 623.6, h: 221.1 },
  /** 특수문자·숫자는 폭만 공통이고 높이는 글자마다 다르다(아래 SPECIAL_HEIGHT). */
  specialWidth: 598.28,
} as const;

/**
 * 특수문자·숫자의 캔버스 높이 — 폭은 598.28 로 같고 높이만 다르다.
 * 쉼표·마침표가 세로로 긴 것은 글자가 **아래쪽에 놓이도록** 캔버스가 그렇게 잡힌 것이다.
 * 폭을 기준으로 맞추면 그 위치가 자동으로 재현된다(원본이 높이를 개별 보정하던 이유).
 */
export const SPECIAL_HEIGHT: Record<number, number> = {
  44: 713.95, // ,
  46: 744.95, // .
  63: 601.78, // ?
};
export const SPECIAL_HEIGHT_DEFAULT = 598.28;

/** 배치 사각형 — 전체 상자(BOX) 좌표계 기준 */
export interface Placement {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** 초성: 좌상단 */
export const CHO: Placement = { x: 0, y: 0, w: CANVAS.cho.w, h: CANVAS.cho.h };

/** 중성: 상단 전체 폭 */
export const JUNG: Placement = { x: 0, y: 0, w: CANVAS.jung.w, h: CANVAS.jung.h };

/**
 * 종성: 상자 **바닥 기준**, 전체 폭.
 *
 * 조각이 상자에서 크롭된 것이므로 종성은 하단에 붙는다.
 * 중성 하단(400.9)과 종성 상단(402.5) 사이에 1.6(0.26%)의 틈이 남는데,
 * 이는 자산 제작 시의 반올림이다. 중성 아래에 이어 붙이는 대신 바닥을 기준으로 삼아야
 * 글자가 칸 안에서 아래로 처지거나 떠 보이지 않는다.
 * (jammin 원본도 `top 59 + height 33 = 92 ≈ 상자 93` 으로 바닥에 붙였다.)
 */
export const JONG: Placement = {
  x: 0,
  y: BOX - CANVAS.jong.h,
  w: CANVAS.jong.w,
  h: CANVAS.jong.h,
};

/** 빈 칸(원고지 배경) */
export const BACKGROUND: Placement = { x: 0, y: 0, w: BOX, h: BOX };

/**
 * 특수문자·숫자 배치 — 폭을 상자에 맞추고 높이는 캔버스 비율을 따른다.
 * 가로 가운데 정렬, 위쪽 기준(세로로 긴 글자는 자연히 아래로 내려간다).
 */
export function placeSpecial(code: number): Placement {
  const h = SPECIAL_HEIGHT[code] ?? SPECIAL_HEIGHT_DEFAULT;
  const scale = BOX / CANVAS.specialWidth;
  return { x: 0, y: 0, w: BOX, h: h * scale };
}

/**
 * CSS 절대배치용 퍼센트 값 — 상자 크기와 무관하게 위치가 고정된다.
 * (인터페이스가 아니라 Record 인 이유: 그대로 style 바인딩에 넘기기 위해서)
 */
export type PercentBox = Record<'left' | 'top' | 'width' | 'height', string>;

/**
 * 배치를 상자 대비 퍼센트로 바꾼다.
 *
 * `<img>` 절대배치를 쓰는 이유: 외부 SVG 를 `<svg><image href>` 로 참조하는 방식은
 * 브라우저·인쇄 경로에서 동작이 갈릴 수 있다. 원본 jammin 이 검증한 `<img>` 방식을
 * 그대로 두되 좌표만 픽셀에서 퍼센트로 바꿔, 칸 크기 하나로 전체가 비례하게 한다.
 */
export function toPercent(p: Placement): PercentBox {
  const pct = (v: number) => `${((v / BOX) * 100).toFixed(4)}%`;
  return { left: pct(p.x), top: pct(p.y), width: pct(p.w), height: pct(p.h) };
}

/** 자모 코드 → SVG 경로. 파일명이 곧 유니코드 코드값이다(jammin 규약). */
export function glyphSrc(code: number | string, base = 'hangul'): string {
  return `${base}/${code}.svg`;
}

/** 빈 원고지 칸 파일 */
export const BACKGROUND_SRC = glyphSrc('000000');
