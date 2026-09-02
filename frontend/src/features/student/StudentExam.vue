<script setup lang="ts">
/**
 * 학생 웹 응시 — 참여코드로 입장, 듣고 쓰고 제출.
 *
 * 문제 텍스트는 서버가 내려주지 않는다(받아쓰기). 교사가 읽어주고 학생은 칸 수만 보며 쓴다.
 * 정답은 제출 직후 채점 결과에서만 보인다.
 */
import { computed, onMounted, ref } from 'vue';
import {
  api,
  ApiError,
  loadSession,
  saveSession,
  type AttemptResult,
  type ExamStart,
  type Session,
} from '../../api/client';

const session = ref<Session | null>(loadSession());
const joinCode = ref('');
const displayName = ref('');
const message = ref<{ tone: 'ok' | 'warn'; text: string } | null>(null);

const exam = ref<ExamStart | null>(null);
const answers = ref<Record<string, string>>({});
const results = ref<Record<string, AttemptResult>>({});

const isStudent = computed(() => session.value?.role === 'STUDENT');

function notify(tone: 'ok' | 'warn', text: string) {
  message.value = { tone, text };
}

async function guard<T>(fn: () => Promise<T>): Promise<T | null> {
  try {
    return await fn();
  } catch (e) {
    notify('warn', e instanceof ApiError ? e.message : String(e));
    return null;
  }
}

async function join() {
  const result = await guard(() =>
    api.post<Session>('/api/auth/join', {
      joinCode: joinCode.value.trim().toUpperCase(),
      displayName: displayName.value.trim(),
    }),
  );
  if (!result) return;
  session.value = result;
  saveSession(result);
  notify('ok', `${result.displayName} 학생, 반갑습니다.`);
  await loadExam();
}

async function loadExam() {
  const data = await guard(() => api.get<ExamStart>('/api/exam/current'));
  if (data) {
    exam.value = data;
    message.value = null;
  }
}

async function submit(itemId: string) {
  const answer = (answers.value[itemId] ?? '').trim();
  if (!answer) return;
  const result = await guard(() =>
    api.post<AttemptResult>('/api/exam/attempts', { itemId, rawAnswer: answer, inputKind: 'keyboard' }),
  );
  if (result) {
    results.value[itemId] = result;
  }
}

function leave() {
  session.value = null;
  saveSession(null);
  exam.value = null;
  answers.value = {};
  results.value = {};
}

onMounted(() => {
  if (isStudent.value) loadExam();
});
</script>

<template>
  <div class="exam">
    <p v-if="message" class="msg" :class="message.tone">{{ message.text }}</p>

    <section v-if="!isStudent" class="card join">
      <h2>받아쓰기 참여</h2>
      <p class="hint">선생님이 알려준 참여코드와 이름을 넣으세요.</p>
      <input v-model="joinCode" placeholder="참여코드 (예: K7M2QX)" maxlength="6" class="code" />
      <input v-model="displayName" placeholder="이름 (예: 3번 김OO)" @keyup.enter="join" />
      <button class="primary" @click="join">들어가기</button>
    </section>

    <template v-else>
      <header class="top">
        <span>{{ session?.displayName }}</span>
        <button @click="leave">나가기</button>
      </header>

      <section v-if="!exam" class="card">
        <p>지금 열린 받아쓰기가 없습니다.</p>
        <button @click="loadExam">다시 확인</button>
      </section>

      <template v-else>
        <h2 class="title">{{ exam.title }}</h2>
        <p class="hint">선생님이 불러주는 문장을 듣고 빈칸에 쓰세요.</p>

        <section v-for="item in exam.items" :key="item.itemId" class="card question">
          <h3>{{ item.orderNo + 1 }}번 <span class="dim">({{ item.glyphCount }}칸)</span></h3>

          <div class="row">
            <input
              v-model="answers[item.itemId]"
              type="text"
              placeholder="여기에 쓰세요"
              :disabled="!!results[item.itemId]"
              @keyup.enter="submit(item.itemId)"
            />
            <button class="primary" :disabled="!!results[item.itemId]" @click="submit(item.itemId)">
              {{ results[item.itemId] ? '제출함' : '제출' }}
            </button>
          </div>

          <div v-if="results[item.itemId]" class="result">
            <div class="score">
              <strong :class="{ perfect: results[item.itemId]!.scorePercent === 100 }">
                {{ results[item.itemId]!.scorePercent }}점
              </strong>
              <span class="dim">
                {{ results[item.itemId]!.correctCount }}/{{ results[item.itemId]!.totalCount }}글자
              </span>
            </div>
            <div class="marks">
              <span
                v-for="m in results[item.itemId]!.matches"
                :key="m.index"
                class="mark"
                :class="{ ok: m.correct }"
                :title="m.why ?? ''"
              >
                {{ m.correct ? '○' : '✕' }}
              </span>
            </div>
            <p class="answer">정답: {{ results[item.itemId]!.expectedText }}</p>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.exam {
  max-width: 640px;
  margin: 0 auto;
  padding: 24px 20px 60px;
  font-family: system-ui, -apple-system, sans-serif;
  color: #0f172a;
}

.card {
  margin-bottom: 16px;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.join {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.join h2,
.title {
  margin: 0;
  font-size: 20px;
}

.hint {
  margin: 4px 0 12px;
  color: #64748b;
  font-size: 14px;
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  font-size: 14px;
}

input {
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font-size: 17px;
}

input.code {
  font-size: 22px;
  letter-spacing: 4px;
  text-align: center;
  text-transform: uppercase;
}

button {
  padding: 10px 16px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  font-size: 15px;
  cursor: pointer;
}

button.primary {
  border-color: #296429;
  background: #296429;
  color: #fff;
  font-weight: 600;
}

button:disabled {
  opacity: 0.5;
  cursor: default;
}

.question h3 {
  margin: 0 0 10px;
  font-size: 16px;
}

.row {
  display: flex;
  gap: 8px;
}

.row input {
  flex: 1;
}

.result {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.score strong {
  font-size: 26px;
  color: #d97706;
}

.score strong.perfect {
  color: #1b6e32;
}

.score .dim {
  margin-left: 8px;
}

.dim {
  color: #94a3b8;
  font-size: 14px;
}

.marks {
  display: flex;
  gap: 6px;
  margin: 10px 0;
}

.mark {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 1px solid #fca5a5;
  border-radius: 6px;
  background: #fef2f2;
  color: #b91c1c;
}

.mark.ok {
  border-color: #86efac;
  background: #f0fdf4;
  color: #1b6e32;
}

.answer {
  margin: 0;
  font-size: 16px;
}

.msg {
  margin: 0 0 14px;
  font-size: 14px;
}

.msg.ok {
  color: #1b6e32;
}

.msg.warn {
  color: #b91c1c;
}
</style>
