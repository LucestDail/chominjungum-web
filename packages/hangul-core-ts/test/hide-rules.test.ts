import { describe, expect, it } from 'vitest';

import { hangulSplit } from '../src/hangul-util.js';
import {
  allCodesForMode,
  changeMode,
  hiddenPartsOf,
  isHidableInMode,
  jamoKindOf,
  parseCheckboxValue,
  toggleCodes,
  type HideRule,
} from '../src/hide-rules.js';

describe('jammin 체크박스 value 해석', () => {
  it('단일 코드', () => {
    expect(parseCheckboxValue('4352')).toEqual([4352]);
  });

  it('초성·종성 쌍은 펼친다', () => {
    expect(parseCheckboxValue('4352_4520')).toEqual([4352, 4520]);
  });
});

describe('자모 종류 판정', () => {
  it('초성 ᄀ(4352) / 중성 ᅡ(4449) / 종성 ᆨ(4520)', () => {
    expect(jamoKindOf(4352)).toBe('cho');
    expect(jamoKindOf(4449)).toBe('jung');
    expect(jamoKindOf(4520)).toBe('jong');
  });

  it('종성 없음 표식(4519)은 글리프가 아니다', () => {
    expect(jamoKindOf(4519)).toBeNull();
  });
});

describe('모드별 가리기 가능 여부', () => {
  it('자음 모드는 초성·종성 모두', () => {
    expect(isHidableInMode(4352, 'ja')).toBe(true);
    expect(isHidableInMode(4520, 'ja')).toBe(true);
    expect(isHidableInMode(4449, 'ja')).toBe(false);
  });

  it('초성 모드는 초성만', () => {
    expect(isHidableInMode(4352, 'cho')).toBe(true);
    expect(isHidableInMode(4520, 'cho')).toBe(false);
  });
});

describe('실제 글자에 적용', () => {
  const [gap] = hangulSplit('값'); // 초성 ᄀ4352 · 중성 ᅡ4449 · 종성 ᆹ4537

  it('선택한 초성만 가려진다', () => {
    const rule: HideRule = { mode: 'cho', codes: [4352] };
    expect(hiddenPartsOf(gap!, rule)).toEqual({ cho: true, jung: false, jong: false });
  });

  it('자음 모드에서 초성·종성 쌍이 함께 가려진다', () => {
    const rule = toggleCodes({ mode: 'ja', codes: [] }, '4352_4537', true);
    expect(hiddenPartsOf(gap!, rule)).toEqual({ cho: true, jung: false, jong: true });
  });

  it('모드와 맞지 않는 코드는 무시된다', () => {
    const rule: HideRule = { mode: 'jung', codes: [4352] };
    expect(hiddenPartsOf(gap!, rule)).toEqual({ cho: false, jung: false, jong: false });
  });

  it('특수문자는 가려지지 않는다', () => {
    const [space] = hangulSplit(' ');
    const rule: HideRule = { mode: 'ja', codes: allCodesForMode('ja') };
    expect(hiddenPartsOf(space!, rule)).toEqual({ cho: false, jung: false, jong: false });
  });

  it('전체 토글은 해당 모드의 자모를 모두 덮는다', () => {
    const rule: HideRule = { mode: 'ja', codes: allCodesForMode('ja') };
    expect(hiddenPartsOf(gap!, rule)).toEqual({ cho: true, jung: false, jong: true });
  });
});

describe('모드 변경', () => {
  it('다른 모드로 바꾸면 선택이 초기화된다 (원본 radio 동작)', () => {
    const rule: HideRule = { mode: 'cho', codes: [4352] };
    expect(changeMode(rule, 'jung')).toEqual({ mode: 'jung', codes: [] });
  });

  it('같은 모드면 유지', () => {
    const rule: HideRule = { mode: 'cho', codes: [4352] };
    expect(changeMode(rule, 'cho')).toEqual(rule);
  });
});
