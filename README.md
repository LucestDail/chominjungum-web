# 초민정음 Web (chominjungum-web)

초민정음 생태계의 **웹 레이어 + 종합 서버**.
교실에서는 서버 없이 굴러가고(Flutter 앱의 LAN 직결 유지), 온라인일 때 학급·과제·성적을 한곳에 모은다.

> 설계 전문은 **[PLAN.md](PLAN.md)** 를 먼저 읽을 것.

---

## 생태계에서의 위치

| 저장소 | 역할 | 관계 |
|---|---|---|
| [jammin](../jammin) | 받아쓰기 학습지 생성·인쇄 웹 (Spring Boot + Thymeleaf) | **원본. 절대 수정 금지.** 자모 분해 로직·SVG·제약을 준용만 함 |
| [chominjungum](../chominjungum) | Flutter 앱. 교실 LAN 직결 출제·응시·채점 | 업싱크로 이 서버에 성적 전송 |
| **chominjungum-web** (여기) | 교사 콘솔 + 학생 웹 응시 + 종합 서버 | 신규 |

## 세 가지 운영 모드

1. **교실 모드** — 학생앱 ↔ LAN WebSocket ↔ 교사앱. **인터넷 불필요**. 현행 유지
2. **온라인 모드** — 브라우저로 응시. 크롬북 교실·가정 학습·앱 미설치 학생
3. **업싱크** — 수업 후 교사앱이 결과를 서버로 배치 전송(멱등)

**서버 연동은 옵트인이다.** 교사가 업로드하지 않으면 학생 데이터는 서버에 존재하지 않는다.

## 구조

```
packages/hangul-core-ts/   자모 분해·채점·가리기 규칙 (TS 이식본)
golden/                    3구현(Java·Dart·TS) 동치성 검증용 골든 벡터
backend/                   Spring Boot 3.5 + PostgreSQL + Flyway
frontend/                  Vue 3 + Vite + TS
docs/sync-protocol.md      업싱크 프로토콜
```

## 실행

npm workspaces 모노레포다. **루트에서 한 번만** 설치한다.

```bash
npm install

npm test          # 공용 로직 테스트 (골든 동치성) — 47 tests
npm run typecheck # tsc + vue-tsc
npm run dev       # 프론트 개발 서버 (http://localhost:5180)
npm run build     # vue-tsc + vite build
```

### 골든 벡터 재채집

`golden/hangul-split.json`은 jammin 실응답이다. 손으로 고치지 말고 다시 채집한다.

```bash
# 1) jammin 기동 — 설정 파일은 건드리지 않고 커맨드라인으로만 덮어쓴다
cd ../jammin
./mvnw -q spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8089 --server.ssl.enabled=false"

# 2) 채집
cd ../chominjungum-web && npm run golden:collect
```

Dart 구현도 같은 파일을 본다:

```bash
cd ../chominjungum/packages/hangul_core && dart test    # golden_test.dart 포함
```

### 아직 없는 것

`backend/`(Spring Boot)는 Phase 2에서 만든다. `docker-compose.yml`도 그때 함께 추가한다.

## 준용 규칙 (요약)

jammin에서 가져와 **바꾸면 안 되는 것**:

- 자모 분해 스키마와 코드값 (`choCode` 4352~, `jungCode` 4449~, `jongCode` 4519=종성없음)
- 허용 문자 집합 (완성형 한글 + `공백 . , ? !` + 숫자) — 그 외는 출제 거부
- 출제 제약 (문장 16글자, 학습지 20줄, 한 줄 8자)
- 글리프 렌더 프로필 수치 (editor / compact)
- 자모 가리기 4모드 (자음 / 초성 / 중성 / 종성)

상세는 [PLAN.md §2](PLAN.md).
