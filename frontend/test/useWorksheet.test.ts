import { describe, expect, it } from 'vitest';

import { REJECT_MESSAGES } from '../src/features/worksheet/types';
import { rowsForGlyphCount, useWorksheet } from '../src/features/worksheet/useWorksheet';

describe('줄 수 계산 (jammin worksheet-app.js:162 준용)', () => {
  it('8글자까지 1줄, 9글자부터 2줄', () => {
    expect(rowsForGlyphCount(0)).toBe(0);
    expect(rowsForGlyphCount(1)).toBe(1);
    expect(rowsForGlyphCount(8)).toBe(1);
    expect(rowsForGlyphCount(9)).toBe(2);
    expect(rowsForGlyphCount(16)).toBe(2);
  });
});

describe('문항 추가 제약', () => {
  it('정상 문장은 추가된다', () => {
    const ws = useWorksheet();
    expect(ws.addItem('안녕하세요')).toEqual({ ok: true });
    expect(ws.state.value.items).toHaveLength(1);
    expect(ws.state.value.items[0]!.glyphs).toHaveLength(5);
  });

  it('빈 입력은 거부', () => {
    const ws = useWorksheet();
    expect(ws.addItem('   ')).toEqual({ ok: false, reason: REJECT_MESSAGES.empty });
  });

  it('허용 밖 문자는 원본 문구로 거부', () => {
    const ws = useWorksheet();
    expect(ws.addItem('Hello')).toEqual({ ok: false, reason: REJECT_MESSAGES.notHangul });
    expect(ws.addItem('ㄱㄴㄷ')).toEqual({ ok: false, reason: REJECT_MESSAGES.notHangul });
  });

  it('17글자는 거부, 16글자는 통과', () => {
    const ws = useWorksheet();
    expect(ws.addItem('가나다라마바사아자차카타파하거너더')).toEqual({
      ok: false,
      reason: REJECT_MESSAGES.tooLong,
    });
    expect(ws.addItem('가나다라마바사아자차카타파하거너')).toEqual({ ok: true });
  });

  it('20줄을 넘기면 거부', () => {
    const ws = useWorksheet();
    // 9글자 = 2줄씩 10개 = 20줄
    for (let i = 0; i < 10; i++) {
      expect(ws.addItem('가나다라마바사아자')).toEqual({ ok: true });
    }
    expect(ws.totalRows.value).toBe(20);
    expect(ws.addItem('가')).toEqual({ ok: false, reason: REJECT_MESSAGES.tooManyRows });
  });

  it('수정은 자기 줄 수를 반납하고 검사한다', () => {
    const ws = useWorksheet();
    for (let i = 0; i < 9; i++) ws.addItem('가나다라마바사아자'); // 18줄
    ws.addItem('가나'); // 1줄 → 19줄
    const shortId = ws.state.value.items.at(-1)!.id;

    // 1줄짜리를 2줄짜리로 바꾸면 20줄 → 허용
    expect(ws.updateItem(shortId, '가나다라마바사아자')).toEqual({ ok: true });
    expect(ws.totalRows.value).toBe(20);
  });
});

describe('문항 조작', () => {
  it('삭제', () => {
    const ws = useWorksheet();
    ws.addItem('하나');
    ws.addItem('두울');
    const id = ws.state.value.items[0]!.id;
    ws.removeItem(id);
    expect(ws.state.value.items).toHaveLength(1);
    expect(ws.state.value.items[0]!.text).toBe('두울');
  });

  it('순서 이동', () => {
    const ws = useWorksheet();
    ws.addItem('하나');
    ws.addItem('두울');
    ws.addItem('세엣');
    const second = ws.state.value.items[1]!.id;

    expect(ws.moveItem(second, -1)).toBe(true);
    expect(ws.state.value.items.map((i) => i.text)).toEqual(['두울', '하나', '세엣']);

    expect(ws.moveItem(second, -1)).toBe(false); // 이미 처음
    expect(ws.state.value.items.map((i) => i.text)).toEqual(['두울', '하나', '세엣']);
  });

  it('수정하면 글리프가 다시 계산된다', () => {
    const ws = useWorksheet();
    ws.addItem('가');
    const id = ws.state.value.items[0]!.id;
    ws.updateItem(id, '안녕하세요');
    expect(ws.state.value.items[0]!.glyphs).toHaveLength(5);
  });
});

describe('자모 가리기 상태', () => {
  it('쌍 코드를 토글하면 둘 다 들어간다', () => {
    const ws = useWorksheet();
    ws.toggleJamo('4352_4520', true);
    expect(ws.state.value.hideRule.codes).toEqual([4352, 4520]);
    expect(ws.isJamoChecked('4352_4520')).toBe(true);

    ws.toggleJamo('4352_4520', false);
    expect(ws.state.value.hideRule.codes).toEqual([]);
  });

  it('모드를 바꾸면 선택이 초기화된다', () => {
    const ws = useWorksheet();
    ws.toggleJamo('4352', true);
    ws.setHideMode('jung');
    expect(ws.state.value.hideRule).toEqual({ mode: 'jung', codes: [] });
  });
});

describe('저장·복원', () => {
  function memoryStorage(): Storage {
    const map = new Map<string, string>();
    return {
      get length() {
        return map.size;
      },
      clear: () => map.clear(),
      getItem: (k: string) => map.get(k) ?? null,
      key: (i: number) => [...map.keys()][i] ?? null,
      removeItem: (k: string) => void map.delete(k),
      setItem: (k: string, v: string) => void map.set(k, v),
    } as Storage;
  }

  it('저장한 학습지를 복원하면 글리프가 다시 분해된다', async () => {
    const storage = memoryStorage();
    const a = useWorksheet();
    a.persist(storage);
    a.addItem('안녕하세요');
    a.toggleJamo('4352_4520', true);
    a.state.value.title = '2학기 1회';

    // watch 는 비동기라 한 틱 기다린다
    await new Promise((r) => setTimeout(r, 0));

    const b = useWorksheet();
    expect(b.restore(storage)).toBe(true);
    expect(b.state.value.title).toBe('2학기 1회');
    expect(b.state.value.items).toHaveLength(1);
    expect(b.state.value.items[0]!.glyphs).toHaveLength(5);
    expect(b.state.value.hideRule.codes).toEqual([4352, 4520]);
  });

  it('저장본이 없으면 false', () => {
    const ws = useWorksheet();
    expect(ws.restore(memoryStorage())).toBe(false);
  });
});
