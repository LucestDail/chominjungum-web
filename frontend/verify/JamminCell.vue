<script setup lang="ts">
/**
 * jammin 원본이 만드는 DOM 을 **그대로** 렌더한다 (대조 기준).
 *
 * `jammin-ref/` 는 원본 파일의 바이트 복사본이므로, 여기 나오는 화면은
 * jammin 서버에서 보던 것과 같다. 새 구현(`HangulCell`)과 겹쳐 보기 위한 것이다.
 */
import { computed } from 'vue';
import type { HangulGlyph } from '@chominjungum/hangul-core';

import { buildHangulWrapperHtml } from './jammin-ref/glyph-renderer.js';

const props = withDefaults(
  defineProps<{
    glyph: HangulGlyph;
    profileKey?: 'editor' | 'compact';
    /** 원고지 배경을 함께 그릴지 (자모만 대조할 때는 끈다) */
    showGuide?: boolean;
  }>(),
  { profileKey: 'editor', showGuide: true },
);

/** 원본 wrapper 에서 칸 하나(.hangulSet)만 꺼낸다. */
const html = computed(() => {
  const wrapper = buildHangulWrapperHtml({
    jsonArray: [props.glyph],
    fullWord: props.glyph.word ?? '',
    ordering: 0,
    profileKey: props.profileKey,
    toolbar: 'none',
    rowWrapperStyle: 'width:100%;',
  });
  const doc = new DOMParser().parseFromString(wrapper, 'text/html');
  const cell = doc.querySelector('.hangulSet');
  if (!cell) return '';
  if (!props.showGuide) cell.querySelector('.hangul-background')?.remove();
  // 클릭 영역용 빈 div 는 대조에 방해가 되므로 뺀다(그림에 영향 없음)
  cell.querySelector('.hangulFullWord')?.remove();
  return cell.outerHTML;
});
</script>

<template>
  <div class="jammin-cell" v-html="html" />
</template>

<style>
/* 원본 페이지의 규칙 중 그림에 영향을 주는 것만 재현한다. */
.jammin-cell .hidebox {
  opacity: 0;
}

/**
 * 원본에는 없는 우리 전역 규칙(`img { max-width: 100% }`)을 걷어낸다.
 * 원본 특수문자는 `position:absolute` 인 부모(`.special`) 안에 있어 부모 폭이 0 이고,
 * 거기에 max-width:100% 가 걸리면 **폭 0 으로 사라진다**. 대조가 왜곡되므로 해제한다.
 */
.jammin-cell img {
  max-width: none;
}

/* 진단: 원본 조각이 실제로 어디에 놓였는지 외곽선으로 본다 */
.diagnose .jammin-cell .special img {
  outline: 1.5px solid magenta;
}

.diagnose .jammin-cell .cho img {
  outline: 1px solid #e11d48;
}

.diagnose .jammin-cell .jung img {
  outline: 1px solid #0ea5e9;
}

.diagnose .jammin-cell .jong img {
  outline: 1px solid #16a34a;
}

.diagnose .jammin-cell .hangul-background img {
  outline: 1px dotted #a855f7;
}
</style>
