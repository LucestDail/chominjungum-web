/**
 * 종합 서버 API 클라이언트.
 *
 * 서버는 옵트인이다 — 학습지 편집기는 서버 없이도 완전히 동작하므로,
 * 이 모듈은 교사 콘솔·웹 응시에서만 쓰인다.
 */

const BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8100';
const TOKEN_KEY = 'chominjungum.token';

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
      ...(session ? { Authorization: `Bearer ${session.token}` } : {}),
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
