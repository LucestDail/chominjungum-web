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
  }>(),
  { profileKey: 'editor', hideRule: null },
);

const profile = computed(() => PROFILES[props.profileKey]);

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
      width: `${profile.cellWidth}px`,
      height: `${profile.cellHeight}px`,
    }"
  >
    <img
      class="hangul-background"
      :src="profile.background.src"
      :style="{
        height: `${profile.background.height}px`,
        top: `${profile.background.top}px`,
        left: `${profile.background.left}px`,
      }"
      alt=""
    />

    <template v-if="glyph.specialFlag">
      <img
        class="glyph special"
        :src="src(glyph.specialTypeCode!)"
        :style="{ height: `${specialHeight(glyph.specialTypeCode!, profile)}px`, top: '2px', left: '2px' }"
        :alt="glyph.specialType"
      />
    </template>

    <template v-else>
      <img
        v-if="glyph.choCode !== undefined"
        class="glyph cho"
        :class="{ hidebox: hidden.cho }"
        :src="src(glyph.choCode)"
        :style="{ height: `${profile.cho.height}px`, top: `${profile.cho.top}px`, zIndex: profile.cho.zIndex }"
        :alt="glyph.chosung"
      />
      <img
        v-if="glyph.jungCode !== undefined"
        class="glyph jung"
        :class="{ hidebox: hidden.jung }"
        :src="src(glyph.jungCode)"
        :style="{ height: `${profile.jung.height}px`, top: `${profile.jung.top}px` }"
        :alt="glyph.jungsung"
      />
      <img
        v-if="glyph.jongCode !== undefined"
        class="glyph jong"
        :class="{ hidebox: hidden.jong }"
        :src="src(glyph.jongCode)"
        :style="{ height: `${profile.jong.height}px`, top: `${profile.jong.top}px` }"
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
