#!/usr/bin/env node
/**
 * 커밋된 골든이 **지금의 jammin 과 아직 같은지** 대조한다.
 *
 * ## 왜 필요한가
 *
 * `hangul-split.json` 은 채집한 **스냅샷**이다. 세 이식본(Java·Dart·TS)이 이 파일과
 * 맞는지는 각 저장소 테스트가 보지만, **그 파일 자체가 jammin 과 어긋나면** 셋이
 * 사이좋게 같이 틀린다. 원본이 바뀌었는데 아무도 모르는 상태가 그것이다.
 *
 * ## 쓰는 법 (jammin 은 읽기 전용 — 코드도 설정도 고치지 않는다)
 *
 * ```bash
 * # 1) 이미 빌드된 jar 로 띄운다. 포트·SSL 은 커맨드라인으로만 넘긴다.
 * cd ../../jammin
 * java -jar target/jammin-0.0.1-SNAPSHOT.jar \
 *   --server.port=8089 --server.ssl.enabled=false
 *
 * # 2) 대조
 * node golden/verify-against-jammin.mjs
 * ```
 *
 * ⚠️**context path 가 `/chominjungum` 이다** — `application.properties` 에 박혀 있다.
 * `/addWord` 로 바로 치면 404 가 나고, 그걸 "서버가 안 떴다"로 오해하기 쉽다
 * (2026-09-10 에 실제로 그랬다).
 *
 * 종료코드: 0 일치 · 1 불일치 · 2 서버에 못 붙음(= **검사 못 함**, 통과 아님)
 */

import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const BASE =
  process.env.JAMMIN_BASE ?? 'http://localhost:8089/chominjungum'
const GOLDEN = fileURLToPath(new URL('./hangul-split.json', import.meta.url))

/** 키 순서는 무시하고 값만 본다. */
function norm(v) {
  if (Array.isArray(v)) return v.map(norm)
  if (v && typeof v === 'object') {
    return Object.fromEntries(
      Object.keys(v)
        .sort()
        .map((k) => [k, norm(v[k])]),
    )
  }
  return v
}

async function addWord(word) {
  const res = await fetch(`${BASE}/addWord`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ requestWord: word }),
    signal: AbortSignal.timeout(10_000),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

const { cases } = JSON.parse(readFileSync(GOLDEN, 'utf8'))

try {
  // 붙는지 먼저 본다 — 못 붙으면 "검사 못 함"이지 "통과"가 아니다.
  await addWord('가')
} catch (e) {
  console.error(
    `jammin 에 못 붙었습니다 (${BASE}): ${e.message}\n` +
      '  → jar 로 띄우고 다시 시도하세요. context path 가 /chominjungum 인 것에 주의.',
  )
  process.exit(2)
}

const bad = []
for (const c of cases) {
  // ⚠️빈 문자열 케이스가 있다. falsy 로 거르면 조용히 건너뛴다(실제로 그랬다).
  const word = c.input
  let got
  try {
    got = await addWord(word)
  } catch (e) {
    bad.push([word, `요청 실패: ${e.message}`])
    continue
  }
  if (JSON.stringify(norm(got)) !== JSON.stringify(norm(c.expected))) {
    bad.push([word, `골든 ${c.expected.length}자 vs 실제 ${got.length}자`])
  }
}

console.log(`대조 ${cases.length - bad.length}/${cases.length} 일치`)
for (const [w, why] of bad.slice(0, 10)) {
  console.error(`  ❌ ${JSON.stringify(w)}: ${why}`)
}
if (bad.length) {
  console.error(
    '\n골든이 jammin 과 어긋났습니다. 원본이 바뀌었다면 `collect.mjs` 로 다시 채집하고,\n' +
      '세 이식본(Java·Dart·TS) 테스트를 모두 다시 돌릴 것.',
  )
}
process.exit(bad.length ? 1 : 0)
