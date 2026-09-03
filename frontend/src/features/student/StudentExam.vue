<script setup lang="ts">
/**
 * 학생 웹 응시 — 참여코드로 입장, 듣고 쓰고 제출.
 *
 * 문제 텍스트는 서버가 내려주지 않는다(받아쓰기). 교사가 읽어주고 학생은 칸 수만 보며 쓴다.
 * 정답은 제출 직후 채점 결과에서만 보인다.
 *
 * 초등학생이 직접 쓰는 화면이라 ①한 번에 한 문항만 ②글자 크게 ③버튼 크게 를 지킨다.
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
const current = ref(0);
const bigText = ref(false);

const isStudent = computed(() => session.value?.role === 'STUDENT');
const items = computed(() => exam.value?.items ?? []);
const currentItem = computed(() => items.value[current.value] ?? null);
const currentResult = computed(() =>
  currentItem.value ? (results.value[currentItem.value.itemId] ?? null) : null,
);
const doneCount = computed(() => Object.keys(results.value).length);
const allDone = computed(() => items.value.length > 0 && doneCount.value === items.value.length);

/** 브라우저 TTS 지원 여부 — 크롬북·아이패드는 대개 지원한다. */
const canSpeak = typeof window !== 'undefined' && 'speechSynthesis' in window;

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
    current.value = 0;
    message.value = null;
  }
}

async function submit() {
  const item = currentItem.value;
  if (!item) return;
  const answer = (answers.value[item.itemId] ?? '').trim();
  if (!answer) {
    notify('warn', '답을 쓰고 제출하세요.');
    return;
  }
  const result = await guard(() =>
    api.post<AttemptResult>('/api/exam/attempts', {
      itemId: item.itemId,
      rawAnswer: answer,
      inputKind: 'keyboard',
    }),
  );
  if (result) {
    results.value[item.itemId] = result;
    message.value = null;
  }
}

function goto(index: number) {
  if (index < 0 || index >= items.value.length) return;
  current.value = index;
  message.value = null;
}

/**
 * 정답을 모르는 상태에서 읽어줄 수는 없다(서버가 안 내려준다).
 * 대신 채점 후에는 정답을 읽어줘서 소리로 확인하게 한다.
 */
function speakAnswer() {
  const result = currentResult.value;
  if (!canSpeak || !result) return;
  window.speechSynthesis.cancel();
  const utter = new SpeechSynthesisUtterance(result.expectedText);
  utter.lang = 'ko-KR';
  utter.rate = 0.8;
  window.speechSynthesis.speak(utter);
}

function leave() {
  session.value = null;
  saveSession(null);
  exam.value = null;
  answers.value = {};
  results.value = {};
  current.value = 0;
}

onMounted(() => {
  if (isStudent.value) loadExam();
});
</script>

<template>
  <div class="exam" :class="{ big: bigText }">
    <p v-if="message" class="msg" :class="message.tone">{{ message.text }}</p>

    <!-- 입장 -->
    <section v-if="!isStudent" class="card join">
      <h2>받아쓰기 참여</h2>
      <p class="hint">선생님이 알려준 참여코드와 이름을 넣으세요.</p>
      <input v-model="joinCode" placeholder="참여코드 (예: K7M2QX)" maxlength="6" class="code" />
      <input v-model="displayName" placeholder="이름 (예: 3번 김OO)" @keyup.enter="join" />
      <button class="primary big-btn" @click="join">들어가기</button>
    </section>

    <template v-else>
      <header class="top">
        <span class="who">{{ session?.displayName }}</span>
        <button class="ghost" @click="bigText = !bigText">
          {{ bigText ? '보통 글씨' : '큰 글씨' }}
        </button>
        <button class="ghost" @click="leave">나가기</button>
      </header>

      <section v-if="!exam" class="card">
        <p>지금 열린 받아쓰기가 없습니다.</p>
        <button @click="loadExam">다시 확인</button>
      </section>

      <template v-else>
        <h2 class="title">{{ exam.title }}</h2>

        <!-- 진행 표시 -->
        <div class="progress">
          <button
            v-for="(item, i) in items"
            :key="item.itemId"
            class="pip"
            :class="{ active: i === current, done: !!results[item.itemId] }"
            :title="`${i + 1}번`"
            @click="goto(i)"
          >
            {{ i + 1 }}
          </button>
          <span class="progress-text">{{ doneCount }} / {{ items.length }}</span>
        </div>

        <section v-if="currentItem" class="card question">
          <h3>
            {{ current + 1 }}번
            <span class="dim">{{ currentItem.glyphCount }}칸</span>
          </h3>

          <p v-if="!currentResult" class="listen">선생님이 불러주는 문장을 듣고 쓰세요.</p>

          <input
            v-model="answers[currentItem.itemId]"
            type="text"
            class="answer-input"
            placeholder="여기에 쓰세요"
            :disabled="!!currentResult"
            @keyup.enter="submit"
          />

          <div class="row">
            <button class="ghost big-btn" :disabled="current === 0" @click="goto(current - 1)">
              ← 이전
            </button>
            <button
              class="primary big-btn grow"
              :disabled="!!currentResult"
              @click="submit"
            >
              {{ currentResult ? '제출함' : '제출' }}
            </button>
            <button
              class="ghost big-btn"
              :disabled="current === items.length - 1"
              @click="goto(current + 1)"
            >
              다음 →
            </button>
          </div>

          <!-- 채점 결과 -->
          <div v-if="currentResult" class="result">
            <div class="score">
              <strong :class="{ perfect: currentResult.scorePercent === 100 }">
                {{ currentResult.scorePercent }}점
              </strong>
              <span class="dim">
                {{ currentResult.correctCount }}/{{ currentResult.totalCount }}글자
              </span>
            </div>

            <div class="marks">
              <span
                v-for="m in currentResult.matches"
                :key="m.index"
                class="mark"
                :class="{ ok: m.correct }"
                :title="m.why ?? ''"
              >
                {{ m.correct ? '○' : '✕' }}
              </span>
            </div>

            <p class="answer">
              정답: <strong>{{ currentResult.expectedText }}</strong>
              <button v-if="canSpeak" class="ghost speak" @click="speakAnswer">🔊 들어보기</button>
            </p>

            <button
              v-if="current < items.length - 1"
              class="primary big-btn next"
              @click="goto(current + 1)"
            >
              다음 문제 →
            </button>
          </div>
        </section>

        <p v-if="allDone" class="done-all">모두 풀었습니다. 수고했어요! 🎉</p>
      </template>
    </template>
  </div>
</template>

<style scoped>
.exam {
  max-width: 640px;
  margin: 0 auto;
  padding: 24px 20px 60px;
  color: var(--text);
}

/* 큰 글씨 모드 — 초등 저학년·시력 배려 */
.exam.big {
  font-size: 120%;
}

.exam.big .answer-input {
  font-size: 32px;
  padding: 16px;
}

.exam.big .title {
  font-size: 26px;
}

.card {
  margin-bottom: 16px;
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface-sunken);
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
  color: var(--text-muted);
  font-size: 14px;
}

.top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 14px;
}

.top .who {
  margin-right: auto;
  font-weight: 600;
}

input {
  padding: 10px 12px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-size: 17px;
}

input.code {
  font-size: 22px;
  letter-spacing: 4px;
  text-align: center;
  text-transform: uppercase;
}

.answer-input {
  width: 100%;
  margin-bottom: 12px;
  font-size: 24px;
  padding: 14px;
  font-family: var(--font-hand);
}

button {
  padding: 10px 16px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  font-size: 15px;
  cursor: pointer;
}

button.primary {
  border-color: var(--tool-brand);
  background: var(--tool-brand);
  color: #fff;
  font-weight: 600;
}

button.ghost {
  background: var(--surface);
}

button.big-btn {
  padding: 14px 18px;
  font-size: 16px;
}

button.grow {
  flex: 1;
}

button:disabled {
  opacity: 0.5;
  cursor: default;
}

/* 진행 표시 */
.progress {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 12px 0 16px;
}

.pip {
  width: 38px;
  height: 38px;
  padding: 0;
  border-radius: 50%;
  font-size: 15px;
}

.pip.done {
  border-color: var(--success);
  background: var(--success-soft);
  color: var(--success);
  font-weight: 700;
}

.pip.active {
  border-color: var(--tool-brand);
  border-width: 2px;
  font-weight: 700;
}

.progress-text {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 14px;
}

.question h3 {
  margin: 0 0 10px;
  font-size: 18px;
}

.listen {
  margin: 0 0 10px;
  color: var(--text-muted);
  font-size: 15px;
}

.row {
  display: flex;
  gap: 8px;
}

.dim {
  color: var(--text-faint);
  font-size: 14px;
  font-weight: 400;
}

.result {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}

.score strong {
  font-size: 30px;
  color: #d97706;
}

.score strong.perfect {
  color: var(--success);
}

.score .dim {
  margin-left: 8px;
}

.marks {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 12px 0;
}

.mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid #fca5a5;
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
  color: var(--danger);
}

.mark.ok {
  border-color: #86efac;
  background: var(--success-soft);
  color: var(--success);
}

.answer {
  margin: 0 0 12px;
  font-size: 18px;
  font-family: var(--font-hand);
}

.speak {
  margin-left: 10px;
  padding: 5px 10px;
  font-size: 13px;
  font-family: var(--font-ui);
}

.next {
  width: 100%;
}

.done-all {
  padding: 16px;
  border-radius: var(--radius);
  background: var(--success-soft);
  color: var(--success);
  font-size: 17px;
  text-align: center;
  font-weight: 600;
}

.msg {
  margin: 0 0 14px;
  font-size: 15px;
}

.msg.ok {
  color: var(--success);
}

.msg.warn {
  color: var(--danger);
}
</style>
