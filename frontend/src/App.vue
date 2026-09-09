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
    <!-- 로고는 jammin 원본을 바이트 그대로 쓴다(임의 재현 금지).
         출처: jammin/src/main/resources/static/img/navbar-logo.png (sha256 3a1deff1…) -->
    <img class="brand-logo" src="/navbar-logo.png" alt="초민정음" />
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
  /* jammin 원본 네비와 같은 초록 바 — 로고가 흰색이라 밝은 배경에서는 보이지 않는다.
     본문(콘솔·성적표)은 종전대로 단색 실무 톤을 유지한다. 이원화는 "학습지 vs 콘솔
     본문"이지 상단 브랜드바가 아니다. 앱도 초록 앱바 + 같은 로고라 셋이 일치한다. */
  background: #296429;
  font-family: system-ui, -apple-system, sans-serif;
}

.brand-logo {
  height: 26px;
  margin-right: 10px;
  display: block;
}

.nav button {
  padding: 6px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: none;
  color: rgba(255, 255, 255, 0.86);
  font-size: 14px;
  cursor: pointer;
}

.nav button:hover {
  color: #fff;
}

.nav button.active {
  border-color: rgba(255, 255, 255, 0.55);
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-weight: 600;
}

/* 인쇄할 때는 학습지만 남긴다 */
@media print {
  .nav {
    display: none;
  }
}
</style>
