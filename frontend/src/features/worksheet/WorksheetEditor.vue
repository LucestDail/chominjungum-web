<script setup lang="ts">
/**
 * 받아쓰기 학습지 편집기 — jammin `/test` 화면의 재구현.
 *
 * 원본과의 차이(의도적 개선):
 *  - 자모 분해가 온디바이스라 서버 왕복이 없다(오프라인 가능)
 *  - 가리기 설정이 데이터로 남아 저장·재사용된다
 *  - 인쇄가 CSS `@page` 기반이라 880px 하드코딩이 필요 없다
 */
import { computed, onMounted, ref } from 'vue';
import { WORKSHEET_LIMITS, optionsOf, type HideMode } from '@chominjungum/hangul-core';

import JamoToggleGrid from './JamoToggleGrid.vue';
import WorksheetItemRow from './WorksheetItemRow.vue';
import { useWorksheet } from './useWorksheet';
import type { ProfileKey } from './profiles';

const {
  state,
  totalRows,
  addItem,
  updateItem,
  removeItem,
  moveItem,
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

const FONT_SIZES = [60, 80, 100, 120] as const;
const scale = computed(() => state.value.fontSize / 100);

const itemCount = computed(() => state.value.items.length);

function printSheet() {
  window.print();
}

function notify(tone: 'ok' | 'warn', text: string) {
  message.value = { tone, text };
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

onMounted(() => {
  restore(window.localStorage);
  persist(window.localStorage);
});
</script>

<template>
  <div class="editor">
    <!-- 인쇄에서 빠지는 조작 영역 -->
    <section class="controls">
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
        <button type="button" class="primary" :disabled="itemCount === 0" @click="printSheet">
          인쇄
        </button>
      </div>
    </section>

    <!-- 실제 학습지 (인쇄 대상) -->
    <section class="sheet">
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
        :is-first="i === 0"
        :is-last="i === itemCount - 1"
        @edit="startEdit(item.id)"
        @remove="remove(item.id)"
        @move="(d) => moveItem(item.id, d)"
      />
    </section>
  </div>
</template>

<style scoped>
.editor {
  max-width: 1000px;
  margin: 0 auto;
  padding: 28px 20px 60px;
  font-family: system-ui, -apple-system, sans-serif;
  color: #0f172a;
}

h1 {
  margin: 0 0 4px;
  font-size: 22px;
}

.hint {
  margin: 0 0 16px;
  color: #64748b;
  font-size: 13px;
}

.controls {
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.add-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.add-row input {
  flex: 1;
  padding: 9px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font-size: 16px;
}

button {
  padding: 8px 14px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  font-size: 14px;
  cursor: pointer;
}

button.primary {
  border-color: #296429;
  background: #296429;
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
  color: #1b6e32;
}

.msg.warn {
  color: #b91c1c;
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
  border: 1px solid #cbd5e1;
  border-radius: 6px;
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
  color: #64748b;
  font-size: 13px;
}

.sheet {
  margin-top: 28px;
}

.sheet-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18px;
  padding-bottom: 10px;
  border-bottom: 2px solid #0f172a;
}

.sheet-head h2 {
  margin: 0;
  font-size: 20px;
}

.who {
  display: flex;
  gap: 16px;
  color: #475569;
  font-size: 14px;
}

.empty {
  color: #94a3b8;
  font-size: 14px;
}

@media print {
  .controls {
    display: none;
  }

  .editor {
    max-width: none;
    padding: 0;
  }

  .sheet {
    margin-top: 0;
  }
}
</style>
