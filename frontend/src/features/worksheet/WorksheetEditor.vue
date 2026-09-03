<script setup lang="ts">
/**
 * 받아쓰기 학습지 편집기 — jammin `/test` 화면의 재구현.
 *
 * 원본과의 차이(의도적 개선):
 *  - 자모 분해가 온디바이스라 서버 왕복이 없다(오프라인 가능)
 *  - 가리기 설정이 데이터로 남아 저장·재사용된다(프리셋)
 *  - 인쇄가 CSS `@page` 기반이라 880px 하드코딩이 필요 없다
 *
 * 톤: 학습지(종이 결과물)는 도담도담체 + jammin 골든 옐로우,
 *     조작 영역(작업 도구)은 단색 실무 톤으로 분리한다.
 */
import { computed, onMounted, ref, watch } from 'vue';
import { WORKSHEET_LIMITS, optionsOf, type HideMode } from '@chominjungum/hangul-core';

import JamoToggleGrid from './JamoToggleGrid.vue';
import WorksheetItemRow from './WorksheetItemRow.vue';
import { useWorksheet } from './useWorksheet';
import { takeHandoff, useWorksheetHandoff } from './handoff';
import type { ProfileKey } from './profiles';

const {
  state,
  totalRows,
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
  persist,
  restore,
} = useWorksheet();

const input = ref('');
const message = ref<{ tone: 'ok' | 'warn'; text: string } | null>(null);
const editingId = ref<string | null>(null);
const presetName = ref('');
/** 조작 영역을 감추고 인쇄될 모습만 본다 */
const previewMode = ref(false);

const FONT_SIZES = [60, 80, 100, 120] as const;
const scale = computed(() => state.value.fontSize / 100);
const itemCount = computed(() => state.value.items.length);

function notify(tone: 'ok' | 'warn', text: string) {
  message.value = { tone, text };
}

function printSheet() {
  window.print();
}

function submit() {
  const result = editingId.value
    ? updateItem(editingId.value, input.value)
    : addItem(input.value);

  if (!result.ok) {
    notify('warn', result.reason);
    return;
  }
  notify('ok', editingId.value ? '문제 수정 완료' : '문제 추가 완료');
  input.value = '';
  editingId.value = null;
}

function startEdit(id: string) {
  const item = state.value.items.find((i) => i.id === id);
  if (!item) return;
  editingId.value = id;
  input.value = item.text;
}

function cancelEdit() {
  editingId.value = null;
  input.value = '';
}

function remove(id: string) {
  if (editingId.value === id) cancelEdit();
  removeItem(id);
}

function toggleAll(on: boolean) {
  for (const opt of optionsOf(state.value.hideRule.mode)) {
    toggleJamo(opt.value, on);
  }
}

function changeMode(mode: HideMode) {
  setHideMode(mode);
}

function clearAll() {
  if (itemCount.value === 0) return;
  clear();
  cancelEdit();
  notify('ok', '학습지를 비웠습니다.');
}

// ── 프리셋 ────────────────────────────────────────────────
function onSavePreset() {
  const saved = savePreset(presetName.value, window.localStorage);
  if (!saved) {
    notify('warn', '프리셋 이름을 입력하세요.');
    return;
  }
  presetName.value = '';
  notify('ok', `"${saved.name}" 설정을 저장했습니다.`);
}

function onApplyPreset(id: string) {
  if (applyPreset(id)) notify('ok', '저장한 가리기 설정을 적용했습니다.');
}

function onRemovePreset(id: string) {
  removePreset(id, window.localStorage);
}

// ── 드래그 정렬 ───────────────────────────────────────────
const dragId = ref<string | null>(null);
const dragOverIndex = ref<number | null>(null);

function onDragStart(id: string) {
  dragId.value = id;
}

function onDragOver(index: number) {
  dragOverIndex.value = index;
}

function onDrop(index: number) {
  if (dragId.value) moveItemTo(dragId.value, index);
  dragId.value = null;
  dragOverIndex.value = null;
}

function onDragEnd() {
  dragId.value = null;
  dragOverIndex.value = null;
}

/** 리포트에서 넘어온 가리기 규칙을 받아 적용한다. */
function consumeHandoff() {
  const handoff = takeHandoff();
  if (!handoff) return;
  state.value.hideRule = handoff.rule;
  previewMode.value = false;
  notify('ok', handoff.note);
}

const { tabRequest } = useWorksheetHandoff();
watch(tabRequest, consumeHandoff);

onMounted(() => {
  restore(window.localStorage);
  loadPresets(window.localStorage);
  persist(window.localStorage);
  consumeHandoff();
});
</script>

<template>
  <div class="editor">
    <!-- 인쇄·미리보기에서 빠지는 조작 영역 -->
    <section v-show="!previewMode" class="controls">
      <h1>받아쓰기 학습지</h1>
      <p class="hint">
        문장을 추가하고 가릴 자모를 고른 뒤 인쇄하세요. 한 문장 최대
        {{ WORKSHEET_LIMITS.maxGlyphsPerSentence }}글자, 학습지 전체
        {{ WORKSHEET_LIMITS.maxRows }}줄까지입니다.
      </p>

      <div class="add-row">
        <input
          v-model="input"
          type="text"
          :placeholder="editingId ? '문항 수정' : '예: 안녕하세요'"
          @keyup.enter="submit"
        />
        <button type="button" class="primary" @click="submit">
          {{ editingId ? '수정' : '추가' }}
        </button>
        <button v-if="editingId" type="button" @click="cancelEdit">취소</button>
      </div>

      <p v-if="message" class="msg" :class="message.tone">{{ message.text }}</p>

      <JamoToggleGrid
        :mode="state.hideRule.mode"
        :is-checked="isJamoChecked"
        @change-mode="changeMode"
        @toggle="toggleJamo"
        @toggle-all="toggleAll"
      />

      <!-- 가리기 프리셋 -->
      <div class="preset-row">
        <input
          v-model="presetName"
          class="preset-name"
          placeholder="이 가리기 설정 저장 (예: 받침 빼기)"
          @keyup.enter="onSavePreset"
        />
        <button type="button" @click="onSavePreset">저장</button>
        <span v-for="p in presets" :key="p.id" class="preset">
          <button type="button" class="preset-apply" @click="onApplyPreset(p.id)">{{ p.name }}</button>
          <button
            type="button"
            class="preset-del"
            title="프리셋 삭제"
            @click="onRemovePreset(p.id)"
          >
            ×
          </button>
        </span>
      </div>

      <div class="layout-row">
        <label>
          <span>글자 크기</span>
          <select
            :value="state.fontSize"
            @change="setFontSize(Number(($event.target as HTMLSelectElement).value))"
          >
            <option v-for="size in FONT_SIZES" :key="size" :value="size">{{ size }}%</option>
          </select>
        </label>

        <label>
          <span>칸 모양</span>
          <select
            :value="state.profileKey"
            @change="setProfile(($event.target as HTMLSelectElement).value as ProfileKey)"
          >
            <option value="editor">넓게 (editor)</option>
            <option value="compact">좁게 (compact)</option>
          </select>
        </label>

        <label class="title-field">
          <span>학습지 제목</span>
          <input v-model="state.title" type="text" />
        </label>
      </div>

      <div class="actions">
        <span class="rows">
          {{ itemCount }}문항 · {{ totalRows }}/{{ WORKSHEET_LIMITS.maxRows }}줄
        </span>
        <button type="button" @click="clearAll">전체 비우기</button>
        <button type="button" :disabled="itemCount === 0" @click="previewMode = true">
          인쇄 미리보기
        </button>
        <button type="button" class="primary" :disabled="itemCount === 0" @click="printSheet">
          인쇄
        </button>
      </div>
    </section>

    <!-- 미리보기 모드 상단 바 -->
    <div v-if="previewMode" class="preview-bar">
      <span>인쇄될 모습입니다</span>
      <button type="button" @click="previewMode = false">편집으로 돌아가기</button>
      <button type="button" class="primary" @click="printSheet">인쇄</button>
    </div>

    <!-- 실제 학습지 (인쇄 대상) -->
    <section class="sheet sheet-typography" :class="{ previewing: previewMode }">
      <header class="sheet-head">
        <h2>{{ state.title }}</h2>
        <div class="who">
          <span>이름 __________</span>
          <span>날짜 __________</span>
        </div>
      </header>

      <p v-if="itemCount === 0" class="empty">문장을 추가하면 여기에 학습지가 만들어집니다.</p>

      <WorksheetItemRow
        v-for="(item, i) in state.items"
        :key="item.id"
        :item="item"
        :index="i"
        :profile-key="state.profileKey"
        :hide-rule="state.hideRule"
        :scale="scale"
        :editable="!previewMode"
        :is-first="i === 0"
        :is-last="i === itemCount - 1"
        :drop-target="dragOverIndex === i && dragId !== item.id"
        @edit="startEdit(item.id)"
        @remove="remove(item.id)"
        @move="(d) => moveItem(item.id, d)"
        @drag-start="onDragStart(item.id)"
        @drag-over="onDragOver(i)"
        @drop="onDrop(i)"
        @drag-end="onDragEnd"
      />
    </section>
  </div>
</template>

<style scoped>
.editor {
  max-width: 1000px;
  margin: 0 auto;
  padding: 28px 20px 60px;
  color: var(--text);
}

h1 {
  margin: 0 0 4px;
  font-size: 22px;
}

.hint {
  margin: 0 0 16px;
  color: var(--text-muted);
  font-size: 13px;
}

/* ── 조작 영역: 단색 실무 톤 ─────────────────────── */
.controls {
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface-sunken);
}

.add-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.add-row input {
  flex: 1;
  padding: 9px 12px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-size: 16px;
}

button {
  padding: 8px 14px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  font-size: 14px;
  cursor: pointer;
}

button.primary {
  border-color: var(--tool-brand);
  background: var(--tool-brand);
  color: #fff;
  font-weight: 600;
}

button:disabled {
  opacity: 0.5;
  cursor: default;
}

.msg {
  margin: 0 0 12px;
  font-size: 14px;
}

.msg.ok {
  color: var(--success);
}

.msg.warn {
  color: var(--danger);
}

.preset-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.preset-name {
  flex: 0 1 260px;
  padding: 6px 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-size: 13px;
}

.preset {
  display: inline-flex;
  align-items: stretch;
  margin-left: 2px;
}

.preset-apply {
  padding: 5px 9px;
  border-radius: var(--radius) 0 0 var(--radius);
  border-right: none;
  font-size: 13px;
}

.preset-del {
  padding: 5px 7px;
  border-radius: 0 var(--radius) var(--radius) 0;
  color: var(--text-faint);
  font-size: 13px;
}

.layout-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 14px;
  font-size: 14px;
}

.layout-row label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.layout-row select,
.layout-row input {
  padding: 6px 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.title-field {
  flex: 1;
  min-width: 200px;
}

.title-field input {
  flex: 1;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
}

.rows {
  margin-right: auto;
  color: var(--text-muted);
  font-size: 13px;
}

.preview-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  padding: 10px 14px;
  border: 1px solid var(--sheet-accent-deep);
  border-radius: var(--radius);
  background: var(--sheet-accent-soft);
  font-size: 14px;
}

.preview-bar span {
  margin-right: auto;
}

/* ── 학습지: jammin 원본 톤 + 손글씨체 ──────────── */
.sheet {
  margin-top: 28px;
}

.sheet.previewing {
  margin-top: 0;
  padding: 24px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: 0 2px 12px rgb(15 23 42 / 8%);
}

.sheet-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18px;
  padding-bottom: 10px;
  border-bottom: 3px solid var(--sheet-accent);
}

.sheet-head h2 {
  margin: 0;
  font-size: 24px;
  color: var(--sheet-ink);
}

.who {
  display: flex;
  gap: 16px;
  color: var(--text-muted);
  font-size: 15px;
}

.empty {
  color: var(--text-faint);
  font-size: 14px;
}

@media print {
  .controls,
  .preview-bar {
    display: none;
  }

  .editor {
    max-width: none;
    padding: 0;
  }

  .sheet,
  .sheet.previewing {
    margin-top: 0;
    padding: 0;
    border: none;
    box-shadow: none;
  }
}
</style>
