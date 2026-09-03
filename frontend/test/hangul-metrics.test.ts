import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import {
  BACKGROUND,
  BOX,
  CANVAS,
  CHO,
  glyphSrc,
  JONG,
  JUNG,
  placeSpecial,
  SPECIAL_HEIGHT,
  toPercent,
} from '../src/features/worksheet/hangul-metrics';

const HANGUL_DIR = fileURLToPath(new URL('../public/hangul', import.meta.url));

/** 실제 SVG 파일에서 viewBox 를 읽는다. 배치 상수의 근거가 자산과 어긋나면 실패한다. */
function viewBox(code: number | string): { w: number; h: number } {
  const text = readFileSync(`${HANGUL_DIR}/${code}.svg`, 'utf8').slice(0, 800);
  const m = /viewBox="([\d.\s]+)"/.exec(text);
  if (!m) throw new Error(`${code}.svg 에 viewBox 가 없습니다`);
  const [, , w, h] = m[1]!.trim().split(/\s+/).map(Number);
  return { w: w!, h: h! };
}

const CHO_CODES = Array.from({ length: 19 }, (_, i) => 0x1100 + i); // 4352~4370
const JUNG_CODES = Array.from({ length: 21 }, (_, i) => 0x1161 + i); // 4449~4469
const JONG_CODES = Array.from({ length: 27 }, (_, i) => 0x11a8 + i); // 4520~4546

describe('자산 실측 — 배치 상수의 근거', () => {
  it('초성 19개가 모두 같은 캔버스다', () => {
    for (const code of CHO_CODES) {
      expect(viewBox(code), `초성 ${code}`).toEqual({ w: CANVAS.cho.w, h: CANVAS.cho.h });
    }
  });

  it('중성 21개가 모두 같은 캔버스다', () => {
    for (const code of JUNG_CODES) {
      expect(viewBox(code), `중성 ${code}`).toEqual({ w: CANVAS.jung.w, h: CANVAS.jung.h });
    }
  });

  it('종성 27개가 모두 같은 캔버스다', () => {
    for (const code of JONG_CODES) {
      expect(viewBox(code), `종성 ${code}`).toEqual({ w: CANVAS.jong.w, h: CANVAS.jong.h });
    }
  });

  it('특수문자·숫자는 폭이 공통이고 높이만 다르다', () => {
    const specials = readdirSync(HANGUL_DIR)
      .filter((f) => /^\d{1,3}\.svg$/.test(f))
      .map((f) => Number(f.replace('.svg', '')));
    expect(specials.length).toBeGreaterThan(0);

    for (const code of specials) {
      const vb = viewBox(code);
      expect(vb.w, `특수 ${code} 폭`).toBe(CANVAS.specialWidth);
      if (SPECIAL_HEIGHT[code] !== undefined) {
        expect(vb.h, `특수 ${code} 높이`).toBe(SPECIAL_HEIGHT[code]);
      }
    }
  });

  it('빈 칸(000000)은 전체 상자와 같은 정사각이다', () => {
    const vb = viewBox('000000');
    expect(vb.w).toBeCloseTo(BOX, 0);
    expect(vb.h).toBeCloseTo(BOX, 0);
    expect(vb.w).toBeCloseTo(vb.h, 5);
  });
});

describe('배치 규칙', () => {
  it('중성과 종성이 세로를 거의 정확히 나눠 갖는다', () => {
    // 400.9 + 221.1 = 622.0 vs 상자 623.6 — 자산 제작 시 반올림으로 1.6 차이
    const gap = BOX - (JUNG.h + JONG.h);
    expect(gap).toBeGreaterThanOrEqual(0);
    expect(gap).toBeLessThan(2);
  });

  it('종성은 상자 바닥에 붙는다', () => {
    expect(JONG.y + JONG.h).toBeCloseTo(BOX, 5);
  });

  it('중성 하단과 종성 상단 사이에 겹침이 없다', () => {
    expect(JONG.y).toBeGreaterThanOrEqual(JUNG.h);
    expect(JONG.y - JUNG.h).toBeLessThan(2);
  });

  it('초성은 중성 영역의 좌상단 안에 들어간다', () => {
    expect(CHO.x).toBe(0);
    expect(CHO.y).toBe(0);
    expect(CHO.w).toBeLessThan(JUNG.w);
    expect(CHO.h).toBeLessThan(JUNG.h);
  });

  it('중성·종성은 전체 폭을 쓴다', () => {
    expect(JUNG.w).toBe(BOX);
    expect(JONG.w).toBe(BOX);
  });

  it('배경은 상자 전체를 덮는다', () => {
    expect(BACKGROUND).toEqual({ x: 0, y: 0, w: BOX, h: BOX });
  });

  it('모든 배치가 상자를 벗어나지 않는다', () => {
    for (const [name, p] of [['초성', CHO], ['중성', JUNG], ['종성', JONG]] as const) {
      expect(p.x + p.w, `${name} 우측`).toBeLessThanOrEqual(BOX + 0.1);
      expect(p.y + p.h, `${name} 하단`).toBeLessThanOrEqual(BOX + 0.1);
    }
  });
});

describe('jammin 원본 수치와의 정합', () => {
  /**
   * 원본 profiles.js(editor)의 픽셀 수치를 비율로 역산하면
   * 전부 "전체 상자 ≈93px" 로 수렴한다. 그 관계가 유지되는지 확인한다.
   */
  it('원본 픽셀 수치를 비율로 되돌리면 한 상자 크기로 수렴한다', () => {
    const box1 = 32.5 / (CHO.h / BOX); // 원본 cho height
    const box2 = 59.5 / (JUNG.h / BOX); // 원본 jung height
    const box3 = 33 / (JONG.h / BOX); // 원본 jong height

    for (const b of [box1, box2, box3]) {
      expect(b).toBeGreaterThan(92);
      expect(b).toBeLessThan(94);
    }
    // 서로 1px 이내로 일치
    expect(Math.max(box1, box2, box3) - Math.min(box1, box2, box3)).toBeLessThan(1);
  });

  it('원본 종성 top(59)도 같은 비율에서 나온다', () => {
    const box = 93;
    // 원본은 59 로 반올림해 적었다. 정확값은 59.8.
    expect((JONG.y / BOX) * box).toBeGreaterThan(58.5);
    expect((JONG.y / BOX) * box).toBeLessThan(60.5);
  });

  it('원본처럼 종성이 상자 바닥에 닿는다 (top + height ≈ box)', () => {
    const box = 93;
    const top = (JONG.y / BOX) * box;
    const height = (JONG.h / BOX) * box;
    expect(top + height).toBeCloseTo(box, 0); // 원본: 59 + 33 = 92 ≈ 93
  });
});

describe('특수문자 배치', () => {
  it('폭은 항상 상자에 맞춘다', () => {
    expect(placeSpecial(32).w).toBe(BOX);
    expect(placeSpecial(46).w).toBe(BOX);
  });

  it('세로로 긴 글자(쉼표·마침표)는 상자보다 아래로 내려간다', () => {
    // 캔버스가 세로로 길게 잡혀 있어 글자가 아래쪽에 놓인다
    expect(placeSpecial(46).h).toBeGreaterThan(BOX); // .
    expect(placeSpecial(44).h).toBeGreaterThan(BOX); // ,
    expect(placeSpecial(32).h).toBeLessThanOrEqual(BOX + 0.1); // 공백
  });

  it('정사각 특수문자는 상자와 거의 같다', () => {
    expect(placeSpecial(33).h).toBeCloseTo(BOX, 0); // !
  });
});

describe('퍼센트 변환 — 크기와 무관하게 위치 고정', () => {
  it('중성은 상단에서 전체 폭', () => {
    const p = toPercent(JUNG);
    expect(p.left).toBe('0.0000%');
    expect(p.top).toBe('0.0000%');
    expect(p.width).toBe('100.0000%');
    expect(Number.parseFloat(p.height)).toBeCloseTo(64.29, 1);
  });

  it('초성은 좌상단 67% x 35%', () => {
    const p = toPercent(CHO);
    expect(Number.parseFloat(p.width)).toBeCloseTo(67.27, 1);
    expect(Number.parseFloat(p.height)).toBeCloseTo(35.01, 1);
  });

  it('종성은 하단에 붙는다 (top + height = 100%)', () => {
    const p = toPercent(JONG);
    expect(Number.parseFloat(p.top) + Number.parseFloat(p.height)).toBeCloseTo(100, 2);
  });

  it('배경은 상자 전체', () => {
    expect(toPercent(BACKGROUND)).toEqual({
      left: '0.0000%',
      top: '0.0000%',
      width: '100.0000%',
      height: '100.0000%',
    });
  });
});

describe('경로 규약', () => {
  it('파일명이 곧 유니코드 코드값이다', () => {
    expect(glyphSrc(4352)).toBe('hangul/4352.svg');
    expect(glyphSrc('000000')).toBe('hangul/000000.svg');
  });
});
