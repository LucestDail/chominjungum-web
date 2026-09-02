import { describe, expect, it } from 'vitest';

import { score, scorePercent, summarizeMatches } from '../src/dictation-compare.js';

/** Dart `DictationCompare` 테스트와 같은 기대치를 유지한다. */
describe('DictationCompare (Dart 구현과 동일 규칙)', () => {
  it('완전 일치', () => {
    const r = score('가나', '가나');
    expect(r.correctCount).toBe(2);
    expect(r.totalCount).toBe(2);
    expect(r.ratio).toBe(1);
    expect(scorePercent(r)).toBe(100);
  });

  it('부분 일치 — 글자별 정오와 사유', () => {
    const r = score('가나', '가다');
    expect(r.correctCount).toBe(1);
    expect(r.totalCount).toBe(2);
    expect(r.matches[1]!.isCorrect).toBe(false);
    expect(r.matches[1]!.mismatchReason).toContain('글자 불일치');
  });

  it('받침 차이를 잡아낸다', () => {
    const r = score('간', '가');
    expect(r.correctCount).toBe(0);
    expect(r.matches[0]!.mismatchReason).toContain('글자 불일치');
  });

  it('답이 짧으면 누락으로 센다', () => {
    const r = score('안녕하세요', '안녕');
    expect(r.correctCount).toBe(2);
    expect(r.totalCount).toBe(5);
    expect(r.matches[2]!.mismatchReason).toBe('글자 누락');
    expect(scorePercent(r)).toBe(40);
  });

  it('초과 입력은 별도 match 로 덧붙고 플래그가 선다', () => {
    const r = score('가', '가나다');
    expect(r.totalCount).toBe(1);
    expect(r.correctCount).toBe(1);
    expect(r.hasExtraInput).toBe(true);
    expect(r.matches).toHaveLength(3);
    expect(r.matches[1]!.mismatchReason).toBe('불필요한 글자');
  });

  it('특수문자·숫자도 채점 대상', () => {
    const r = score('1반!', '1반?');
    expect(r.correctCount).toBe(2);
    expect(r.matches[2]!.mismatchReason).toContain('기호·숫자 불일치');
  });

  it('허용 밖 문자는 오류로 반환한다', () => {
    expect(score('abc', '가').error).toContain('정답이');
    expect(score('가', 'abc').error).toContain('답안이');
  });

  it('업싱크용 요약은 인덱스·정오·사유만 남긴다', () => {
    const summary = summarizeMatches(score('안녕하세요', '안녕하세오'));
    expect(summary).toHaveLength(5);
    expect(summary[0]).toEqual({ i: 0, ok: true });
    expect(summary[4]!.ok).toBe(false);
    expect(summary[4]!.why).toContain('글자 불일치');
  });
});
