#!/usr/bin/env node
/**
 * 골든 벡터 채집 — jammin 서버의 `POST /addWord` 응답을 가공 없이 수집한다.
 *
 * jammin 은 읽기 전용으로만 쓴다. 설정 파일도 고치지 말고 커맨드라인으로 포트를 넘길 것:
 *
 *   cd ../../jammin
 *   ./mvnw -q spring-boot:run \
 *     -Dspring-boot.run.arguments="--server.port=8089 --server.ssl.enabled=false"
 *
 *   node golden/collect.mjs > golden/hangul-split.json
 */

const BASE = process.env.JAMMIN_BASE ?? 'http://localhost:8089';

/** 경계 케이스 — golden/README.md 의 표와 1:1 대응 */
const INPUTS = [
  // 기본
  '안녕하세요',
  '강아지와고양이',
  // 종성 없음 / 있음
  '가',
  '간',
  // 겹받침
  '값',
  '없다',
  // 쌍자음 초성 · 이중모음
  '까치',
  '의외의',
  // 특수문자 5종
  '안녕. 잘 가!',
  '누구, 어디?',
  // 숫자
  '1학년 2반',
  '0123456789',
  // 공백만
  ' ',
  // 길이 경계 (16자 / 17자) — 분해 자체는 되고, 거부는 UI 계층의 몫
  '가나다라마바사아자차카타파하거너',
  '가나다라마바사아자차카타파하거너더',
  // 허용 밖 — 빈 배열이어야 한다
  'Hello',
  'ㄱㄴㄷ',
  'ㅏㅑㅓ',
  '漢字',
  '한글English',
  '이모지🙂',
  // 빈 문자열
  '',
];

async function addWord(requestWord) {
  const res = await fetch(`${BASE}/addWord`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ requestWord }),
  });
  if (!res.ok) throw new Error(`addWord(${JSON.stringify(requestWord)}) → HTTP ${res.status}`);
  const text = await res.text();
  return text === '' ? [] : JSON.parse(text);
}

const cases = [];
for (const input of INPUTS) {
  cases.push({ input, expected: await addWord(input) });
}

const doc = {
  version: 1,
  source: 'jammin HangulUtil (read-only reference)',
  collectedAt: new Date().toISOString().slice(0, 10),
  note: '손으로 편집하지 말 것. 반드시 jammin 실제 응답으로 다시 채집한다.',
  cases,
};

process.stdout.write(JSON.stringify(doc, null, 2) + '\n');
