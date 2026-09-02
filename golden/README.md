# 골든 벡터 — 자모 분해 동치성

같은 자모 분해 로직이 **세 곳**에 존재한다.

| 구현 | 위치 | 지위 |
|---|---|---|
| Java | `jammin/src/main/java/com/jammin/util/HangulUtil.java` | **참조 원본 (수정 금지)** |
| Dart | `chominjungum/packages/hangul_core` | 앱용 이식본 |
| TS | `chominjungum-web/packages/hangul-core-ts` | 웹용 이식본 |

하나라도 어긋나면 교실 모드와 웹 모드의 채점 결과가 달라진다. `hangul-split.json`이 셋을 묶는 단일 기준이다.

## 채집 방법 (jammin은 읽기 전용으로만 사용)

```bash
# 1) jammin을 로컬에서 기동 (코드 변경 없음)
cd ../../jammin && ./mvnw spring-boot:run

# 2) 케이스별로 /addWord 응답을 그대로 수집
curl -s -X POST http://localhost:8080/addWord \
  -H 'Content-Type: application/json; charset=utf-8' \
  -d '{"requestWord":"안녕하세요"}'
```

수집한 응답을 `cases[].expected`에 **가공 없이** 넣는다. 키 순서는 무관하되 값은 그대로 둔다.

## 포함해야 할 경계 케이스

| 케이스 | 입력 예 | 확인 대상 |
|---|---|---|
| 종성 없음 | `가` | `emptyJongsung=true`, `jongsung` 키 부재 |
| 겹받침 | `값` | `jongCode`가 겹받침 코드 |
| 쌍자음 초성 | `까치` | `choCode` |
| 특수문자 5종 | `안녕. 잘 가!` | `specialFlag=true`, `specialTypeCode` |
| 숫자 | `1학년 2반` | 숫자도 special 취급 |
| 허용 밖 | `Hello`, `ㄱㄴㄷ`, `漢字` | **빈 배열** |
| 길이 경계 | 16글자 / 17글자 | 17자는 UI에서 거부(분해 자체는 됨) |
| 빈 문자열 | `` | 빈 배열 |

## 파일 형식

```json
{
  "version": 1,
  "source": "jammin HangulUtil (read-only reference)",
  "collectedAt": "2026-09-02",
  "cases": [
    { "input": "안녕하세요", "expected": [ /* addWord 응답 배열 그대로 */ ] }
  ]
}
```

## 각 구현의 검증

- TS: `packages/hangul-core-ts/test/golden.test.ts` — `npm test`
- Dart: `chominjungum/packages/hangul_core/test/` 에서 같은 파일을 읽어 대조
- Java(신규 서버): jammin 코드를 복사해 쓰되 동일 골든으로 검증

⚠️ 골든을 고칠 때는 **jammin 실제 응답으로 다시 채집**한다. 손으로 기대값을 편집하면 세 구현이 함께 틀리는 사태가 벌어진다.
