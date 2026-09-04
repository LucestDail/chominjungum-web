<script setup lang="ts">
/**
 * jammin 원본이 그리는 **학습지 한 문항 전체**. 칸 하나가 아니라 줄바꿈까지 원본 그대로다.
 * 새 구현(`WorksheetItemRow`)과 나란히 놓고 종이 위 모습을 대조하기 위한 것.
 */
import { computed } from 'vue';
import type { HangulGlyph } from '@chominjungum/hangul-core';

import { buildHangulWrapperHtml } from './jammin-ref/glyph-renderer.js';

const props = withDefaults(
  defineProps<{
    glyphs: HangulGlyph[];
    text?: string;
    profileKey?: 'editor' | 'compact';
  }>(),
  { text: '', profileKey: 'editor' },
);

const html = computed(() => {
  const wrapper = buildHangulWrapperHtml({
    jsonArray: props.glyphs,
    fullWord: props.text,
    ordering: 0,
    profileKey: props.profileKey,
    toolbar: 'none',
    rowWrapperStyle: 'width:100%;',
  });
  const doc = new DOMParser().parseFromString(wrapper, 'text/html');
  // 편집 버튼은 종이에 안 나오므로 뺀다
  doc.querySelectorAll('button').forEach((b) => b.remove());
  return doc.body.innerHTML;
});
</script>

<template>
  <div class="jammin-sheet" v-html="html" />
</template>

<style>
.jammin-sheet img {
  max-width: none;
}

.jammin-sheet .hidebox {
  opacity: 0;
}

/* 원본 페이지의 Bootstrap `.row` 는 가로 flex 다 — 줄바꿈 모습을 재현하는 데 필요하다. */
.jammin-sheet .hangul-row {
  display: flex;
  flex-wrap: nowrap;
}

.jammin-sheet .hangulWrapper {
  display: flex;
}
</style>
