<script setup lang="ts">
import { ref, watch } from 'vue';

import WorksheetEditor from './features/worksheet/WorksheetEditor.vue';
import { useWorksheetHandoff } from './features/worksheet/handoff';
import TeacherConsole from './features/teacher/TeacherConsole.vue';
import StudentExam from './features/student/StudentExam.vue';

type Tab = 'worksheet' | 'teacher' | 'student';

const TABS: { key: Tab; label: string; hint: string }[] = [
  { key: 'worksheet', label: '학습지', hint: '서버 없이 동작' },
  { key: 'teacher', label: '교사 콘솔', hint: '학급·과제·성적' },
  { key: 'student', label: '받아쓰기 참여', hint: '참여코드로 입장' },
];

const tab = ref<Tab>('worksheet');

// 리포트에서 "이 자모만 가린 학습지 만들기" 를 누르면 학습지 탭으로 넘어온다
const { tabRequest } = useWorksheetHandoff();
watch(tabRequest, () => {
  tab.value = 'worksheet';
});
</script>

<template>
  <nav class="nav">
    <span class="brand">초민정음</span>
    <button
      v-for="t in TABS"
      :key="t.key"
      :class="{ active: tab === t.key }"
      :title="t.hint"
      @click="tab = t.key"
    >
      {{ t.label }}
    </button>
  </nav>

  <WorksheetEditor v-if="tab === 'worksheet'" />
  <TeacherConsole v-else-if="tab === 'teacher'" />
  <StudentExam v-else />
</template>

<style scoped>
.nav {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
  font-family: system-ui, -apple-system, sans-serif;
}

.brand {
  margin-right: 8px;
  font-weight: 700;
  color: #296429;
}

.nav button {
  padding: 6px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: none;
  font-size: 14px;
  cursor: pointer;
}

.nav button.active {
  border-color: #296429;
  background: #eaf4ea;
  color: #1e4a1e;
  font-weight: 600;
}

/* 인쇄할 때는 학습지만 남긴다 */
@media print {
  .nav {
    display: none;
  }
}
</style>
