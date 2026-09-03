import type { HangulGlyph, HideRule } from '@chominjungum/hangul-core';
export type { HideRule };

import type { ProfileKey } from './profiles';

/** 학습지 한 문항. */
export interface WorksheetItem {
  id: string;
  text: string;
  glyphs: HangulGlyph[];
}

/** 학습지 전체 상태 — 그대로 저장·재사용된다(jammin 은 저장이 불가능했다). */
export interface WorksheetState {
  title: string;
  items: WorksheetItem[];
  hideRule: HideRule;
  profileKey: ProfileKey;
  /** 글리프 높이(px). jammin `currentFontSize` 와 같은 역할. */
  fontSize: number;
}

/**
 * 자주 쓰는 가리기 설정에 이름을 붙여 재사용한다.
 * ("받침 빼기", "초성만 보여주기" 처럼 교사마다 반복하는 패턴이 있다)
 */
export interface HidePreset {
  id: string;
  name: string;
  rule: HideRule;
}

/** 문항 추가·수정 결과. 실패 사유는 원본 문구를 따른다. */
export type WorksheetOpResult = { ok: true } | { ok: false; reason: string };

export const REJECT_MESSAGES = {
  notHangul: '한글이 아니거나, 완성된 한글 단어가 아닌 문자가 포함되어있습니다.',
  tooLong: '16글자 이상의 문장은 추가할 수 없습니다.',
  tooManyRows: '학습지 전체 줄수는 20줄을 넘을 수 없습니다.',
  empty: '문장을 입력하세요.',
} as const;
