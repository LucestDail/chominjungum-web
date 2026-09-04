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
  /** 학생 화면에서 큰 글씨를 켜고 답을 써서 제출한 상태까지 만든다 */
  play?: string;
}>();

/** 요소가 나타날 때까지 기다린다(데이터가 도착해야 그려지는 것들) */
async function waitFor<T extends Element>(selector: string, tries = 40): Promise<T | null> {
  for (let i = 0; i < tries; i++) {
    const el = document.querySelector<T>(selector);
    if (el) return el;
    await new Promise((r) => setTimeout(r, 100));
  }
  return null;
}

/** v-model 에 값을 넣으려면 Vue 가 듣는 input 이벤트를 함께 보내야 한다 */
function typeInto(el: HTMLInputElement, text: string) {
  el.value = text;
  el.dispatchEvent(new Event('input', { bubbles: true }));
}

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
      const row = await waitFor<HTMLElement>('tr.clickable');
      row?.click();
    }

    if (props.play) {
      // 큰 글씨 → 답 입력 → 제출. 채점 결과가 보이는 상태까지 만든다.
      const input = await waitFor<HTMLInputElement>('.answer-input');
      if (input) {
        const buttons = Array.from(document.querySelectorAll('button'));
        buttons.find((b) => b.textContent?.includes('글씨'))?.click();
        typeInto(input, props.play);
        await new Promise((r) => setTimeout(r, 50));
        buttons.find((b) => b.textContent?.trim() === '제출')?.click();
        await waitFor('.result');
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
