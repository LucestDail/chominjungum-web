<script setup lang="ts">
/**
 * 자모 가리기 선택 — jammin `test.html` 의 체크박스 묶음을 컴포넌트로 옮긴 것.
 * 모드 4종 + 기본/확장 두 줄 구성, 전체 토글까지 원본 구성을 따른다.
 */
import { computed } from 'vue';
import { JAMO_GROUPS, HIDE_MODE_LABELS, type HideMode } from '@chominjungum/hangul-core';

const props = defineProps<{
  mode: HideMode;
  isChecked: (value: string) => boolean;
}>();

const emit = defineEmits<{
  (e: 'change-mode', mode: HideMode): void;
  (e: 'toggle', value: string, on: boolean): void;
  (e: 'toggle-all', on: boolean): void;
}>();

const group = computed(() => JAMO_GROUPS[props.mode]);
const allChecked = computed(() =>
  [...group.value.primary, ...group.value.extra].every((o) => props.isChecked(o.value)),
);

const MODES: HideMode[] = ['ja', 'cho', 'jung', 'jong'];
</script>

<template>
  <section class="jamo-picker">
    <div class="mode-row">
      <label class="mode-label">가릴 자모</label>
      <select
        :value="mode"
        class="mode-select"
        @change="emit('change-mode', ($event.target as HTMLSelectElement).value as HideMode)"
      >
        <option v-for="m in MODES" :key="m" :value="m">{{ HIDE_MODE_LABELS[m] }}</option>
      </select>

      <label class="chip all">
        <input
          type="checkbox"
          :checked="allChecked"
          @change="emit('toggle-all', ($event.target as HTMLInputElement).checked)"
        />
        <span>전체</span>
      </label>
    </div>

    <div class="chips">
      <label v-for="opt in group.primary" :key="opt.value" class="chip">
        <input
          type="checkbox"
          :checked="isChecked(opt.value)"
          @change="emit('toggle', opt.value, ($event.target as HTMLInputElement).checked)"
        />
        <span>{{ opt.label }}</span>
      </label>
    </div>

    <div class="chips extra">
      <label v-for="opt in group.extra" :key="`x-${opt.value}`" class="chip">
        <input
          type="checkbox"
          :checked="isChecked(opt.value)"
          @change="emit('toggle', opt.value, ($event.target as HTMLInputElement).checked)"
        />
        <span>{{ opt.label }}</span>
      </label>
    </div>
  </section>
</template>

<style scoped>
.jamo-picker {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mode-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mode-label {
  font-size: 14px;
  font-weight: 600;
}

.mode-select {
  padding: 6px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font-size: 14px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chips.extra {
  padding-top: 2px;
  border-top: 1px dashed #e2e8f0;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 9px;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  background: #fff;
  font-size: 14px;
  cursor: pointer;
  user-select: none;
}

.chip:has(input:checked) {
  border-color: #296429;
  background: #eaf4ea;
  color: #1e4a1e;
  font-weight: 600;
}

.chip.all {
  margin-left: auto;
}

.chip input {
  accent-color: #296429;
}
</style>
