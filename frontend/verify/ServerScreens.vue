<script setup lang="ts">
/**
 * 서버가 필요한 화면(교사 콘솔·학생 응시)을 로그인한 상태로 렌더한다.
 *
 * 이 두 화면은 데이터가 있어야 의미가 있는데, 헤드리스 스크린샷으로는 로그인 조작을
 * 할 수 없다. 그래서 URL 로 받은 계정으로 대신 로그인한 뒤 실제 컴포넌트를 띄운다.
 *
 *   /verify.html?only=H&email=...&pw=...        교사 콘솔
 *   /verify.html?only=H&join=7SA2SC&name=김하늘   학생 응시
 *
 * **개발 전용**이다. 제품 빌드에 들어가지 않으며(`verify.html` 은 빌드 엔트리가 아니다),
 * 자격 증명을 URL 로 받는 방식도 로컬 검증에서만 쓴다.
 */
import { onMounted, ref } from 'vue';

import { api, saveSession, type Session } from '../src/api/client';
import TeacherConsole from '../src/features/teacher/TeacherConsole.vue';
import StudentExam from '../src/features/student/StudentExam.vue';

const props = defineProps<{
  email?: string;
  pw?: string;
  join?: string;
  name?: string;
  /** 학생 상세(점수 추이·취약 자모)는 행을 눌러야 열린다 — 스크린샷용으로 대신 눌러준다 */
  openStudent?: boolean;
}>();

const status = ref('로그인 중…');
const ready = ref<'teacher' | 'student' | null>(null);

onMounted(async () => {
  try {
    if (props.email && props.pw) {
      const s = await api.post<Session>('/api/auth/teacher/login', {
        email: props.email,
        password: props.pw,
      });
      saveSession(s);
      ready.value = 'teacher';
    } else if (props.join) {
      const s = await api.post<Session>('/api/auth/join', {
        joinCode: props.join,
        displayName: props.name ?? '검증학생',
      });
      saveSession(s);
      ready.value = 'student';
    } else {
      status.value = '계정 정보가 없습니다 (?email=&pw= 또는 ?join=)';
      return;
    }
    status.value = '';

    if (props.openStudent) {
      // 데이터가 도착한 뒤에야 행이 생긴다 — 나타날 때까지 잠깐 기다린다
      for (let i = 0; i < 40; i++) {
        const row = document.querySelector<HTMLElement>('tr.clickable');
        if (row) {
          row.click();
          break;
        }
        await new Promise((r) => setTimeout(r, 100));
      }
    }
  } catch (e) {
    status.value = `로그인 실패: ${(e as Error).message}`;
  }
});
</script>

<template>
  <p v-if="status" class="status">{{ status }}</p>
  <TeacherConsole v-if="ready === 'teacher'" />
  <StudentExam v-else-if="ready === 'student'" />
</template>

<style scoped>
.status {
  padding: 10px 0;
  font-size: 13px;
  color: #b45309;
}
</style>
