<script setup lang="ts">
/**
 * 교사 콘솔 — 로그인, 학급·명단·문항·과제.
 * 서버 연동은 옵트인이므로, 로그인하지 않으면 학습지 편집기만 쓰면 된다.
 */
import { computed, onMounted, ref } from 'vue';
import {
  api,
  ApiError,
  loadSession,
  saveSession,
  type AssignmentSummary,
  type Classroom,
  type Item,
  type Session,
  type Student,
  type UnassignedDevice,
} from '../../api/client';

import ClassroomReportPanel from './ClassroomReportPanel.vue';

const session = ref<Session | null>(loadSession());
const message = ref<{ tone: 'ok' | 'warn'; text: string } | null>(null);

// 로그인 폼
const mode = ref<'login' | 'register'>('login');
const email = ref('');
const password = ref('');
const displayName = ref('');

// 데이터
const classrooms = ref<Classroom[]>([]);
const selectedClassroomId = ref<string | null>(null);
const roster = ref<Student[]>([]);
const unassigned = ref<UnassignedDevice[]>([]);
const bindTarget = ref<Record<string, string>>({});
const items = ref<Item[]>([]);
const assignments = ref<AssignmentSummary[]>([]);

// 입력
const classroomName = ref('');
const studentName = ref('');
const itemText = ref('');
const itemGrade = ref<string>('');
const itemUnit = ref('');
const itemQuery = ref('');
const bulkRoster = ref('');
const showBulk = ref(false);
const assignmentTitle = ref('');
const selectedItemIds = ref<string[]>([]);

/** 문항 검색 — 문장·단원·학년 어디든 걸리면 보여준다. */
const filteredItems = computed(() => {
  const q = itemQuery.value.trim().toLowerCase();
  if (!q) return items.value;
  return items.value.filter((i) =>
    [i.expectedText, i.unit ?? '', i.grade == null ? '' : `${i.grade}학년`]
      .join(' ')
      .toLowerCase()
      .includes(q),
  );
});

const selectedClassroom = computed(() =>
  classrooms.value.find((c) => c.id === selectedClassroomId.value) ?? null,
);

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

async function submitAuth() {
  const path = mode.value === 'login' ? '/api/auth/teacher/login' : '/api/auth/teacher/register';
  const body =
    mode.value === 'login'
      ? { email: email.value, password: password.value }
      : { email: email.value, password: password.value, displayName: displayName.value };

  const result = await guard(() => api.post<Session>(path, body));
  if (!result) return;
  session.value = result;
  saveSession(result);
  notify('ok', `${result.displayName} 선생님, 반갑습니다.`);
  await loadAll();
}

function logout() {
  session.value = null;
  saveSession(null);
  classrooms.value = [];
  selectedClassroomId.value = null;
}

async function loadAll() {
  const list = await guard(() => api.get<Classroom[]>('/api/classrooms'));
  if (list) {
    classrooms.value = list;
    if (!selectedClassroomId.value && list.length > 0) {
      await selectClassroom(list[0]!.id);
    }
  }
  const itemList = await guard(() => api.get<Item[]>('/api/items'));
  if (itemList) items.value = itemList;
}

async function selectClassroom(id: string) {
  selectedClassroomId.value = id;
  const [r, a, u] = await Promise.all([
    guard(() => api.get<Student[]>(`/api/classrooms/${id}/students`)),
    guard(() => api.get<AssignmentSummary[]>(`/api/classrooms/${id}/assignments`)),
    guard(() => api.get<UnassignedDevice[]>(`/api/classrooms/${id}/unassigned-devices`)),
  ]);
  if (r) roster.value = r;
  if (a) assignments.value = a;
  if (u) unassigned.value = u;
}

/** 교실 LAN 제출의 익명 기기를 학생과 연결한다. 과거 제출도 소급해서 붙는다. */
async function bindDevice(deviceBindingId: string) {
  const studentId = bindTarget.value[deviceBindingId];
  if (!studentId || !selectedClassroomId.value) {
    notify('warn', '연결할 학생을 고르세요.');
    return;
  }
  const res = await guard(() =>
    api.post<{ backfilled: number }>(`/api/classrooms/${selectedClassroomId.value}/devices`, {
      deviceBindingId,
      studentId,
    }),
  );
  if (!res) return;
  notify('ok', `기기를 연결했습니다. 이전 제출 ${res.backfilled}건이 성적에 반영되었습니다.`);
  await selectClassroom(selectedClassroomId.value);
}

async function createClassroom() {
  if (!classroomName.value.trim()) return;
  const c = await guard(() => api.post<Classroom>('/api/classrooms', { name: classroomName.value.trim() }));
  if (!c) return;
  classroomName.value = '';
  notify('ok', `학급을 만들었습니다. 참여코드: ${c.joinCode}`);
  await loadAll();
  await selectClassroom(c.id);
}

async function addStudent() {
  if (!selectedClassroomId.value || !studentName.value.trim()) return;
  const s = await guard(() =>
    api.post<Student>(`/api/classrooms/${selectedClassroomId.value}/students`, {
      displayName: studentName.value.trim(),
    }),
  );
  if (!s) return;
  studentName.value = '';
  await selectClassroom(selectedClassroomId.value);
}

async function addItem() {
  if (!itemText.value.trim()) return;
  const item = await guard(() =>
    api.post<Item>('/api/items', {
      expectedText: itemText.value.trim(),
      grade: itemGrade.value ? Number(itemGrade.value) : null,
      unit: itemUnit.value.trim() || null,
    }),
  );
  if (!item) return;
  itemText.value = '';
  notify('ok', '문항을 등록했습니다.');
  await reloadItems();
}

async function reloadItems() {
  const itemList = await guard(() => api.get<Item[]>('/api/items'));
  if (itemList) items.value = itemList;
}

/**
 * 명단 일괄 입력 — 엑셀에서 복사해 붙여넣는 흐름.
 * 한 줄에 한 명, "번호<탭>이름" 이나 이름만 있어도 받는다.
 */
async function addBulkRoster() {
  if (!selectedClassroomId.value) return;
  const lines = bulkRoster.value
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);
  if (lines.length === 0) {
    notify('warn', '붙여넣은 명단이 비어 있습니다.');
    return;
  }

  let added = 0;
  for (const line of lines) {
    const cells = line.split(/[\t,]/).map((c) => c.trim());
    const maybeNo = Number(cells[0]);
    const hasNo = cells.length > 1 && Number.isFinite(maybeNo);
    const displayName = (hasNo ? cells.slice(1).join(' ') : cells.join(' ')).trim();
    if (!displayName) continue;

    const created = await guard(() =>
      api.post<Student>(`/api/classrooms/${selectedClassroomId.value}/students`, {
        displayName,
        studentNo: hasNo ? maybeNo : null,
      }),
    );
    if (created) added += 1;
  }

  bulkRoster.value = '';
  showBulk.value = false;
  notify('ok', `${added}명을 명단에 추가했습니다.`);
  await selectClassroom(selectedClassroomId.value);
}

async function createAssignment() {
  if (!selectedClassroomId.value || selectedItemIds.value.length === 0) {
    notify('warn', '학급과 문항을 선택하세요.');
    return;
  }
  const a = await guard(() =>
    api.post<AssignmentSummary>('/api/assignments', {
      classroomId: selectedClassroomId.value,
      title: assignmentTitle.value.trim() || '받아쓰기',
      mode: 'ONLINE',
      itemIds: selectedItemIds.value,
    }),
  );
  if (!a) return;
  assignmentTitle.value = '';
  selectedItemIds.value = [];
  notify('ok', '과제를 만들었습니다. "열기"를 눌러야 학생이 응시할 수 있습니다.');
  await selectClassroom(selectedClassroomId.value);
}

async function toggleAssignment(a: AssignmentSummary) {
  const path = a.open ? `/api/assignments/${a.id}/close` : `/api/assignments/${a.id}/open`;
  const updated = await guard(() => api.post<AssignmentSummary>(path));
  if (updated && selectedClassroomId.value) await selectClassroom(selectedClassroomId.value);
}

function toggleItemSelection(id: string) {
  const i = selectedItemIds.value.indexOf(id);
  if (i >= 0) selectedItemIds.value.splice(i, 1);
  else selectedItemIds.value.push(id);
}

onMounted(() => {
  if (session.value?.role === 'TEACHER') loadAll();
});
</script>

<template>
  <div class="console">
    <p v-if="message" class="msg" :class="message.tone">{{ message.text }}</p>

    <!-- 로그인 -->
    <section v-if="session?.role !== 'TEACHER'" class="card auth">
      <h2>교사 로그인</h2>
      <p class="hint">
        학습지 편집기는 로그인 없이 쓸 수 있습니다. 학급·성적 관리를 하려면 로그인하세요.
      </p>
      <div class="tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">로그인</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">가입</button>
      </div>
      <input v-model="email" type="email" placeholder="이메일" />
      <input v-model="password" type="password" placeholder="비밀번호 (8자 이상)" />
      <input v-if="mode === 'register'" v-model="displayName" type="text" placeholder="이름 (예: 김선생)" />
      <button class="primary" @click="submitAuth">{{ mode === 'login' ? '로그인' : '가입하기' }}</button>
    </section>

    <template v-else>
      <header class="top">
        <span>{{ session.displayName }} 선생님</span>
        <button @click="logout">로그아웃</button>
      </header>

      <!-- 학급 -->
      <section class="card">
        <h2>학급</h2>
        <div class="row">
          <input v-model="classroomName" placeholder="예: 1학년 3반" @keyup.enter="createClassroom" />
          <button class="primary" @click="createClassroom">학급 만들기</button>
        </div>
        <ul class="chips">
          <li v-for="c in classrooms" :key="c.id">
            <button :class="{ active: c.id === selectedClassroomId }" @click="selectClassroom(c.id)">
              {{ c.name }}
            </button>
          </li>
        </ul>
        <p v-if="selectedClassroom" class="join-code">
          참여코드 <strong>{{ selectedClassroom.joinCode }}</strong> — 학생이 이 코드로 입장합니다.
        </p>
      </section>

      <template v-if="selectedClassroom">
        <!-- 명단 -->
        <section class="card">
          <h2>학생 명단 ({{ roster.length }})</h2>
          <p class="hint">실명 대신 "3번", "김OO" 같은 교실 표시명을 권장합니다.</p>
          <div class="row">
            <input v-model="studentName" placeholder="예: 3번 김OO" @keyup.enter="addStudent" />
            <button @click="addStudent">추가</button>
            <button @click="showBulk = !showBulk">{{ showBulk ? '닫기' : '여러 명 붙여넣기' }}</button>
          </div>

          <div v-if="showBulk" class="bulk">
            <p class="hint">
              엑셀에서 복사해 붙여넣으세요. 한 줄에 한 명, <code>번호[탭]이름</code> 또는 이름만.
            </p>
            <textarea
              v-model="bulkRoster"
              rows="5"
              placeholder="1&#9;김OO&#10;2&#9;이OO&#10;3&#9;박OO"
            ></textarea>
            <button class="primary" @click="addBulkRoster">명단에 추가</button>
          </div>
          <ul class="list">
            <li v-for="s in roster" :key="s.id">{{ s.displayName }}</li>
            <li v-if="roster.length === 0" class="empty">아직 학생이 없습니다.</li>
          </ul>
        </section>

        <!-- 미배정 기기 -->
        <section v-if="unassigned.length > 0" class="card">
          <h2>명단 연결이 필요한 기기 ({{ unassigned.length }})</h2>
          <p class="hint">
            교실에서 올라온 제출입니다. 어느 학생의 기기인지 연결하면 이전 제출까지 성적에 반영됩니다.
          </p>
          <ul class="list">
            <li v-for="d in unassigned" :key="d.deviceBindingId" class="assignment">
              <code>{{ d.deviceBindingId.slice(0, 8) }}…</code>
              <span class="dim">{{ d.attemptCount }}건</span>
              <select v-model="bindTarget[d.deviceBindingId]">
                <option value="">학생 선택</option>
                <option v-for="s in roster" :key="s.id" :value="s.id">{{ s.displayName }}</option>
              </select>
              <button @click="bindDevice(d.deviceBindingId)">연결</button>
            </li>
          </ul>
        </section>

        <!-- 문항 -->
        <section class="card">
          <h2>문항 ({{ items.length }})</h2>
          <div class="row">
            <input v-model="itemText" placeholder="예: 안녕하세요" @keyup.enter="addItem" />
            <input v-model="itemGrade" class="narrow" type="number" min="1" max="6" placeholder="학년" />
            <input v-model="itemUnit" class="narrow-wide" placeholder="단원 (선택)" />
            <button @click="addItem">문항 등록</button>
          </div>

          <div class="row">
            <input v-model="itemQuery" placeholder="문항 검색 (문장·단원·학년)" />
            <span class="dim search-count">{{ filteredItems.length }}/{{ items.length }}</span>
          </div>

          <ul class="list">
            <li v-for="item in filteredItems" :key="item.id">
              <label>
                <input
                  type="checkbox"
                  :checked="selectedItemIds.includes(item.id)"
                  @change="toggleItemSelection(item.id)"
                />
                {{ item.expectedText }}
                <span class="dim">({{ item.glyphCount }}칸)</span>
                <span v-if="item.grade" class="tag">{{ item.grade }}학년</span>
                <span v-if="item.unit" class="tag">{{ item.unit }}</span>
              </label>
            </li>
            <li v-if="items.length === 0" class="empty">등록된 문항이 없습니다.</li>
            <li v-else-if="filteredItems.length === 0" class="empty">검색 결과가 없습니다.</li>
          </ul>
        </section>

        <!-- 과제 -->
        <section class="card">
          <h2>과제</h2>
          <div class="row">
            <input v-model="assignmentTitle" placeholder="과제 제목 (예: 2학기 1회)" />
            <button class="primary" @click="createAssignment">
              선택한 {{ selectedItemIds.length }}문항으로 과제 만들기
            </button>
          </div>
          <ul class="list">
            <li v-for="a in assignments" :key="a.id" class="assignment">
              <span>{{ a.title }} · {{ a.itemCount }}문항</span>
              <span class="state" :class="{ open: a.open }">{{ a.open ? '열림' : '닫힘' }}</span>
              <button @click="toggleAssignment(a)">{{ a.open ? '닫기' : '열기' }}</button>
            </li>
            <li v-if="assignments.length === 0" class="empty">과제가 없습니다.</li>
          </ul>
        </section>

        <!-- 성적 -->
        <ClassroomReportPanel :classroom-id="selectedClassroom.id" />
      </template>
    </template>
  </div>
</template>

<style scoped>
.console {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 20px 60px;
  font-family: system-ui, -apple-system, sans-serif;
  color: var(--text);
}

.card {
  margin-bottom: 20px;
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface-sunken);
}

.bulk {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
}

.bulk textarea {
  width: 100%;
  margin-bottom: 8px;
  padding: 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: var(--font-ui);
  font-size: 14px;
  resize: vertical;
}

.bulk code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--border);
  font-size: 12px;
}

.narrow {
  flex: 0 0 72px;
}

.narrow-wide {
  flex: 0 0 140px;
}

.search-count {
  align-self: center;
}

.tag {
  margin-left: 6px;
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--tool-brand-soft);
  color: var(--tool-brand-deep);
  font-size: 12px;
}

.card h2 {
  margin: 0 0 6px;
  font-size: 17px;
}

.hint {
  margin: 0 0 12px;
  color: var(--text-muted);
  font-size: 13px;
}

.auth {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 380px;
}

.tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 4px;
}

.tabs button.active {
  border-color: var(--tool-brand);
  color: var(--tool-brand);
  font-weight: 600;
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  font-size: 14px;
}

.row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.row input {
  flex: 1;
}

input {
  padding: 8px 11px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  font-size: 15px;
}

button {
  padding: 8px 13px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: #fff;
  font-size: 14px;
  cursor: pointer;
}

button.primary {
  border-color: var(--tool-brand);
  background: var(--tool-brand);
  color: #fff;
  font-weight: 600;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.chips button.active {
  border-color: var(--tool-brand);
  background: var(--tool-brand-soft);
  font-weight: 600;
}

.join-code {
  margin: 12px 0 0;
  font-size: 14px;
}

.join-code strong {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--sheet-accent);
  font-size: 18px;
  letter-spacing: 2px;
}

.list {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 14px;
}

.list li {
  padding: 5px 0;
  border-bottom: 1px solid var(--border);
}

.list li.empty {
  color: var(--text-faint);
  border: none;
}

.dim {
  color: var(--text-faint);
}

.assignment {
  display: flex;
  align-items: center;
  gap: 10px;
}

.assignment .state {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: 999px;
  background: #e2e8f0;
  font-size: 12px;
}

.assignment .state.open {
  background: var(--success-soft);
  color: var(--success);
  font-weight: 600;
}

.msg {
  margin: 0 0 14px;
  font-size: 14px;
}

.msg.ok {
  color: var(--success);
}

.msg.warn {
  color: var(--danger);
}
</style>
