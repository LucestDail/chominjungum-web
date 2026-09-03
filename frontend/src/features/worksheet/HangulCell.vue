<script setup lang="ts">
/**
 * 받아쓰기 한 칸 — 초/중/종성을 고정 비율 위치에 겹친다.
 *
 * 자산이 규격화돼 있어(부위마다 캔버스가 하나씩) 배치는 `hangul-metrics.ts` 의
 * 비율 상수로 완전히 결정된다. **`size` 하나만 바꾸면** 전체가 비례해서 커지고,
 * 어떤 크기에서도 초/중/종 위치가 서로 어긋나지 않는다.
 *
 * 이전에는 부위마다 픽셀 height·top 을 따로 줬는데(jammin 원본 방식),
 * 그 수치들은 결국 같은 비율을 손으로 계산해 반올림한 값이었다 — metrics 주석 참고.
 */
import { computed } from 'vue';
import type { HangulGlyph } from '@chominjungum/hangul-core';
import { hiddenPartsOf, type HideRule } from '@chominjungum/hangul-core';

import {
  BACKGROUND,
  BACKGROUND_SRC,
  CHO,
  glyphSrc,
  JONG,
  JUNG,
  placeSpecial,
  toPercent,
} from './hangul-metrics';

const props = withDefaults(
  defineProps<{
    glyph: HangulGlyph;
    hideRule?: HideRule | null;
    /** 한 칸의 변 길이(px). 이 값 하나로 전체가 비례한다. */
    size?: number;
    /** 원고지 안내선(빈 칸) 표시 */
    showGuide?: boolean;
  }>(),
  { hideRule: null, size: 92, showGuide: true },
);

const hidden = computed(() =>
  props.hideRule
    ? hiddenPartsOf(props.glyph, props.hideRule)
    : { cho: false, jung: false, jong: false },
);

/** 배치는 상수라 한 번만 계산하면 된다. */
const boxes = {
  background: toPercent(BACKGROUND),
  cho: toPercent(CHO),
  jung: toPercent(JUNG),
  jong: toPercent(JONG),
};

const special = computed(() => {
  if (!props.glyph.specialFlag || props.glyph.specialTypeCode === undefined) return null;
  return {
    src: glyphSrc(props.glyph.specialTypeCode),
    box: toPercent(placeSpecial(props.glyph.specialTypeCode)),
  };
});

const cellStyle = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }));
</script>

<template>
  <div class="hangul-cell" :style="cellStyle">
    <!-- 원고지 안내선 -->
    <img v-if="showGuide" class="layer guide" :src="BACKGROUND_SRC" :style="boxes.background" alt="" />

    <!-- 특수문자·숫자: 폭을 상자에 맞추고 높이는 캔버스 비율대로 -->
    <img
      v-if="special"
      class="layer"
      :src="special.src"
      :style="special.box"
      :alt="glyph.specialType"
    />

    <!-- 한글: 중성 → 초성 → 종성 순으로 겹친다 -->
    <template v-else>
      <img
        v-if="glyph.jungCode !== undefined"
        class="layer jung"
        :class="{ hidebox: hidden.jung }"
        :src="glyphSrc(glyph.jungCode)"
        :style="boxes.jung"
        :alt="glyph.jungsung"
      />
      <img
        v-if="glyph.choCode !== undefined"
        class="layer cho"
        :class="{ hidebox: hidden.cho }"
        :src="glyphSrc(glyph.choCode)"
        :style="boxes.cho"
        :alt="glyph.chosung"
      />
      <img
        v-if="glyph.jongCode !== undefined"
        class="layer jong"
        :class="{ hidebox: hidden.jong }"
        :src="glyphSrc(glyph.jongCode)"
        :style="boxes.jong"
        :alt="glyph.jongsung"
      />
    </template>
  </div>
</template>

<style scoped>
.hangul-cell {
  position: relative;
  flex: 0 0 auto;
}

.layer {
  position: absolute;
  /* 조각마다 종횡비가 정해져 있으므로 지정한 상자를 그대로 채운다 */
  display: block;
}

/* jammin 의 `.hidebox` — 자리를 유지한 채 감춘다(빈칸 학습지) */
.hidebox {
  visibility: hidden;
}
</style>
