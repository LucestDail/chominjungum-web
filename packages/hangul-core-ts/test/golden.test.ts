import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import { addWord, type HangulGlyph } from '../src/hangul-util.js';

interface GoldenDoc {
  version: number;
  source: string;
  cases: { input: string; expected: HangulGlyph[] }[];
}

const goldenPath = fileURLToPath(new URL('../../../golden/hangul-split.json', import.meta.url));
const golden = JSON.parse(readFileSync(goldenPath, 'utf8')) as GoldenDoc;

/**
 * jammin 응답은 HashMap 기반이라 키 순서가 보장되지 않는다.
 * 순서를 뺀 "키-값 집합"으로 비교한다 — 키의 존재/부재는 그대로 검사한다.
 */
function normalize(glyph: HangulGlyph): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(glyph as unknown as Record<string, unknown>).sort(([a], [b]) =>
      a.localeCompare(b),
    ),
  );
}

describe('jammin 골든 벡터 동치성', () => {
  it('골든 파일이 비어 있지 않다', () => {
    expect(golden.cases.length).toBeGreaterThan(0);
  });

  for (const { input, expected } of golden.cases) {
    it(`addWord(${JSON.stringify(input)}) — ${expected.length}글자`, () => {
      const actual = addWord(input);
      expect(actual.map(normalize)).toEqual(expected.map(normalize));
    });
  }
});

describe('허용 문자 집합 (jammin isHangul)', () => {
  it('자모 단독·영문·한자·이모지는 출제 거부(빈 배열)', () => {
    for (const bad of ['ㄱ', 'ㅏ', 'abc', '漢', '🙂', '한글English']) {
      expect(addWord(bad)).toEqual([]);
    }
  });

  it('완성형 한글 + 특수문자 5종 + 숫자만 허용', () => {
    expect(addWord('안녕. 잘 가!').length).toBe(8);
    expect(addWord('1반, 2반?').length).toBe(7);
  });

  it('허용 밖 문자가 하나라도 섞이면 전체가 거부된다', () => {
    expect(addWord('안녕a')).toEqual([]);
  });
});
