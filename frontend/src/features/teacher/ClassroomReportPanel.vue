<script setup lang="ts">
/**
 * 성적·리포트 — 학급 요약, 문항별 정답률, 학생별 취약 자모.
 *
 * 취약 자모는 학습지 편집기의 가리기 기능과 같은 자모 코드를 쓰므로,
 * "이 학생이 자주 틀리는 자모만 가린 학습지"로 바로 이어진다.
 */
import { ref, watch } from 'vue';
import {
  api,
  ApiError,
  downloadClassroomCsv,
  type ClassroomReport,
  type StudentReport,
} from '../../api/client';

const props = defineProps<{ classroomId: string }>();

const report = ref<ClassroomReport | null>(null);
const detail = ref<StudentReport | null>(null);
const error = ref<string | null>(null);
const loading = ref(false);

const KIND_LABEL: Record<string, string> = { cho: '초성', jung: '중성', jong: '종성' };

async function load() {
  loading.value = true;
  error.value = null;
  try {
    report.value = await api.get<ClassroomReport>(`/api/reports/classroom/${props.classroomId}`);
    detail.value = null;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}

async function openStudent(studentId: string) {
  try {
    detail.value = await api.get<StudentReport>(`/api/reports/student/${studentId}`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : String(e);
  }
}

async function downloadCsv() {
  try {
    await downloadClassroomCsv(props.classroomId, `${report.value?.classroomName ?? '성적표'}.csv`);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : String(e);
  }
}

watch(() => props.classroomId, load, { immediate: true });
</script>

<template>
  <section class="card">
    <header class="head">
      <h2>성적</h2>
      <button @click="load">새로고침</button>
      <button :disabled="!report" @click="downloadCsv">성적표 내려받기</button>
    </header>

    <p v-if="error" class="warn">{{ error }}</p>
    <p v-else-if="loading" class="dim">불러오는 중…</p>

    <template v-else-if="report">
      <p class="summary">
        학생 {{ report.studentCount }}명 · 제출 {{ report.attemptCount }}건 · 평균
        <strong>{{ report.averagePercent }}점</strong>
        <span v-if="report.unassignedDeviceCount > 0" class="warn">
          · 미배정 제출 {{ report.unassignedDeviceCount }}건
        </span>
      </p>

      <h3>학생별</h3>
      <table v-if="report.students.length > 0">
        <thead>
          <tr>
            <th>학생</th>
            <th>제출</th>
            <th>평균</th>
            <th>만점</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="s in report.students"
            :key="s.studentId"
            class="clickable"
            @click="openStudent(s.studentId)"
          >
            <td>{{ s.displayName }}</td>
            <td>{{ s.attemptCount }}</td>
            <td>{{ s.averagePercent }}점</td>
            <td>{{ s.perfectCount }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="dim">아직 제출이 없습니다.</p>

      <template v-if="report.items.length > 0">
        <h3>문항별 (어려운 순)</h3>
        <table>
          <thead>
            <tr>
              <th>문장</th>
              <th>제출</th>
              <th>평균</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="i in report.items" :key="i.itemId">
              <td>{{ i.expectedText }}</td>
              <td>{{ i.attemptCount }}</td>
              <td>{{ i.averagePercent }}점</td>
            </tr>
          </tbody>
        </table>
      </template>

      <!-- 학생 상세 -->
      <div v-if="detail" class="detail">
        <h3>{{ detail.displayName }} — 평균 {{ detail.averagePercent }}점</h3>

        <template v-if="detail.weakJamos.length > 0">
          <p class="dim">자주 틀리는 자모</p>
          <div class="jamos">
            <span v-for="w in detail.weakJamos" :key="`${w.kind}-${w.code}`" class="jamo">
              {{ w.label }}
              <small>{{ KIND_LABEL[w.kind] }} · {{ w.missCount }}회</small>
            </span>
          </div>
        </template>
        <p v-else class="dim">틀린 자모가 없습니다.</p>

        <table>
          <thead>
            <tr>
              <th>정답</th>
              <th>쓴 답</th>
              <th>점수</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in detail.attempts" :key="a.attemptId">
              <td>{{ a.expectedText }}</td>
              <td :class="{ wrong: a.scorePercent < 100 }">{{ a.rawAnswer }}</td>
              <td>{{ a.scorePercent }}점 ({{ a.correctCount }}/{{ a.totalCount }})</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.card {
  margin-bottom: 20px;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.head h2 {
  margin: 0;
  margin-right: auto;
  font-size: 17px;
}

h3 {
  margin: 18px 0 6px;
  font-size: 14px;
  color: #475569;
}

button {
  padding: 6px 11px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

button:disabled {
  opacity: 0.5;
  cursor: default;
}

.summary {
  margin: 0;
  font-size: 14px;
}

.summary strong {
  font-size: 17px;
  color: #296429;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

th,
td {
  padding: 6px 8px;
  text-align: left;
  border-bottom: 1px solid #eef2f7;
}

th {
  color: #64748b;
  font-weight: 600;
  font-size: 13px;
}

tr.clickable {
  cursor: pointer;
}

tr.clickable:hover {
  background: #eef6ee;
}

td.wrong {
  color: #b91c1c;
}

.detail {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 2px solid #e2e8f0;
}

.jamos {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0 14px;
}

.jamo {
  padding: 6px 10px;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  background: #fef2f2;
  font-size: 18px;
}

.jamo small {
  margin-left: 6px;
  color: #64748b;
  font-size: 12px;
}

.dim {
  color: #94a3b8;
  font-size: 13px;
}

.warn {
  color: #b91c1c;
  font-size: 14px;
}
</style>
