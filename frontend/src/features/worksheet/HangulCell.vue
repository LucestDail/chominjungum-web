<script setup lang="ts">
/**
 * 받아쓰기 한 칸 (jammin `hangulSet`).
 * 배경 원고지 칸 위에 초/중/종성 SVG 를 절대배치하는 원본 구조를 그대로 따른다.
 */
import { computed } from 'vue';
import type { HangulGlyph } from '@chominjungum/hangul-core';
import { hiddenPartsOf, type HideRule } from '@chominjungum/hangul-core';

import { PROFILES, specialHeight, type ProfileKey } from './profiles';

const props = withDefaults(
  defineProps<{
    glyph: HangulGlyph;
    profileKey?: ProfileKey;
    hideRule?: HideRule | null;
    /**
     * 글자 크기 배율. jammin `syncFontConfig` 는 모든 글리프 높이를 같은 값으로 덮어써
     * 초/중/종성 비율이 무너졌는데, 여기서는 **프로필 비율을 유지한 채** 확대·축소한다.
     */
    scale?: number;
  }>(),
  { profileKey: 'editor', hideRule: null, scale: 1 },
);

const profile = computed(() => PROFILES[props.profileKey]);
const px = (v: number) => `${v * props.scale}px`;

const hidden = computed(() =>
  props.hideRule
    ? hiddenPartsOf(props.glyph, props.hideRule)
    : { cho: false, jung: false, jong: false },
);

const src = (code: number) => `hangul/${code}.svg`;
</script>

<template>
  <div
    class="hangul-set"
    :style="{
      width: px(profile.cellWidth),
      height: px(profile.cellHeight),
    }"
  >
    <img
      class="hangul-background"
      :src="profile.background.src"
      :style="{
        height: px(profile.background.height),
        top: px(profile.background.top),
        left: px(profile.background.left),
      }"
      alt=""
    />

    <template v-if="glyph.specialFlag">
      <img
        class="glyph special"
        :src="src(glyph.specialTypeCode!)"
        :style="{
          height: px(specialHeight(glyph.specialTypeCode!, profile)),
          top: px(2),
          left: px(2),
        }"
        :alt="glyph.specialType"
      />
    </template>

    <template v-else>
      <img
        v-if="glyph.choCode !== undefined"
        class="glyph cho"
        :class="{ hidebox: hidden.cho }"
        :src="src(glyph.choCode)"
        :style="{ height: px(profile.cho.height), top: px(profile.cho.top), zIndex: profile.cho.zIndex }"
        :alt="glyph.chosung"
      />
      <img
        v-if="glyph.jungCode !== undefined"
        class="glyph jung"
        :class="{ hidebox: hidden.jung }"
        :src="src(glyph.jungCode)"
        :style="{ height: px(profile.jung.height), top: px(profile.jung.top) }"
        :alt="glyph.jungsung"
      />
      <img
        v-if="glyph.jongCode !== undefined"
        class="glyph jong"
        :class="{ hidebox: hidden.jong }"
        :src="src(glyph.jongCode)"
        :style="{ height: px(profile.jong.height), top: px(profile.jong.top) }"
        :alt="glyph.jongsung"
      />
    </template>
  </div>
</template>

<style scoped>
.hangul-set {
  position: relative;
  flex: 0 0 auto;
}

.hangul-background,
.glyph {
  position: absolute;
}

/* jammin 의 `.hidebox` — 자리를 유지한 채 감춘다(빈칸 학습지) */
.hidebox {
  visibility: hidden;
}
</style>
