import { ref } from 'vue';
import type { HideRule } from '@chominjungum/hangul-core';

/**
 * 리포트 → 학습지 인계.
 *
 * 취약 자모 분석 결과와 학습지 가리기가 **같은 자모 코드 체계**를 쓰기 때문에,
 * "이 학생이 자주 틀리는 자모만 가린 학습지"를 바로 만들 수 있다.
 * 리포트에서 규칙을 담아 두면 학습지 화면이 그것을 받아 적용한다.
 */

export interface WorksheetHandoff {
  rule: HideRule;
  /** 화면에 표시할 설명 — 어디서 왔는지 알려준다 */
  note: string;
}

const pending = ref<WorksheetHandoff | null>(null);
/** 학습지 탭으로 전환해 달라는 요청 (App 이 감시) */
const tabRequest = ref(0);

export function requestWorksheet(handoff: WorksheetHandoff): void {
  pending.value = handoff;
  tabRequest.value += 1;
}

/** 학습지 화면이 한 번 꺼내 쓰고 비운다. */
export function takeHandoff(): WorksheetHandoff | null {
  const value = pending.value;
  pending.value = null;
  return value;
}

export function useWorksheetHandoff() {
  return { pending, tabRequest };
}
