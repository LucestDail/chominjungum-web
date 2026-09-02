# 초민정음 Web (chominjungum-web) — 전체 설계

> 작성: 2026-09-02. 대상 독자: 이 저장소를 이어받는 다음 세션·개발자.
> **원칙: [jammin](../jammin) 저장소는 절대 수정하지 않는다.** 읽기·복사·대조만 한다.

---

## 0. 한 줄 정의

**초민정음 생태계의 웹 레이어 + 종합 서버.**
교실에서는 서버 없이 굴러가고(기존 Flutter 앱의 LAN 직결 유지), 온라인일 때 성적·학급·과제를 한곳에 모으는 관리 축을 추가한다.

---

## 1. 왜 새 저장소인가

| | jammin (기존 웹) | chominjungum (Flutter 앱) | **chominjungum-web (신규)** |
|---|---|---|---|
| 역할 | 받아쓰기 학습지 **생성·인쇄** | 교실 **직결 출제·응시·채점** | **웹 응시 + 종합 서버(학급·성적·ERP)** |
| 스택 | Spring Boot 3.0 + Thymeleaf + jQuery | Flutter 3.41 + Riverpod | Spring Boot 3.5 + Vue 3 + PostgreSQL |
| 상태 | 안정 운영 중, **수정 금지** | Phase 1 진행 중 | 신규 |
| DB | 없음 | 없음(메모리) | **PostgreSQL** |

jammin을 고쳐 확장하지 않는 이유는 세 가지다. ①이미 동작하는 학습지 도구를 깨뜨릴 위험, ②Thymeleaf+jQuery 전역 DOM 구조가 학급·성적 같은 상태 관리로 확장되지 않음, ③사용자 지시가 "jammin 절대 수정 금지".

대신 **jammin의 핵심 받아쓰기 로직은 그대로 준용**한다(§2).

---

## 2. jammin에서 준용하는 핵심 (변경 금지 계약)

이 절의 규칙은 **세 구현체(Java 서버 / Dart 앱 / TS 웹)가 반드시 동일하게** 지켜야 한다.

### 2.1 자모 분해 스키마

`HangulUtil.hangulSplit(String)` → `List<Hangul>`, 각 항목 `Hangul.toJSON()`:

```json
{ "word":"안", "chosung":"ᄋ", "choCode":4363, "jungsung":"ᅡ", "jungCode":4449,
  "jongsung":"ᆫ", "jongCode":4540, "emptyJongsung":false, "errorFlag":false, "specialFlag":false }
```

특수문자·숫자는 다른 형태를 취한다:

```json
{ "specialFlag":true, "specialType":" ", "specialTypeCode":32, "emptyJongsung":true, "errorFlag":false }
```

계산식 (`HangulUtil.java:37-54`):

```
comVal = ch - 0xAC00
cho  = ((comVal - comVal%28) / 28) / 21 + 0x1100   // 4352~
jung = ((comVal - comVal%28) / 28) % 21 + 0x1161   // 4449~
jong =  (comVal % 28) + 0x11A7                     // 4519(= 종성없음) ~
```

- `jong == 4519`(0x11A7)이면 종성 없음 → `jongsung` 필드 자체를 생략하고 `emptyJongsung=true`.
- 초/중성도 `!= 4519` 검사를 거친다(원본 코드 그대로 준용).
- **이 코드값이 곧 SVG 파일명**이다: `hangul/{choCode}.svg`, `hangul/{jungCode}.svg`, `hangul/{jongCode}.svg`, 특수문자는 `hangul/{specialTypeCode}.svg`.

### 2.2 허용 문자 집합

`isHangul()` = 모든 문자가 다음 중 하나:
- `Character.UnicodeBlock.HANGUL_SYLLABLES` (완성형 음절 가-힣)
- 특수문자 5개: `공백` `.` `,` `?` `!`
- 숫자 `0-9`

하나라도 벗어나면 **분해 결과는 빈 배열**이고, jammin UI는 "한글이 아니거나, 완성된 한글 단어가 아닌 문자가 포함되어있습니다"로 거부한다. 자모 단독(ㄱ, ㅏ), 한자, 영문은 **출제 불가**다.

### 2.3 출제 제약

| 제약 | 값 | 출처 |
|---|---|---|
| 한 문장 최대 | **16글자** | `worksheet-app.js:157` |
| 학습지 전체 | **20줄** | `worksheet-app.js:163` |
| 한 줄당 글자수 | **8자** (`lineBreakCount`) | `profiles.js` |

### 2.4 글리프 렌더링 프로필

`profiles.js`의 `editor` / `compact` 픽셀 수치를 그대로 가져온다. 한 칸(`hangulSet`)은 배경 `32.svg`(빈 원고지 칸) 위에 cho/jung/jong `<img>`를 절대배치하는 구조다.

| | editor | compact |
|---|---|---|
| rowHeight / cellWidth / cellHeight | 170 / 100 / 100 | 80 / 80 / 100 |
| cho (height, top, z) | 32.5, 0, 1 | 25, 0, 1 |
| jung (height, top) | 59.5, 0 | 46, 0 |
| jong (height, top) | 33, 59 | 25.5, 48 |
| background (h, top, left) | 90, 2, 13 | 70, 2, 13 |
| specialHeights | 32/33/63→114, 46→142, 기타 136 | 32/33/63→70, 46→87, 기타 70 |

### 2.5 자모 가리기(`hidebox`) — 학습지의 핵심 기능

교사가 특정 자모를 가려 빈칸 학습지를 만든다. jammin의 실제 규칙:

- **모드 4종** (`#removeCombo`): `jaremove`(자음=초성+종성) · `choremove`(초성) · `jungremove`(중성) · `jongremove`(종성)
- **개별 체크박스 value**:
  - 단일 코드 `"4352"` → `.4352` 요소에 `hidebox` 클래스 부여
  - 쌍 코드 `"4352_4520"` → 초성 ᄀ(4352)과 종성 ᆨ(4520)을 **함께** 가림 (자음 모드에서 같은 자음의 초/종성 쌍)
  - `"all-ja"` / `"all-cho"` / `"all-jung"` / `"all-jong"` → 해당 그룹 전체 토글
- 렌더된 `<img>`의 class에 코드값이 들어 있어(`class='hangul ... cho 4352'`) CSS 선택으로 가린다.

**웹 버전에서의 개선**: jammin은 이 상태가 DOM에만 존재해 저장·재사용이 불가능하다. 신규 웹은 이를 **명시적 데이터**로 모델링한다.

```ts
type HideRule = {
  mode: 'ja' | 'cho' | 'jung' | 'jong';
  codes: number[];        // 가릴 자모 코드 (쌍은 펼쳐서 저장)
};
```

### 2.6 자산

- SVG 83개: `jammin/src/main/resources/static/hangul/*.svg` → **복사**해서 사용(원본 수정 금지)
- KCC 도담도담체(woff/ttf/otf) → 복사. ⚠️**재배포 라이선스 확인 필요**(§9 리스크)

### 2.7 이미 검증된 이식본

Flutter 쪽 `packages/hangul_core`(Dart)가 위 스키마를 이미 이식했고 `add_word_response_test.dart`로 jammin 응답과의 호환을 검증하고 있다. **TS 이식본은 이 Dart 구현과 jammin Java 구현 양쪽을 기준으로 삼는다.**

---

## 3. 시스템 아키텍처

```
                          ┌──────────────────────────────────┐
                          │   종합 서버 (chominjungum-web)    │
                          │   Spring Boot 3.5 + PostgreSQL   │
                          │   학급·문항·과제·성적·리포트       │
                          └───┬───────────┬──────────────┬───┘
                    HTTPS/JWT │           │ 배치 업싱크   │ HTTPS
                              │           │ (오프라인 후) │
            ┌─────────────────▼──┐   ┌────▼─────────┐  ┌▼──────────────┐
            │ 교사 콘솔 (웹)      │   │ 교사 앱       │  │ 학생 응시(웹)  │
            │ Vue 3 + Vite       │   │ Flutter       │  │ Vue 3         │
            │ 학습지·출제·성적    │   │ (LAN 허브)    │  │ 참여코드 입장  │
            └────────────────────┘   └────┬─────────┘  └───────────────┘
                                          │ ws://LAN:30020
                                          │ AES-GCM SyncEnvelope
                                     ┌────▼─────────┐
                                     │ 학생 앱       │
                                     │ Flutter       │
                                     └──────────────┘
                                     ↑ 인터넷 없어도 완전 동작
```

### 3.1 세 가지 운영 모드

| 모드 | 경로 | 인터넷 | 용도 |
|---|---|---|---|
| **교실 모드** | 학생앱 ↔ (LAN WS) ↔ 교사앱 | **불필요** | 수업 중 출제·응시·채점. 현행 그대로 유지 |
| **온라인 모드** | 학생 웹/앱 ↔ 서버 | 필요 | 크롬북 교실, 가정 학습, 앱 미설치 학생 |
| **업싱크** | 교사앱 → 서버 (배치) | 수업 후 | 교실 모드 결과를 성적부에 반영 |

**핵심 원칙: 교실 모드는 서버를 몰라도 된다.** 초민정음의 최대 강점(중앙 서버 없음 → 학교 인프라·개인정보 이슈 회피)을 서버 도입으로 잃지 않기 위해, 서버 연동은 **교사가 명시적으로 켜는 옵트인**이다.

---

## 4. 저장소 구조

```
chominjungum-web/
├── PLAN.md                    ← 이 문서
├── README.md                  ← 실행 방법
├── golden/                    ← 자모 분해 골든 벡터 (3구현 공용 SSOT)
│   ├── hangul-split.json      ← 입력 → 기대 출력
│   └── README.md
├── packages/
│   └── hangul-core-ts/        ← jammin 로직 TS 이식 (분해·채점·hidebox)
│       ├── src/hangul-util.ts
│       ├── src/dictation-compare.ts
│       ├── src/hide-rules.ts
│       └── test/golden.test.ts
├── backend/                   ← Spring Boot 3.5 / Java 17
│   ├── src/main/java/com/chominjungum/...
│   ├── src/main/resources/db/migration/   ← Flyway
│   └── pom.xml
├── frontend/                  ← Vue 3 + Vite + TS
│   ├── src/features/worksheet/   ← 학습지 생성·인쇄 (jammin 업그레이드)
│   ├── src/features/teacher/     ← 학급·과제·성적 콘솔
│   ├── src/features/student/     ← 웹 응시
│   └── public/hangul/            ← SVG 83개 (jammin에서 복사)
├── docs/
│   └── sync-protocol.md       ← 업싱크 프로토콜 상세
└── docker-compose.yml
```

### 4.1 왜 `hangul-core-ts`를 별도 패키지로 두는가

웹이 매번 서버에 `POST /addWord`를 왕복하면 ①오프라인 학습지 생성 불가 ②타이핑마다 네트워크 지연 ③서버 부하. jammin은 실제로 `syncWordLineBreak()`에서 **문항마다 addWord를 재호출**한다(`worksheet-app.js:374-407`) — 폰트 크기만 바꿔도 전 문항이 서버를 때린다. 이걸 그대로 옮기지 않는다.

대신 **분해는 클라이언트 온디바이스**로 하고, 서버는 검증·저장만 한다. 세 구현이 갈라지지 않도록 §5의 골든 테스트로 묶는다.

---

## 5. 동치성 보증 — 골든 벡터

**문제**: 같은 자모 분해 로직이 Java(jammin, 참조) · Dart(Flutter 앱) · TS(웹) 세 곳에 존재한다. 하나라도 어긋나면 교실 모드와 웹 모드의 채점이 달라진다.

**해법**: `golden/hangul-split.json`을 단일 기준으로 두고 각 구현이 이를 통과하는지 CI에서 검사한다.

```json
{
  "version": 1,
  "source": "jammin HangulUtil (read-only reference)",
  "cases": [
    { "input": "안녕하세요", "expected": [ /* Hangul.toJSON() 배열 */ ] },
    { "input": "강아지와고양이", "expected": [ ... ] },
    { "input": "1학년 2반!", "expected": [ ... ] },
    { "input": "Hello", "expected": [] },
    { "input": "ㄱㄴㄷ", "expected": [] }
  ]
}
```

- 최초 생성: jammin 서버를 로컬에서 띄우고 `POST /addWord`를 호출해 응답을 그대로 채집(**읽기 전용 호출, 코드 수정 없음**). 스크립트는 `golden/README.md`에 기록.
- TS: `packages/hangul-core-ts/test/golden.test.ts`
- Dart: chominjungum 저장소의 `hangul_core` 테스트에서 같은 파일을 참조(경로 상대 참조 또는 복사본 + 해시 검증)
- Java(신규 서버): 기존 jammin 코드를 **복사**해 쓰되 동일 골든으로 검증

경계 케이스는 반드시 포함한다: 종성 없는 글자(가), 겹받침(값), 특수문자 5종, 숫자, 16글자 경계, 빈 문자열, 허용 밖 문자.

---

## 6. 데이터 모델 (PostgreSQL)

```sql
-- 계정·조직
teacher(id, email UNIQUE, password_hash, display_name, created_at)
classroom(id, teacher_id FK, name, grade, school_year, join_code UNIQUE, created_at)
student(id, classroom_id FK, display_name, student_no, created_at)
student_device(student_id FK, device_binding_id, bound_at)   -- 앱 기기 ↔ 명단 매핑

-- 콘텐츠
dictation_item(id, owner_teacher_id FK, expected_text, glyphs_json JSONB,
               content_hash, grade, unit, tags TEXT[], created_at)
worksheet(id, teacher_id FK, title, profile, hide_rules JSONB,
          item_ids UUID[], created_at)     -- 학습지 템플릿(가리기 설정 포함)

-- 시험·제출
assignment(id, classroom_id FK, title, mode, opened_at, closed_at, created_at)
   -- mode: ONLINE | CLASSROOM
assignment_item(assignment_id FK, item_id FK, order_no)
exam_session(id UUID, assignment_id FK NULL, classroom_id FK, source,
             started_at, ended_at, synced_at)   -- source: LAN | WEB
attempt(id UUID PK,                      -- = attemptId (멱등 키)
        session_id FK, student_id FK NULL, device_binding_id,
        item_id FK, raw_answer, correct_count, total_count,
        input_kind, glyph_matches JSONB, submitted_at)
UNIQUE(session_id, device_binding_id, item_id)   -- 재제출은 UPSERT
```

`glyph_matches`에 글자별 정오와 사유를 담아야 **취약 자모 분석**이 가능하다. 현재 Flutter의 `AttemptSubmitPayload`에는 `correctCount/totalCount`만 있으므로 프로토콜 확장이 필요하다(§7.2).

### 6.1 개인정보 원칙

- 학생 **실명 저장을 기본으로 하지 않는다**. `display_name`은 "1번", "김OO" 같은 교실 표시명을 권장하고 UI에서 그렇게 안내한다.
- 손글씨 이미지·음성은 **서버에 올리지 않는다**(교실 모드에서 기기 로컬에만).
- 서버 연동 자체가 옵트인. 교사가 업로드하지 않으면 어떤 학생 데이터도 서버에 없다.

---

## 7. 동기화 설계

### 7.1 오프라인 우선 업싱크

```
[수업 중]  교사앱 = LAN 허브. attempt를 로컬 큐에 축적 (인터넷 불필요)
[수업 후]  교사앱 → POST /api/sync/sessions  (배치, 재시도 가능)
```

```jsonc
POST /api/sync/sessions
{
  "sessionId": "uuid",              // 교사앱이 생성 (허브 세션 ID 재사용)
  "classroomId": "uuid",
  "source": "LAN",
  "startedAt": 1756..., "endedAt": 1756...,
  "items": [ { "id":"...", "expectedText":"안녕하세요", "glyphsJson":"[...]" } ],
  "attempts": [
    { "attemptId":"uuid", "itemId":"...", "deviceBindingId":"...",
      "rawAnswer":"안녕하세오", "correctCount":4, "totalCount":5,
      "inputKind":"keyboard", "submittedAtMs":1756...,
      "matches":[{"i":0,"ok":true},{"i":4,"ok":false,"why":"글자 불일치"}] }
  ]
}
```

**멱등성**: `attemptId`가 PK다. 같은 배치를 두 번 보내도 결과가 같다(UPSERT). 네트워크가 불안한 학교에서 재시도를 안전하게 만드는 핵심.

**충돌 해소**: `(sessionId, deviceBindingId, itemId)` 중복 시 `submittedAt`이 최신인 것이 승리. 교실 모드의 현재 동작(같은 기기·같은 문항 재제출 → 최신 대체)과 일치시킨다.

**신원 매핑**: LAN에서 학생은 익명 `deviceBindingId`다. 업싱크 후 교사가 콘솔에서 기기 ↔ 명단을 연결하면(`student_device`) 이후 세션은 자동 매핑된다. 매핑 전 제출도 버리지 않고 미배정 상태로 보관한다.

### 7.2 프로토콜 v2 (하위호환)

`AttemptSubmitPayload`에 선택 필드를 추가한다. 기존 v1 페이로드는 그대로 수용한다.

```dart
// 추가 (nullable — 구버전 앱 호환)
final List<GlyphMatchSummary>? matches;   // [{i, ok, why?}]
final String? sessionId;                  // 서버 업싱크 시 세션 귀속
```

Flutter 쪽 `packages/sync_protocol`에 필드를 더하는 작업이 필요하며, **기존 앱과의 호환을 깨지 않도록 전부 optional**로 둔다.

---

## 8. 기능 설계

### 8.1 학습지 생성 (jammin 업그레이드)

jammin의 `/test` 화면을 Vue로 다시 짓되, 기능은 **동등 이상**을 목표로 한다.

| 항목 | jammin | chominjungum-web |
|---|---|---|
| 자모 분해 | 서버 `POST /addWord` 왕복 | **클라이언트 온디바이스** (오프라인 가능) |
| 폰트 크기 변경 | 전 문항 addWord 재호출 | 상태 갱신만 (네트워크 0) |
| 문항 순서 | DOM `ordering` 속성 직접 조작 | 배열 상태 + 드래그 |
| 가리기 설정 | DOM 클래스에만 존재, 저장 불가 | **`HideRule` 데이터로 저장·재사용** |
| 학습지 저장 | 불가(인쇄만) | 템플릿 저장 → 다음 수업 재사용 |
| 인쇄 | `offset().top/880` 하드코딩 | CSS `@page` + `break-inside: avoid` |
| 제약 | 16자·20줄 | **동일하게 준용** |

### 8.2 학생 웹 응시

1. 학생이 `참여코드`(classroom.join_code) + 표시명 입력 → 단기 토큰 발급(계정 없음)
2. 열린 과제의 문항 수신 → 답안 입력 → **클라이언트 채점**(`hangul-core-ts`의 `DictationCompare` 이식본)
3. `POST /api/attempts`로 제출. 서버는 받은 답안을 **서버 측에서 재채점해 검증**한다(클라이언트 점수를 신뢰하지 않음)

> 채점 로직이 클라이언트에도 있는 이유는 즉시 피드백(초등 학습에 중요) 때문이고, 서버 재채점은 성적 신뢰성 때문이다. 두 결과가 다르면 서버 값을 채택하고 로그를 남긴다.

### 8.3 성적·리포트 (ERP 성격)

- 학생별: 회차별 점수 추이, **취약 자모 Top N**(`glyph_matches` 집계 — 어떤 초성/중성/종성에서 자주 틀리는지)
- 학급별: 평균·분포, 문항별 정답률(= 문항 난이도 실측), 미제출자
- 출력: 성적표 PDF, CSV 내보내기
- 문항은행: 정답률 데이터가 쌓이면 난이도 태깅이 자동화된다

---

## 9. 리스크·미결정

| # | 항목 | 내용 | 상태 |
|---|---|---|---|
| R1 | **배포 서버 리소스** | 기존 배포 대상 서버는 이미 다수의 JVM 서비스를 얹고 있어 여유가 적다. 여기에 JVM(~400MB)을 더하면 체감 저하 위험 | **하드웨어 증설 후 배포 권장**. 개발은 로컬 |
| R2 | **폰트/SVG 라이선스** | KCC 도담도담체 재배포 조건, SVG 자산 출처 | 배포 전 확인 필요 |
| R3 | **개인정보** | 학생 데이터 서버 저장 = 초민정음의 차별점 훼손 가능 | 옵트인 + 표시명 원칙으로 완화(§6.1) |
| R4 | **프로토콜 v2** | Flutter 앱 업데이트 필요 | 전 필드 optional로 하위호환 |
| R5 | **분해 로직 3중화** | 구현 드리프트 | 골든 벡터 CI(§5) |
| R6 | 학교 계정 체계 | 교육청 SSO 연동 여부 | 미결정 — 자체 계정으로 시작 |

---

## 10. 로드맵

각 Phase는 **검증 가능한 완료 기준**을 갖는다.

### Phase 0 — 기반 (스캐폴딩 + 동치성) ✅ 2026-09-02 완료
- [x] `hangul-core-ts` 이식: `hangulSplit`, `isHangul`, `DictationCompare`, `HideRule`
- [x] `golden/hangul-split.json` 채집(jammin 읽기 전용 호출, 22케이스) + TS 골든 테스트 GREEN
- [x] Dart `hangul_core`도 동일 골든 통과 확인 (`test/golden_test.dart`)
- [x] Vue 3 + Vite 스캐폴딩, SVG 83개 복사
- **완료 기준 달성**: TS 47/47 + `tsc`/`vue-tsc` 클린 + `vite build` 성공 / Dart 30/30(골든 22 포함) / jammin 무변경

**Phase 0 실측 기록**
- 골든 채집: jammin을 **설정 파일 수정 없이** 커맨드라인 오버라이드로 기동해 실응답을 받았다.
  `./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--server.port=8089 --server.ssl.enabled=false"`
  (원본 `application.properties`는 443 + keystore 고정이라 그대로는 macOS에서 권한 문제로 못 뜬다)
- Java `char` 래핑 재현: 원본은 `char comVal = (char)(ch - 0xAC00)` 로 **16비트 무부호 래핑**을 한다.
  TS 이식본은 `& 0xFFFF` 로 같은 동작을 재현했다. Dart 구현은 int 뺄셈 + `comVal >= 0` 검사로
  경로가 다르지만 **결과는 동일**하다(허용 문자 검사가 앞서 걸러 실제 도달 불가).
- Java `HANGUL_SYLLABLES` 블록은 AC00–**D7AF**, Dart 이식본은 AC00–**D7A3**로 상한이 다르다.
  그 사이(D7A4–D7AF)는 미할당 코드포인트라 실무 영향은 없으나 **알려진 차이로 기록**해 둔다.
- SVG는 83개가 전부다(초성19 + 중성21 + 종성27 + 특수·숫자15 + 배경1). `static_back/`은 백업본이라 쓰지 않는다.

### Phase 1 — 학습지 (서버 불요) ✅ 2026-09-02
- [x] 문항 입력·분해·글리프 렌더(editor/compact 프로필 준용)
- [x] 자모 가리기 4모드 + 개별/전체 토글 (원본 체크박스 구성 그대로: 자음 14+16 · 초성 14+5 · 중성 10+11 · 종성 14+13)
- [x] 인쇄 레이아웃(@page A4 + 문항 단위 break-inside), 16자·20줄 제약
- [x] 문항 추가·수정·삭제·순서 이동, localStorage 영속화
- **검증**: hangul-core 55/55 + frontend 14/14 + vue-tsc·vite build
- ⚠️ **실기기 인쇄 확인은 남아 있다** — 브라우저 인쇄 미리보기로 눈으로 봐야 한다.

### Phase 2 — 서버 + 웹 응시 ✅ 2026-09-02
- [x] Spring Boot 3.5.6 + PostgreSQL 16 + Flyway(V1), 교사 JWT / 학생 참여코드 토큰
- [x] 학급·명단·기기바인딩·문항·과제 CRUD (교사 소유 검증)
- [x] 참여코드 입장 → 웹 응시 → **서버 재채점** → 즉시 피드백
- [x] jammin 로직 Java 복사본도 같은 골든 통과 → **Java·Dart·TS 3구현 동치**
- **검증**: 백엔드 통합 e2e 7건 — 교사 가입→학급→문항→과제→학생 참여→응시→재채점 한 사이클
- 설계 결정: **정답 텍스트를 응시 화면에 내려주지 않는다.** 받아쓰기는 듣고 쓰는 활동이라
  화면에는 문항 번호와 칸 수만 보이고, 정답은 제출 직후 결과에서만 공개된다.

### Phase 3 — 업싱크 (앱 연계) ✅ 2026-09-02
- [x] `sync_protocol` v2(matches·sessionId — **둘 다 optional 이라 구버전 앱과 호환**)
- [x] Flutter 교사앱: 기기에 영속되는 로컬 큐 + 업로드 버튼(실패해도 큐 유지)
- [x] `POST /api/sync/sessions` 멱등 수신, 부분 성공, 미배정 기기 반환
- [x] 기기↔명단 매핑 UI + **소급 반영**(연결 시 그 기기의 과거 미배정 제출이 성적에 붙음)
- **검증**: 업싱크 통합 6건 — 같은 배치 2회 전송 시 accepted 1 / duplicated 1,
  모르는 문항만 거부하고 나머지는 저장, 남의 학급으로 업로드 시 403
- ⚠️ **실기기 2대 리허설은 남아 있다** — 코드 경로는 자동 테스트로 덮었지만 교실 왕복은 눈으로 봐야 한다.

### Phase 4 — 성적·리포트 ✅ 2026-09-02
- [x] 학급 리포트(학생별 평균·만점수, 문항별 정답률=난이도 실측, 미배정 건수)
- [x] 학생 리포트(제출 이력 + **취약 자모 Top 10**)
- [x] 성적표 CSV(엑셀 BOM)
- **검증**: 리포트 통합 5건 — 평균·문항 정답률, 취약 자모 집계, 만점이면 취약 자모 없음, CSV, 타 교사 403
- **취약 자모가 학습지로 이어진다**: 분석 결과의 자모 코드가 가리기 기능과 같은 체계라,
  "이 학생이 자주 틀리는 자모만 가린 학습지"를 바로 만들 수 있다. (자동 연결 UI 는 후속)
- PDF 출력은 미구현 — 현재는 브라우저 인쇄로 대체한다.

---

## 11. 기술 스택

| 구분 | 선택 | 근거 |
|---|---|---|
| 백엔드 | Spring Boot 3.5 / Java 17 | 워크스페이스 표준 |
| DB | **PostgreSQL** + Flyway | 성적·학급 같은 관계형 데이터에 적합. 컨테이너 기준 메모리 사용도 가벼움(수십 MB대) |
| 프론트 | Vue 3 + Vite + TS (vue-tsc) | 워크스페이스 표준 |
| 공용 로직 | TS 패키지 `hangul-core-ts` | 오프라인 분해·채점 |
| 인증 | 교사=JWT, 학생=참여코드 단기 토큰 | 학생 계정·개인정보 최소화 |
| 배포 | Docker Compose | 워크스페이스 표준 |

---

## 12. 참고

- [jammin](../jammin) — 원본 웹. `HangulUtil.java`, `Hangul.java`, `glyph-renderer.js`, `profiles.js`, `worksheet-app.js`. **수정 금지**
- [chominjungum](../chominjungum) — Flutter 앱. `packages/hangul_core`(Dart 이식본), `packages/sync_protocol`(LAN 프로토콜)
- 프로토콜 상세: `docs/sync-protocol.md`

---

## 13. 다음 단계 (2026-09-02 이후)

Phase 0~4 로 **기능 골격과 배포는 끝났다**(.25 라이브, 라이브 e2e 통과). 사용자 브라우저 확인 결과 "구색은 맞다".
여기서부터는 **만듦새**의 문제다.

### 13.1 디자인 (다음 세션 1순위)

현재 프론트는 시스템 폰트 + 초록(`#296429`) 최소 스타일이다. 생태계 안에서 톤이 셋으로 갈려 있다:

| | 톤 |
|---|---|
| jammin (원본 웹) | Bootstrap Agency + **골든 옐로우 `#ffc800`** + KCC 도담도담체 |
| chominjungum (앱) | M3 + jammin 토큰(brand `#296429` 계열) |
| chominjungum-web (지금) | 시스템 폰트 + 초록 최소 스타일 |

- **폰트를 먼저 결정해야 한다.** KCC 도담도담체는 재배포 라이선스 미확인 상태라 아직 가져오지 않았다(§9 R2).
  초등 대상이라 손글씨 계열 폰트의 효과가 크므로, 라이선스 확인 → 번들 여부 결정이 디자인의 전제다.
- 학습지(인쇄물)와 콘솔(화면 도구)은 **성격이 다르다**. 학습지는 종이 결과물이라 원본 톤을 따르는 편이 맞고,
  콘솔·성적표는 작업 도구라 단색·저채도로 두는 편이 읽기 편하다.

### 13.2 세부 컴포넌트

- 학습지: 문항 드래그 정렬(현재는 ↑↓ 버튼), 가리기 프리셋 저장, 인쇄 미리보기 전용 화면
- 교사 콘솔: 학급 전환 UX, 문항 검색·태그(스키마엔 `grade`/`unit`/`tags` 가 이미 있다), 명단 일괄 입력(엑셀 붙여넣기)
- 응시: 문항 이동(현재는 전 문항 한 화면), TTS 문제 읽기, 큰 글씨 모드
- 리포트: 점수 추이 차트, **취약 자모 → 그 자모만 가린 학습지 바로 만들기**(자모 코드 체계가 같아 연결만 하면 된다)

### 13.3 방향성 (사용자 판단 영역)

- 이 웹을 **교사 개인 도구**로 둘지, **학교 단위 서비스**로 키울지 — 인증·개인정보 설계의 갈림길
- 학부모 리포트 열람(Phase 설계 시 보류함)을 넣을지
- 상품화 평가 미실시. `CLAUDE.md` 상품화 표에 chominjungum-web 은 아직 미평가로 올라가 있다

### 13.4 남은 검증 (헤드리스로 불가)

- [ ] 학습지 인쇄 미리보기가 jammin 과 같아 보이는지 눈으로 확인
- [ ] 교실 2대(교사앱 + 학생앱) 왕복 리허설 → 업싱크까지
