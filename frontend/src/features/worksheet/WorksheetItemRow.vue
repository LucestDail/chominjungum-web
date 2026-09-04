<script setup lang="ts">
/**
 * 학습지 한 문항 — 번호 + 8글자마다 줄바꿈된 칸들 + 편집 툴바.
 * 줄바꿈 기준(8자)은 jammin `profiles.js` 의 `lineBreakCount` 를 따른다.
 */
import { computed, ref } from 'vue';
import { PROFILES, type ProfileKey } from './profiles';
import type { HideRule } from '@chominjungum/hangul-core';

import HangulCell from './HangulCell.vue';
import type { WorksheetItem } from './types';

const props = withDefaults(
  defineProps<{
    item: WorksheetItem;
    index: number;
    profileKey?: ProfileKey;
    hideRule: HideRule;
    scale?: number;
    editable?: boolean;
    isFirst?: boolean;
    isLast?: boolean;
    /** 드래그로 이 자리에 놓으려는 중 */
    dropTarget?: boolean;
  }>(),
  {
    profileKey: 'editor',
    scale: 1,
    editable: true,
    isFirst: false,
    isLast: false,
    dropTarget: false,
  },
);

const emit = defineEmits<{
  (e: 'edit'): void;
  (e: 'remove'): void;
  (e: 'move', delta: -1 | 1): void;
  (e: 'drag-start'): void;
  (e: 'drag-over'): void;
  (e: 'drop'): void;
  (e: 'drag-end'): void;
}>();

const dragging = ref(false);

const profile = computed(() => PROFILES[props.profileKey]);
/** 글자 크기 배율을 칸 크기에 곱한다 — 내부 배치는 비율이라 함께 커진다. */
const cellSize = computed(() => Math.round(profile.value.cellSize * props.scale));

const lines = computed(() => {
  const per = profile.value.lineBreakCount;
  const out: WorksheetItem['glyphs'][] = [];
  for (let i = 0; i < props.item.glyphs.length; i += per) {
    out.push(props.item.glyphs.slice(i, i + per));
  }
  return out;
});

function onDragStart() {
  dragging.value = true;
  emit('drag-start');
}

function onDragEnd() {
  dragging.value = false;
  emit('drag-end');
}
</script>

<template>
  <article
    class="worksheet-item"
    :class="{ dragging, 'drop-target': dropTarget }"
    :draggable="editable"
    @dragstart="onDragStart"
    @dragover.prevent="emit('drag-over')"
    @drop.prevent="emit('drop')"
    @dragend="onDragEnd"
  >
    <header>
      <span v-if="editable" class="grip" title="끌어서 순서 바꾸기">⠿</span>
      <span class="no">{{ index + 1 }}.</span>
      <span class="text">{{ item.text }}</span>
      <span class="count">{{ item.glyphs.length }}칸</span>

      <div v-if="editable" class="tools">
        <button type="button" :disabled="isFirst" title="위로" @click="emit('move', -1)">↑</button>
        <button type="button" :disabled="isLast" title="아래로" @click="emit('move', 1)">↓</button>
        <button type="button" title="수정" @click="emit('edit')">수정</button>
        <button type="button" class="danger" title="삭제" @click="emit('remove')">삭제</button>
      </div>
    </header>

    <div class="lines">
      <div
        v-for="(line, li) in lines"
        :key="li"
        class="line"
        :style="{ gap: `${profile.cellGap}px`, marginBottom: `${profile.rowGap}px` }"
      >
        <HangulCell
          v-for="(glyph, gi) in line"
          :key="`${li}-${gi}`"
          :glyph="glyph"
          :hide-rule="hideRule"
          :size="cellSize"
        />
      </div>
    </div>
  </article>
</template>

<style scoped>
.worksheet-item {
  /* 인쇄 시 한 문항이 페이지를 넘어 잘리지 않게 한다 (jammin 은 880px 하드코딩이었다) */
  break-inside: avoid;
  page-break-inside: avoid;
  margin-bottom: 18px;
  padding: 4px;
  border-radius: var(--radius);
  transition: background 0.12s, opacity 0.12s;
}

.worksheet-item.dragging {
  opacity: 0.45;
}

.worksheet-item.drop-target {
  background: var(--sheet-accent-soft);
  outline: 2px dashed var(--sheet-accent-deep);
  outline-offset: -2px;
}

header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 15px;
}

.grip {
  color: var(--text-faint);
  cursor: grab;
  user-select: none;
  font-size: 14px;
}

.no,
.text {
  font-family: var(--font-hand);
  font-size: 18px;
  color: var(--sheet-ink);
}

.no {
  font-weight: 700;
}

.count {
  color: var(--text-faint);
  font-size: 13px;
}

.tools {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.tools button {
  padding: 3px 8px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  font-size: 13px;
  cursor: pointer;
}

.tools button:disabled {
  opacity: 0.4;
  cursor: default;
}

.tools button.danger {
  color: var(--danger);
  border-color: #fca5a5;
}

.line {
  display: flex;
  flex-wrap: nowrap;
  align-items: flex-start;
}

.line:last-child {
  margin-bottom: 0 !important;
}

@media print {
  .tools,
  .grip,
  /* 칸 수는 편집할 때 보는 값이다 — 종이에는 남기지 않는다 */
  .count {
    display: none;
  }

  .worksheet-item {
    padding: 0;
  }
}
</style>
