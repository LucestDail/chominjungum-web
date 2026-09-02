import { describe, expect, it } from 'vitest';

import { hangulSplit } from '../src/hangul-util.js';
import { JAMO_GROUPS, optionsOf } from '../src/jamo-table.js';
import { isHidableInMode, jamoKindOf, parseCheckboxValue, type HideMode } from '../src/hide-rules.js';

const MODES: HideMode[] = ['ja', 'cho', 'jung', 'jong'];

describe('jammin 자모 테이블 무결성', () => {
  it('원본 그룹 크기와 같다', () => {
    // templates/test.html 에서 추출한 실제 개수
    expect(JAMO_GROUPS.ja.primary).toHaveLength(14);
    expect(JAMO_GROUPS.ja.extra).toHaveLength(16);
    expect(JAMO_GROUPS.cho.primary).toHaveLength(14);
    expect(JAMO_GROUPS.cho.extra).toHaveLength(5);
    expect(JAMO_GROUPS.jung.primary).toHaveLength(10);
    expect(JAMO_GROUPS.jung.extra).toHaveLength(11);
    expect(JAMO_GROUPS.jong.primary).toHaveLength(14);
    expect(JAMO_GROUPS.jong.extra).toHaveLength(13);
  });

  it('모든 코드가 실제 자모 범위 안에 있다', () => {
    for (const mode of MODES) {
      for (const opt of optionsOf(mode)) {
        for (const code of parseCheckboxValue(opt.value)) {
          expect(jamoKindOf(code), `${mode} ${opt.label} ${code}`).not.toBeNull();
        }
      }
    }
  });

  it('모든 코드가 해당 모드에서 가릴 수 있다', () => {
    for (const mode of MODES) {
      for (const opt of optionsOf(mode)) {
        for (const code of parseCheckboxValue(opt.value)) {
          expect(isHidableInMode(code, mode), `${mode} ${opt.label} ${code}`).toBe(true);
        }
      }
    }
  });

  it('자음 그룹의 쌍은 초성+종성 순서다', () => {
    for (const opt of optionsOf('ja')) {
      const codes = parseCheckboxValue(opt.value);
      if (codes.length === 2) {
        expect(jamoKindOf(codes[0]!)).toBe('cho');
        expect(jamoKindOf(codes[1]!)).toBe('jong');
      } else {
        // ㄸ/ㅃ/ㅉ 은 종성이 없어 단일 코드, 겹받침은 종성 단독
        expect(codes).toHaveLength(1);
        expect(jamoKindOf(codes[0]!)).toMatch(/cho|jong/);
      }
    }
  });

  it('중성 21개가 모두 담겨 있다 (ㅏ~ㅣ)', () => {
    const codes = optionsOf('jung').flatMap((o) => parseCheckboxValue(o.value));
    expect(new Set(codes).size).toBe(21);
    expect(Math.min(...codes)).toBe(0x1161);
    expect(Math.max(...codes)).toBe(0x1175);
  });

  it('종성 27개가 모두 담겨 있다', () => {
    const codes = optionsOf('jong').flatMap((o) => parseCheckboxValue(o.value));
    expect(new Set(codes).size).toBe(27);
    expect(Math.min(...codes)).toBe(0x11a8);
    expect(Math.max(...codes)).toBe(0x11c2);
  });

  it('초성 19개가 모두 담겨 있다', () => {
    const codes = optionsOf('cho').flatMap((o) => parseCheckboxValue(o.value));
    expect(new Set(codes).size).toBe(19);
    expect(Math.min(...codes)).toBe(0x1100);
    expect(Math.max(...codes)).toBe(0x1112);
  });

  it('라벨이 실제 분해 결과와 대응한다 (ㄱ → 값의 초성)', () => {
    const [ga] = hangulSplit('가');
    const choGa = JAMO_GROUPS.cho.primary.find((o) => o.label === 'ㄱ')!;
    expect(parseCheckboxValue(choGa.value)[0]).toBe(ga!.choCode);
  });
});
