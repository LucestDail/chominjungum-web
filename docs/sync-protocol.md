# 업싱크 프로토콜 (교사앱 → 종합 서버)

> 교실 LAN 프로토콜(학생앱 ↔ 교사앱, AES-GCM `SyncEnvelope`)은 [chominjungum](../../chominjungum) 저장소의 `packages/sync_protocol`이 정본이다.
> 이 문서는 **그 결과를 서버로 올리는 구간**만 다룬다.

---

## 1. 설계 전제

학교 네트워크는 신뢰할 수 없다. 그래서:

1. **수업은 서버 없이 완결된다.** 업싱크 실패가 수업을 막지 않는다.
2. **재전송은 항상 안전하다.** `attemptId`가 멱등 키다.
3. **부분 성공을 허용한다.** 일부 attempt가 거부돼도 나머지는 저장된다.

## 2. 상태 머신 (교사앱 로컬 큐)

```
   수업 중                수업 후
 ┌─────────┐  세션 종료  ┌─────────┐  업로드   ┌──────────┐
 │ COLLECT │───────────►│ PENDING │─────────►│ UPLOADING│
 └─────────┘            └─────────┘          └────┬─────┘
                             ▲                    │
                             │ 실패(재시도 대기)    │ 2xx
                             └────────────────────┤
                                                  ▼
                                            ┌──────────┐
                                            │  SYNCED  │
                                            └──────────┘
```

- `PENDING` 큐는 기기 로컬에 영속 저장한다(앱 재시작·배터리 방전 대비).
- 재시도는 지수 백오프. 사용자가 수동으로 "지금 업로드"도 할 수 있다.
- `SYNCED` 이후에도 로컬 사본을 즉시 지우지 않는다(교사가 확인할 때까지).

## 3. 엔드포인트

### 3.1 세션 업로드

```http
POST /api/sync/sessions
Authorization: Bearer <교사 기기 토큰>
Content-Type: application/json
```

```jsonc
{
  "sessionId": "0f9c…",          // 교사앱의 허브 세션 ID 재사용
  "classroomId": "3a21…",
  "source": "LAN",                // LAN | WEB
  "startedAtMs": 1756800000000,
  "endedAtMs":   1756802400000,
  "items": [
    {
      "id": "e3b0c44298fc",       // contentHash 앞 12자 (앱과 동일 규칙)
      "expectedText": "안녕하세요",
      "glyphsJson": "[{…}]",      // jammin addWord 응답 형식 그대로
      "contentHash": "e3b0c44298fc1c14…"
    }
  ],
  "attempts": [
    {
      "attemptId": "b1d2…",       // UUID v4, 멱등 키
      "itemId": "e3b0c44298fc",
      "deviceBindingId": "d41d…", // 익명 기기 ID
      "rawAnswer": "안녕하세오",
      "correctCount": 4,
      "totalCount": 5,
      "inputKind": "keyboard",     // keyboard | ocrCanvas | ocrImage
      "submittedAtMs": 1756801200000,
      "matches": [                 // v2 추가 (optional)
        { "i": 0, "ok": true },
        { "i": 4, "ok": false, "why": "글자 불일치: \"요\" vs \"오\"" }
      ]
    }
  ]
}
```

### 3.2 응답

```jsonc
// 200 OK — 부분 성공 포함
{
  "sessionId": "0f9c…",
  "accepted": 27,
  "duplicated": 3,                // 이미 저장돼 있던 attemptId (정상)
  "rejected": [
    { "attemptId": "9a…", "reason": "UNKNOWN_ITEM" }
  ],
  "unassignedDevices": ["d41d…"]  // 명단 매핑이 없는 기기 → 교사 콘솔에서 연결 필요
}
```

| 코드 | 의미 | 앱 동작 |
|---|---|---|
| 200 | 수신(부분 성공 포함) | `SYNCED`로 전이, `rejected`는 사용자에게 표시 |
| 401 | 토큰 만료·무효 | 재로그인 유도, 큐 유지 |
| 409 | `classroomId` 불일치 등 | 큐 유지, 교사 확인 필요 |
| 5xx / 네트워크 | 서버 문제 | 백오프 재시도 |

## 4. 멱등성과 충돌

- `attempt.attemptId`가 PK. 재전송 시 **INSERT … ON CONFLICT DO NOTHING** → `duplicated`로 집계.
- 같은 학생이 같은 문항을 다시 풀어 새 `attemptId`로 올라오면, `UNIQUE(session_id, device_binding_id, item_id)`에 걸린다. 이때 **`submittedAtMs`가 더 최신인 쪽으로 UPSERT**한다.
  - 이는 교실 모드 UI 동작(같은 기기·같은 문항 재제출 → 목록에서 최신으로 대체)과 일치한다.
- 서버는 `correctCount/totalCount`를 **그대로 믿지 않고 재채점**한다(`expectedText` vs `rawAnswer`). 불일치 시 서버 값을 저장하고 감사 로그를 남긴다.

## 5. 신원 매핑

교실 LAN에서 학생은 익명 `deviceBindingId`다. 서버에는 학생 명단(`student`)이 있다.

```
업로드 → attempt.student_id = NULL, device_binding_id 보존
      → 응답의 unassignedDevices를 교사 콘솔이 표시
      → 교사가 "이 기기 = 3번 김OO" 연결 (student_device)
      → 서버가 해당 기기의 과거·미래 attempt를 소급 매핑
```

매핑 전 제출도 **버리지 않는다**. 미배정 상태로 보관하다가 연결 시점에 성적에 반영된다.

## 6. 보안

| 구간 | 보호 |
|---|---|
| 학생앱 ↔ 교사앱 (LAN) | AES-GCM, 세션 키는 QR 페어링으로 교환 (현행) |
| 교사앱 → 서버 | HTTPS + 교사 계정으로 발급한 기기 토큰 |
| 학생 웹 → 서버 | HTTPS + 참여코드 단기 토큰(계정 없음) |

- 기기 토큰은 교사 기기 보안 저장소(Flutter Secure Storage)에 둔다.
- 서버는 업로드된 `classroomId`가 **해당 교사 소유인지** 반드시 검증한다.
- 손글씨 이미지·음성은 업싱크 대상이 아니다(기기 로컬 전용).

## 7. 미해결

- 다중 교사가 한 학급을 공유하는 경우의 세션 소유권
- 학년 종료 시 데이터 보존·파기 정책(교육기관 요구사항 확인 필요)
