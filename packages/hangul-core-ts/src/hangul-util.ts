/**
 * jammin `HangulUtil.java` / `Hangul.java` 의 TypeScript 이식본.
 *
 * ⚠️ 이 파일의 동작은 jammin 원본과 **바이트 단위로 같아야** 한다.
 *    변경 시 반드시 `golden/hangul-split.json` 테스트를 통과시킬 것.
 *    원본: jammin/src/main/java/com/jammin/util/HangulUtil.java
 */

/** jammin `Hangul.toJSON()` 과 동일한 형태. 조건부 키는 원본과 같이 생략된다. */
export interface HangulGlyph {
  /** 한글 음절일 때만 존재 */
  word?: string;
  chosung?: string;
  choCode?: number;
  jungsung?: string;
  jungCode?: number;
  /** 종성이 없으면 키 자체가 없다 (`emptyJongsung=true`) */
  jongsung?: string;
  jongCode?: number;

  /** 특수문자·숫자일 때만 존재 */
  specialType?: string;
  specialTypeCode?: number;

  specialFlag: boolean;
  emptyJongsung: boolean;
  errorFlag: boolean;
}

/** 특수문자 — 원본 `specialTypeArray` */
const SPECIAL_CHARS = [' ', '.', ',', '?', '!'] as const;
/** 숫자 — 원본 `numberTypeArray` */
const NUMBER_CHARS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'] as const;

/** 종성 없음 표식. 원본이 cho/jung/jong 모두에 대해 이 값과 비교한다. */
const NO_JONG = 4519; // 0x11A7

const HANGUL_SYLLABLES_START = 0xac00;
/** Java `Character.UnicodeBlock.HANGUL_SYLLABLES` 는 AC00–D7AF 이다 (실제 음절은 D7A3까지). */
const HANGUL_SYLLABLES_END = 0xd7af;

function isSpecialType(ch: string): boolean {
  return (SPECIAL_CHARS as readonly string[]).includes(ch);
}

function isNumberType(ch: string): boolean {
  return (NUMBER_CHARS as readonly string[]).includes(ch);
}

/**
 * jammin `HangulUtil.isHangul` — 모든 문자가 완성형 한글 음절 / 특수문자 5종 / 숫자여야 true.
 *
 * 자모 단독(ㄱ, ㅏ), 영문, 한자는 false. 빈 문자열은 true(원본 루프가 돌지 않음).
 */
export function isHangul(str: string): boolean {
  let checkFlag = true;
  for (const ch of str) {
    const code = ch.codePointAt(0)!;
    const inSyllables = code >= HANGUL_SYLLABLES_START && code <= HANGUL_SYLLABLES_END;
    if (inSyllables || isSpecialType(ch) || isNumberType(ch)) {
      continue;
    }
    checkFlag = false;
  }
  return checkFlag;
}

/**
 * jammin `HangulUtil.hangulSplit` — 문자열을 글자 단위로 분해한다.
 *
 * 유의: 원본은 `char comVal = (char)(ch - 0xAC00)` 로 **16비트 무부호 래핑**을 한다.
 * 'A' 처럼 0xAC00 보다 작은 문자는 음수가 아니라 큰 양수가 되어 `<= 11172` 검사에서 걸러진다.
 * 이 이식본도 `& 0xFFFF` 로 같은 래핑을 재현한다.
 */
export function hangulSplit(s: string): HangulGlyph[] {
  const result: HangulGlyph[] = [];

  for (const ch of s) {
    if (isSpecialType(ch) || isNumberType(ch)) {
      result.push({
        specialFlag: true,
        specialType: ch,
        specialTypeCode: ch.codePointAt(0)!,
        // 원본은 특수문자에도 기본값을 그대로 실어 보낸다.
        emptyJongsung: true,
        errorFlag: false,
      });
      continue;
    }

    const glyph: HangulGlyph = {
      word: ch,
      specialFlag: false,
      emptyJongsung: true,
      errorFlag: false,
    };

    const comVal = (ch.codePointAt(0)! - HANGUL_SYLLABLES_START) & 0xffff;
    if (comVal <= 11172) {
      const uniVal = comVal;
      const cho = Math.floor(Math.floor((uniVal - (uniVal % 28)) / 28) / 21) + 0x1100;
      const jung = (Math.floor((uniVal - (uniVal % 28)) / 28) % 21) + 0x1161;
      const jong = (uniVal % 28) + 0x11a7;

      if (cho !== NO_JONG) {
        glyph.chosung = String.fromCharCode(cho);
        glyph.choCode = cho;
      }
      if (jung !== NO_JONG) {
        glyph.jungsung = String.fromCharCode(jung);
        glyph.jungCode = jung;
      }
      if (jong !== NO_JONG) {
        glyph.jongsung = String.fromCharCode(jong);
        glyph.jongCode = jong;
      }
      glyph.emptyJongsung = glyph.jongsung === undefined;
      glyph.errorFlag = false;
    } else {
      glyph.errorFlag = true;
    }

    result.push(glyph);
  }

  return result;
}

/**
 * jammin `POST /addWord` 와 동일한 응답을 만든다.
 * 허용 문자 집합을 벗어나면 **빈 배열** — 원본이 그렇게 거부한다.
 */
export function addWord(requestWord: string): HangulGlyph[] {
  if (!isHangul(requestWord)) return [];
  return hangulSplit(requestWord);
}

/** 출제 제약 — 원본 `worksheet-app.js` 의 하드코딩 값. */
export const WORKSHEET_LIMITS = {
  /** 한 문장 최대 글자 수 */
  maxGlyphsPerSentence: 16,
  /** 학습지 전체 최대 줄 수 */
  maxRows: 20,
  /** 한 줄당 글자 수 (`profiles.js` lineBreakCount) */
  glyphsPerRow: 8,
} as const;
