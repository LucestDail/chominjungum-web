/**
 * 자모 가리기(`hidebox`) — jammin 학습지의 핵심 출제 기능.
 *
 * jammin 원본은 이 상태를 DOM 클래스로만 들고 있어 저장·재사용이 불가능했다
 * (`worksheet-app.js` 의 체크박스 핸들러 + `.hidebox` 클래스).
 * 여기서는 **데이터로 모델링**해서 학습지 템플릿으로 저장할 수 있게 한다.
 *
 * 원본 규칙:
 *  - 모드 4종 (`#removeCombo`): jaremove(초성+종성=자음) / choremove / jungremove / jongremove
 *  - 체크박스 value: `"4352"` 단일 코드, `"4352_4520"` 초성·종성 쌍, `"all-ja"` 등 전체 토글
 */

import type { HangulGlyph } from './hangul-util.js';

/** 가리기 모드 — 원본 `#removeCombo` 의 option value 와 1:1 */
export type HideMode = 'ja' | 'cho' | 'jung' | 'jong';

export const HIDE_MODE_LABELS: Record<HideMode, string> = {
  ja: '초성 + 종성',
  cho: '초성',
  jung: '중성',
  jong: '종성',
};

/** 어떤 자모를 가릴지에 대한 완전한 상태. 학습지 템플릿에 그대로 저장된다. */
export interface HideRule {
  mode: HideMode;
  /** 가릴 자모 코드 집합. 쌍(`4352_4520`)은 펼쳐서 담는다. */
  codes: number[];
}

export const EMPTY_HIDE_RULE: HideRule = { mode: 'ja', codes: [] };

/**
 * jammin 체크박스 value 를 코드 배열로 편다.
 *  - `"4352"`      → [4352]
 *  - `"4352_4520"` → [4352, 4520]
 */
export function parseCheckboxValue(value: string): number[] {
  return value
    .split('_')
    .map((part) => Number.parseInt(part, 10))
    .filter((n) => Number.isInteger(n));
}

/** 자모 코드가 초성/중성/종성 중 어디에 속하는지. */
export type JamoKind = 'cho' | 'jung' | 'jong';

export function jamoKindOf(code: number): JamoKind | null {
  if (code >= 0x1100 && code <= 0x1112) return 'cho';
  if (code >= 0x1161 && code <= 0x1175) return 'jung';
  // 0x11A7 은 '종성 없음' 표식이라 실제 글리프가 아니다.
  if (code >= 0x11a8 && code <= 0x11c2) return 'jong';
  return null;
}

/** 현재 모드에서 이 코드를 가릴 수 있는가 (모드와 자모 종류가 맞는지). */
export function isHidableInMode(code: number, mode: HideMode): boolean {
  const kind = jamoKindOf(code);
  if (kind === null) return false;
  switch (mode) {
    case 'ja':
      return kind === 'cho' || kind === 'jong';
    case 'cho':
      return kind === 'cho';
    case 'jung':
      return kind === 'jung';
    case 'jong':
      return kind === 'jong';
  }
}

/** 한 글자에서 실제로 가려질 자모 종류를 계산한다. 렌더러가 이걸 보고 글리프를 숨긴다. */
export function hiddenPartsOf(
  glyph: HangulGlyph,
  rule: HideRule,
): { cho: boolean; jung: boolean; jong: boolean } {
  if (glyph.specialFlag) {
    return { cho: false, jung: false, jong: false };
  }
  const set = new Set(rule.codes.filter((c) => isHidableInMode(c, rule.mode)));
  return {
    cho: glyph.choCode !== undefined && set.has(glyph.choCode),
    jung: glyph.jungCode !== undefined && set.has(glyph.jungCode),
    jong: glyph.jongCode !== undefined && set.has(glyph.jongCode),
  };
}

/** 모드에 해당하는 모든 자모를 가린다 (원본의 `all-*` 전체 토글). */
export function allCodesForMode(mode: HideMode): number[] {
  const codes: number[] = [];
  const push = (from: number, to: number) => {
    for (let c = from; c <= to; c++) codes.push(c);
  };
  if (mode === 'cho' || mode === 'ja') push(0x1100, 0x1112);
  if (mode === 'jung') push(0x1161, 0x1175);
  if (mode === 'jong' || mode === 'ja') push(0x11a8, 0x11c2);
  return codes;
}

/** 코드 토글 (체크박스 on/off). 쌍 value 도 그대로 받는다. */
export function toggleCodes(rule: HideRule, value: string | number[], on: boolean): HideRule {
  const targets = typeof value === 'string' ? parseCheckboxValue(value) : value;
  const set = new Set(rule.codes);
  for (const code of targets) {
    if (on) set.add(code);
    else set.delete(code);
  }
  return { mode: rule.mode, codes: [...set].sort((a, b) => a - b) };
}

/**
 * 모드를 바꾸면 원본은 체크를 모두 풀고 `.hidebox` 를 걷어낸다
 * (`worksheet-app.js` 의 radio change 핸들러).
 */
export function changeMode(rule: HideRule, mode: HideMode): HideRule {
  return { mode, codes: rule.mode === mode ? rule.codes : [] };
}
