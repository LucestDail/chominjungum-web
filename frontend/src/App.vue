<script setup lang="ts">
/**
 * Phase 0 스캐폴딩 확인용 화면.
 * 자모 분해(hangul-core-ts) → SVG 자산 → 가리기 규칙까지 배선이 살아 있는지 눈으로 본다.
 * Phase 1 에서 학습지 편집기로 확장된다.
 */
import { computed, ref } from 'vue';
import {
  addWord,
  allCodesForMode,
  EMPTY_HIDE_RULE,
  WORKSHEET_LIMITS,
  type HideMode,
  type HideRule,
} from '@chominjungum/hangul-core';

import HangulCell from './features/worksheet/HangulCell.vue';

const sentence = ref('안녕하세요');
const hideMode = ref<HideMode | 'none'>('none');

const glyphs = computed(() => addWord(sentence.value));
const rejected = computed(() => sentence.value.length > 0 && glyphs.value.length === 0);
const tooLong = computed(() => glyphs.value.length > WORKSHEET_LIMITS.maxGlyphsPerSentence);

const hideRule = computed<HideRule | null>(() => {
  if (hideMode.value === 'none') return null;
  return { ...EMPTY_HIDE_RULE, mode: hideMode.value, codes: allCodesForMode(hideMode.value) };
});
</script>

<template>
  <main>
    <h1>초민정음 Web</h1>
    <p class="sub">Phase 0 — 자모 분해 · SVG 자산 · 가리기 규칙 배선 확인</p>

    <label class="field">
      <span>받아쓰기 문장</span>
      <input v-model="sentence" type="text" placeholder="예: 안녕하세요" />
    </label>

    <div class="modes">
      <label v-for="m in (['none', 'ja', 'cho', 'jung', 'jong'] as const)" :key="m">
        <input v-model="hideMode" type="radio" :value="m" />
        {{ { none: '가리기 없음', ja: '초성+종성', cho: '초성', jung: '중성', jong: '종성' }[m] }}
      </label>
    </div>

    <p v-if="rejected" class="warn">
      한글이 아니거나, 완성된 한글 단어가 아닌 문자가 포함되어있습니다.
    </p>
    <p v-else-if="tooLong" class="warn">
      {{ WORKSHEET_LIMITS.maxGlyphsPerSentence }}글자 이상의 문장은 추가할 수 없습니다.
    </p>

    <div class="row">
      <HangulCell
        v-for="(glyph, i) in glyphs"
        :key="i"
        :glyph="glyph"
        :hide-rule="hideRule"
      />
    </div>
  </main>
</template>

<style scoped>
main {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 20px;
  font-family: system-ui, -apple-system, sans-serif;
  color: #0f172a;
}

h1 {
  margin: 0 0 4px;
  font-size: 24px;
}

.sub {
  margin: 0 0 24px;
  color: #64748b;
  font-size: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 420px;
  font-size: 14px;
}

.field input {
  padding: 8px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font-size: 16px;
}

.modes {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 16px 0;
  font-size: 14px;
}

.warn {
  color: #b91c1c;
  font-size: 14px;
}

.row {
  display: flex;
  flex-wrap: wrap;
  margin-top: 16px;
}
</style>
