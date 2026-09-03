import { computed, ref, watch, type Ref } from 'vue';
import {
  addWord,
  EMPTY_HIDE_RULE,
  WORKSHEET_LIMITS,
  type HideMode,
  type HideRule,
} from '@chominjungum/hangul-core';

import type { ProfileKey } from './profiles';
import {
  REJECT_MESSAGES,
  type HidePreset,
  type WorksheetItem,
  type WorksheetOpResult,
  type WorksheetState,
} from './types';

const STORAGE_KEY = 'chominjungum.worksheet.v1';
const PRESET_KEY = 'chominjungum.hidePresets.v1';

/**
 * jammin 의 줄 수 계산 규칙 (`worksheet-app.js:162`):
 *   `hangulRowCount += jsonArray.length < 9 ? 1 : 2`
 * 8글자까지는 1줄, 9~16글자는 2줄로 센다.
 */
export function rowsForGlyphCount(count: number): number {
  if (count === 0) return 0;
  return count < 9 ? 1 : 2;
}

let seq = 0;
function nextId(): string {
  seq += 1;
  return `item-${Date.now().toString(36)}-${seq}`;
}

export function createWorksheetState(): WorksheetState {
  return {
    title: '받아쓰기',
    items: [],
    hideRule: { ...EMPTY_HIDE_RULE },
    profileKey: 'editor',
    fontSize: 60,
  };
}

export function useWorksheet(initial?: WorksheetState) {
  const state: Ref<WorksheetState> = ref(initial ?? createWorksheetState());

  const totalRows = computed(() =>
    state.value.items.reduce((sum, item) => sum + rowsForGlyphCount(item.glyphs.length), 0),
  );

  const remainingRows = computed(() => WORKSHEET_LIMITS.maxRows - totalRows.value);

  function validate(text: string, opts: { replacingRows?: number } = {}): WorksheetOpResult {
    const trimmed = text.trim();
    if (trimmed.length === 0) {
      return { ok: false, reason: REJECT_MESSAGES.empty };
    }
    const glyphs = addWord(trimmed);
    if (glyphs.length === 0) {
      return { ok: false, reason: REJECT_MESSAGES.notHangul };
    }
    if (glyphs.length > WORKSHEET_LIMITS.maxGlyphsPerSentence) {
      return { ok: false, reason: REJECT_MESSAGES.tooLong };
    }
    const needed = rowsForGlyphCount(glyphs.length) - (opts.replacingRows ?? 0);
    if (totalRows.value + needed > WORKSHEET_LIMITS.maxRows) {
      return { ok: false, reason: REJECT_MESSAGES.tooManyRows };
    }
    return { ok: true };
  }

  function addItem(text: string): WorksheetOpResult {
    const check = validate(text);
    if (!check.ok) return check;
    const trimmed = text.trim();
    state.value.items.push({ id: nextId(), text: trimmed, glyphs: addWord(trimmed) });
    return { ok: true };
  }

  function updateItem(id: string, text: string): WorksheetOpResult {
    const item = state.value.items.find((i) => i.id === id);
    if (!item) return { ok: false, reason: '문항을 찾을 수 없습니다.' };
    const check = validate(text, { replacingRows: rowsForGlyphCount(item.glyphs.length) });
    if (!check.ok) return check;
    const trimmed = text.trim();
    item.text = trimmed;
    item.glyphs = addWord(trimmed);
    return { ok: true };
  }

  function removeItem(id: string): void {
    state.value.items = state.value.items.filter((i) => i.id !== id);
  }

  function moveItem(id: string, delta: -1 | 1): boolean {
    const items = state.value.items;
    const index = items.findIndex((i) => i.id === id);
    if (index < 0) return false;
    return moveItemTo(id, index + delta);
  }

  /** 드래그 정렬용 — 문항을 지정한 자리로 옮긴다. */
  function moveItemTo(id: string, target: number): boolean {
    const items = state.value.items;
    const index = items.findIndex((i) => i.id === id);
    if (index < 0 || target < 0 || target >= items.length || target === index) return false;
    const [moved] = items.splice(index, 1);
    items.splice(target, 0, moved!);
    return true;
  }

  function setHideMode(mode: HideMode): void {
    // 원본 radio 핸들러: 모드를 바꾸면 선택이 모두 풀린다.
    state.value.hideRule = { mode, codes: [] };
  }

  function toggleJamo(value: string, on: boolean): void {
    const codes = new Set(state.value.hideRule.codes);
    for (const code of value.split('_').map((v) => Number.parseInt(v, 10))) {
      if (!Number.isInteger(code)) continue;
      if (on) codes.add(code);
      else codes.delete(code);
    }
    state.value.hideRule = {
      mode: state.value.hideRule.mode,
      codes: [...codes].sort((a, b) => a - b),
    };
  }

  function isJamoChecked(value: string): boolean {
    const codes = value.split('_').map((v) => Number.parseInt(v, 10));
    return codes.every((c) => state.value.hideRule.codes.includes(c));
  }

  function setProfile(profileKey: ProfileKey): void {
    state.value.profileKey = profileKey;
  }

  function setFontSize(size: number): void {
    state.value.fontSize = size;
  }

  function clear(): void {
    state.value = createWorksheetState();
  }

  function toJSON(): WorksheetState {
    return JSON.parse(JSON.stringify(state.value)) as WorksheetState;
  }

  /** localStorage 영속화 — 서버 저장은 Phase 2. */
  function persist(storage: Storage): void {
    watch(
      state,
      (v) => {
        try {
          storage.setItem(STORAGE_KEY, JSON.stringify(v));
        } catch {
          // 용량 초과·프라이빗 모드 등은 무시한다(학습지는 화면에 남아 있다).
        }
      },
      { deep: true },
    );
  }

  function restore(storage: Storage): boolean {
    try {
      const raw = storage.getItem(STORAGE_KEY);
      if (!raw) return false;
      const parsed = JSON.parse(raw) as Partial<WorksheetState>;
      if (!parsed || !Array.isArray(parsed.items)) return false;
      const base = createWorksheetState();
      state.value = {
        ...base,
        ...parsed,
        // 저장본의 glyphs 를 믿지 않고 텍스트에서 다시 분해한다(로직 변경에 안전).
        items: parsed.items.map((i) => ({
          id: i.id ?? nextId(),
          text: i.text ?? '',
          glyphs: addWord(i.text ?? ''),
        })),
        hideRule: (parsed.hideRule as HideRule) ?? base.hideRule,
      };
      return true;
    } catch {
      return false;
    }
  }

  // ── 가리기 프리셋 ─────────────────────────────────────────
  const presets: Ref<HidePreset[]> = ref([]);

  function loadPresets(storage: Storage): void {
    try {
      const raw = storage.getItem(PRESET_KEY);
      presets.value = raw ? (JSON.parse(raw) as HidePreset[]) : [];
    } catch {
      presets.value = [];
    }
  }

  function savePresets(storage: Storage): void {
    try {
      storage.setItem(PRESET_KEY, JSON.stringify(presets.value));
    } catch {
      // 저장 실패해도 현재 화면 동작은 유지된다
    }
  }

  /** 지금 가리기 설정을 이름 붙여 저장. 같은 이름이면 덮어쓴다. */
  function savePreset(name: string, storage: Storage): HidePreset | null {
    const trimmed = name.trim();
    if (!trimmed) return null;
    const rule: HideRule = JSON.parse(JSON.stringify(state.value.hideRule)) as HideRule;
    const existing = presets.value.find((p) => p.name === trimmed);
    if (existing) {
      existing.rule = rule;
    } else {
      presets.value.push({ id: nextId(), name: trimmed, rule });
    }
    savePresets(storage);
    return presets.value.find((p) => p.name === trimmed) ?? null;
  }

  function applyPreset(id: string): boolean {
    const preset = presets.value.find((p) => p.id === id);
    if (!preset) return false;
    state.value.hideRule = JSON.parse(JSON.stringify(preset.rule)) as HideRule;
    return true;
  }

  function removePreset(id: string, storage: Storage): void {
    presets.value = presets.value.filter((p) => p.id !== id);
    savePresets(storage);
  }

  return {
    state,
    totalRows,
    remainingRows,
    validate,
    addItem,
    updateItem,
    removeItem,
    moveItem,
    moveItemTo,
    presets,
    loadPresets,
    savePreset,
    applyPreset,
    removePreset,
    setHideMode,
    toggleJamo,
    isJamoChecked,
    setProfile,
    setFontSize,
    clear,
    toJSON,
    persist,
    restore,
  };
}

export { STORAGE_KEY, PRESET_KEY };
