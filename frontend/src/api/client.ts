/**
 * 종합 서버 API 클라이언트.
 *
 * 서버는 옵트인이다 — 학습지 편집기는 서버 없이도 완전히 동작하므로,
 * 이 모듈은 교사 콘솔·웹 응시에서만 쓰인다.
 */

const BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8100';
const TOKEN_KEY = 'chominjungum.token';

/**
 * 앱 토큰 전용 헤더.
 *
 * 게이트웨이(nginx)가 외부 요청에 HTTP Basic 을 요구하면 브라우저가 `Authorization` 을
 * Basic 으로 채운다. 앱 토큰까지 같은 헤더에 실으면 하나가 덮여 인증이 깨지므로 분리한다.
 */
const TOKEN_HEADER = 'X-Auth-Token';

export interface Session {
  token: string;
  role: 'TEACHER' | 'STUDENT';
  id: string;
  displayName: string;
}

export function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(TOKEN_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function saveSession(session: Session | null): void {
  if (session) localStorage.setItem(TOKEN_KEY, JSON.stringify(session));
  else localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const session = loadSession();
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(session ? { [TOKEN_HEADER]: session.token } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });

  if (!res.ok) {
    let message = `요청에 실패했습니다 (${res.status})`;
    try {
      const data = (await res.json()) as { message?: string };
      if (data.message) message = data.message;
    } catch {
      // 본문이 없을 수 있다
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
};

// ── 타입 (서버 DTO 와 1:1) ──────────────────────────────────
export interface Classroom {
  id: string;
  name: string;
  grade: number | null;
  schoolYear: number | null;
  joinCode: string;
}

export interface Student {
  id: string;
  displayName: string;
  studentNo: number | null;
}

export interface UnassignedDevice {
  deviceBindingId: string;
  attemptCount: number;
}

export interface Item {
  id: string;
  expectedText: string;
  glyphCount: number;
  grade: number | null;
  unit: string | null;
}

export interface AssignmentSummary {
  id: string;
  classroomId: string;
  title: string;
  mode: string;
  open: boolean;
  itemCount: number;
}

export interface ExamItem {
  itemId: string;
  orderNo: number;
  glyphCount: number;
}

export interface ExamStart {
  assignmentId: string;
  title: string;
  items: ExamItem[];
}

export interface StudentRow {
  studentId: string;
  displayName: string;
  attemptCount: number;
  averagePercent: number;
  perfectCount: number;
}

export interface ItemRow {
  itemId: string;
  expectedText: string;
  attemptCount: number;
  averagePercent: number;
}

export interface ClassroomReport {
  classroomId: string;
  classroomName: string;
  studentCount: number;
  attemptCount: number;
  averagePercent: number;
  students: StudentRow[];
  items: ItemRow[];
  unassignedDeviceCount: number;
}

export interface WeakJamo {
  kind: 'cho' | 'jung' | 'jong';
  code: number;
  label: string;
  missCount: number;
}

export interface AttemptRow {
  attemptId: string;
  expectedText: string;
  rawAnswer: string;
  correctCount: number;
  totalCount: number;
  scorePercent: number;
  submittedAtMs: number;
}

export interface StudentReport {
  studentId: string;
  displayName: string;
  attemptCount: number;
  averagePercent: number;
  attempts: AttemptRow[];
  weakJamos: WeakJamo[];
}

/** 성적표 CSV — 토큰이 필요하므로 fetch 로 받아 파일로 저장한다. */
export async function downloadClassroomCsv(classroomId: string, filename: string): Promise<void> {
  const session = loadSession();
  const res = await fetch(`${BASE}/api/reports/classroom/${classroomId}/csv`, {
    headers: session ? { [TOKEN_HEADER]: session.token } : {},
  });
  if (!res.ok) throw new ApiError(res.status, '성적표를 내려받지 못했습니다.');

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export interface GlyphMatch {
  index: number;
  correct: boolean;
  why: string | null;
}

export interface AttemptResult {
  attemptId: string;
  itemId: string;
  expectedText: string;
  rawAnswer: string;
  correctCount: number;
  totalCount: number;
  scorePercent: number;
  matches: GlyphMatch[];
}
