<script setup lang="ts">
/**
 * 점수 추이 — 단일 시리즈 라인차트.
 *
 * 설계 근거:
 *  - 시간(회차) 축의 변화라 라인이 맞다.
 *  - 시리즈가 하나라 범례를 두지 않는다(제목이 곧 시리즈 이름).
 *  - 점수는 척도가 0~100 으로 고정이므로 축을 데이터에 맞춰 늘리지 않는다(0 기준 유지).
 *  - 모든 점에 숫자를 붙이지 않고 처음·마지막·최저점만 직접 라벨한다.
 *  - 표 형태는 아래 제출 이력 표가 담당한다(색만으로 정보를 전달하지 않음).
 *  - 색 #296429 는 팔레트 검증기 통과(대비 3:1↑).
 */
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    /** 회차 순서대로 0~100 점수 */
    points: { label: string; value: number }[];
    height?: number;
  }>(),
  { height: 132 },
);

const W = 320; // viewBox 기준 폭 — 실제 크기는 CSS 가 늘린다
const PAD = { top: 12, right: 14, bottom: 22, left: 30 };

const plot = computed(() => ({
  w: W - PAD.left - PAD.right,
  h: props.height - PAD.top - PAD.bottom,
}));

/** x: 균등 배치, y: 0~100 고정 척도 */
const coords = computed(() => {
  const n = props.points.length;
  return props.points.map((p, i) => {
    const x = n === 1 ? PAD.left + plot.value.w / 2 : PAD.left + (plot.value.w * i) / (n - 1);
    const y = PAD.top + plot.value.h * (1 - Math.min(100, Math.max(0, p.value)) / 100);
    return { ...p, x, y, index: i };
  });
});

const linePath = computed(() =>
  coords.value.map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' '),
);

const gridLines = [0, 50, 100];
function gridY(score: number): number {
  return PAD.top + plot.value.h * (1 - score / 100);
}

/** 직접 라벨은 선택적으로 — 처음·마지막·최저점만 */
const labeled = computed(() => {
  const cs = coords.value;
  if (cs.length === 0) return new Set<number>();
  const lowest = cs.reduce((min, c) => (c.value < min.value ? c : min), cs[0]!);
  return new Set([0, cs.length - 1, lowest.index]);
});

const average = computed(() =>
  props.points.length === 0
    ? 0
    : Math.round(props.points.reduce((s, p) => s + p.value, 0) / props.points.length),
);
</script>

<template>
  <figure v-if="points.length > 0" class="chart">
    <figcaption>
      점수 추이 <span class="dim">{{ points.length }}회 · 평균 {{ average }}점</span>
    </figcaption>

    <svg :viewBox="`0 0 ${W} ${height}`" role="img" :aria-label="`점수 추이, ${points.length}회, 평균 ${average}점`">
      <!-- 격자·축은 눌러서 -->
      <g class="grid">
        <template v-for="g in gridLines" :key="g">
          <line :x1="PAD.left" :x2="W - PAD.right" :y1="gridY(g)" :y2="gridY(g)" />
          <text :x="PAD.left - 6" :y="gridY(g) + 3.5">{{ g }}</text>
        </template>
      </g>

      <path class="line" :d="linePath" />

      <g class="dots">
        <circle v-for="c in coords" :key="c.index" :cx="c.x" :cy="c.y" r="4">
          <title>{{ c.label }} · {{ c.value }}점</title>
        </circle>
      </g>

      <g class="point-labels">
        <text
          v-for="c in coords.filter((c) => labeled.has(c.index))"
          :key="`l-${c.index}`"
          :x="c.x"
          :y="c.y - 9"
        >
          {{ c.value }}
        </text>
      </g>
    </svg>
  </figure>
</template>

<style scoped>
.chart {
  margin: 0 0 16px;
}

figcaption {
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-muted);
}

.dim {
  font-weight: 400;
  color: var(--text-faint);
}

svg {
  width: 100%;
  height: auto;
  display: block;
  overflow: visible;
}

.grid line {
  stroke: var(--border);
  stroke-width: 1;
}

.grid text {
  fill: var(--text-faint);
  font-size: 9px;
  text-anchor: end;
}

.line {
  fill: none;
  stroke: var(--tool-brand);
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.dots circle {
  fill: var(--tool-brand);
  /* 겹칠 때 서로 구분되도록 표면색 링 */
  stroke: var(--surface-sunken);
  stroke-width: 2;
}

.point-labels text {
  fill: var(--text-muted);
  font-size: 10px;
  font-weight: 600;
  text-anchor: middle;
}
</style>
