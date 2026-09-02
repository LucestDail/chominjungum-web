/**
 * 받아쓰기 채점 — chominjungum Flutter 앱의 `DictationCompare`(Dart) 와 동일 규칙.
 *
 * 원본: chominjungum/packages/hangul_core/lib/src/dictation_compare.dart
 * 두 구현이 갈라지면 교실 모드(앱)와 온라인 모드(웹)의 점수가 달라진다.
 */

import { hangulSplit, isHangul, type HangulGlyph } from './hangul-util.js';

/** 한 글자 단위 비교 결과. */
export interface GlyphMatch {
  index: number;
  expected: HangulGlyph | null;
  actual: HangulGlyph | null;
  isCorrect: boolean;
  mismatchReason?: string;
}

export interface DictationScoreResult {
  correctCount: number;
  totalCount: number;
  ratio: number;
  matches: GlyphMatch[];
  error?: string;
  hasExtraInput: boolean;
}

export function scorePercent(result: DictationScoreResult): number {
  return Math.round(result.ratio * 100);
}

/** 서버 업싱크·저장용 축약형 (`attempt.glyph_matches`). */
export interface GlyphMatchSummary {
  i: number;
  ok: boolean;
  why?: string;
}

export function summarizeMatches(result: DictationScoreResult): GlyphMatchSummary[] {
  return result.matches.map((m) => ({
    i: m.index,
    ok: m.isCorrect,
    ...(m.mismatchReason ? { why: m.mismatchReason } : {}),
  }));
}

function glyphEquals(e: HangulGlyph, a: HangulGlyph | null): boolean {
  if (a === null) return false;
  if (e.specialFlag !== a.specialFlag) return false;
  if (e.specialFlag) {
    return e.specialType === a.specialType;
  }
  if (e.errorFlag !== a.errorFlag) return false;
  return (
    e.word === a.word &&
    e.choCode === a.choCode &&
    e.jungCode === a.jungCode &&
    e.jongCode === a.jongCode
  );
}

function reason(e: HangulGlyph, a: HangulGlyph | null): string {
  if (a === null) return '글자 누락';
  if (e.specialFlag !== a.specialFlag) {
    return '문자 종류 불일치(한글/특수·숫자)';
  }
  if (e.specialFlag) {
    return `기호·숫자 불일치: "${e.specialType}" vs "${a.specialType}"`;
  }
  if (e.word !== a.word) {
    return `글자 불일치: "${e.word}" vs "${a.word}"`;
  }
  return '자모 불일치';
}

/**
 * [expected] 정답, [actual] 학생 답(키보드 또는 OCR 정규화 텍스트).
 * 정답률은 **정답 글자 수 기준**이고, 초과 입력은 별도 match로 덧붙는다.
 */
export function score(expected: string, actual: string): DictationScoreResult {
  if (!isHangul(expected)) {
    return {
      correctCount: 0,
      totalCount: 0,
      ratio: 0,
      matches: [],
      error: '정답이 jammin 허용 문자 집합이 아닙니다.',
      hasExtraInput: false,
    };
  }
  if (!isHangul(actual)) {
    return {
      correctCount: 0,
      totalCount: hangulSplit(expected).length,
      ratio: 0,
      matches: [],
      error: '답안이 jammin 허용 문자 집합이 아닙니다.',
      hasExtraInput: false,
    };
  }

  const eGlyphs = hangulSplit(expected);
  const aGlyphs = hangulSplit(actual);
  const matches: GlyphMatch[] = [];
  let correct = 0;

  for (let i = 0; i < eGlyphs.length; i++) {
    const e = eGlyphs[i]!;
    const a = i < aGlyphs.length ? aGlyphs[i]! : null;
    const ok = glyphEquals(e, a);
    if (ok) correct++;
    matches.push({
      index: i,
      expected: e,
      actual: a,
      isCorrect: ok,
      ...(ok ? {} : { mismatchReason: reason(e, a) }),
    });
  }

  for (let i = eGlyphs.length; i < aGlyphs.length; i++) {
    matches.push({
      index: i,
      expected: null,
      actual: aGlyphs[i]!,
      isCorrect: false,
      mismatchReason: '불필요한 글자',
    });
  }

  const total = eGlyphs.length;
  return {
    correctCount: correct,
    totalCount: total,
    ratio: total === 0 ? 1 : correct / total,
    matches,
    hasExtraInput: aGlyphs.length > eGlyphs.length,
  };
}
