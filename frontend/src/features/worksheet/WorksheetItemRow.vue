<script setup lang="ts">
/**
 * 학습지 한 문항 — 번호 + 8글자마다 줄바꿈된 칸들 + 편집 툴바.
 * 줄바꿈 기준(8자)은 jammin `profiles.js` 의 `lineBreakCount` 를 따른다.
 */
import { computed } from 'vue';
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
  }>(),
  { profileKey: 'editor', scale: 1, editable: true, isFirst: false, isLast: false },
);

const emit = defineEmits<{
  (e: 'edit'): void;
  (e: 'remove'): void;
  (e: 'move', delta: -1 | 1): void;
}>();

const lines = computed(() => {
  const per = PROFILES[props.profileKey].lineBreakCount;
  const out: WorksheetItem['glyphs'][] = [];
  for (let i = 0; i < props.item.glyphs.length; i += per) {
    out.push(props.item.glyphs.slice(i, i + per));
  }
  return out;
});
</script>

<template>
  <article class="worksheet-item">
    <header>
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
      <div v-for="(line, li) in lines" :key="li" class="line">
        <HangulCell
          v-for="(glyph, gi) in line"
          :key="`${li}-${gi}`"
          :glyph="glyph"
          :profile-key="profileKey"
          :hide-rule="hideRule"
          :scale="scale"
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
}

header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 15px;
}

.no {
  font-weight: 700;
}

.text {
  color: #0f172a;
}

.count {
  color: #94a3b8;
  font-size: 13px;
}

.tools {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.tools button {
  padding: 3px 8px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.tools button:disabled {
  opacity: 0.4;
  cursor: default;
}

.tools button.danger {
  color: #b91c1c;
  border-color: #fca5a5;
}

.line {
  display: flex;
  flex-wrap: nowrap;
}

@media print {
  .tools {
    display: none;
  }
}
</style>
