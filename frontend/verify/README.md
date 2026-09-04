# 시각 검증 harness (개발 전용)

자모 배치는 자동 테스트로 **수치**는 검증되지만(`test/hangul-metrics.test.ts`),
"실제로 눈에 어떻게 보이는가"는 렌더해 봐야 안다. 이 디렉토리가 그 수단이다.

```bash
npm run dev            # → http://localhost:5180/verify.html
npm run verify:shot    # 헤드리스로 스크린샷 + 인쇄 PDF 를 verify/out/ 에 남긴다
```

## 왜 jammin 렌더러를 복사해 뒀나

`jammin-ref/` 는 jammin 원본 파일의 **바이트 그대로의 복사본**이다(수정 금지).

- `profiles.js` / `glyph-renderer.js` — jammin `static/js/jammin-packages/hangul-worksheet/`

jammin 의 글리프 렌더링은 순수 클라이언트 JS 라서, **jammin 서버를 띄우지 않고도**
원본과 완전히 같은 DOM 을 만들 수 있다. 자산(`public/hangul/*.svg`)도 동일하므로
같은 화면에서 원본과 새 구현을 **겹쳐서** 대조할 수 있다.

이 복사본은 대조 기준일 뿐이고 제품 코드가 아니다. 빌드에도 들어가지 않는다
(`verify.html` 은 `rollupOptions.input` 에 없다).

동기화 확인:

```bash
shasum verify/jammin-ref/*.js \
  ../../jammin/src/main/resources/static/js/jammin-packages/hangul-worksheet/{profiles,glyph-renderer}.js
```

## 대조에서 보는 것

| 섹션 | 무엇을 판정하나 |
|---|---|
| 겹침 대조 | 원본(빨강)과 새 구현(파랑)이 **보라로 합쳐지는지**. 따로 보이면 어긋난 것 |
| 크기 스윕 | 칸 크기를 바꿔도 초/중/종 상대 위치가 유지되는지 |
| 받침·특수문자 | 받침 있는 글자, 쉼표·마침표·숫자의 세로 위치 |
| 가리기 4모드 | `hidebox` 가 자리를 유지한 채 감추는지 |
| 인쇄 | A4 로 뽑았을 때 잘리거나 넘치지 않는지 |
