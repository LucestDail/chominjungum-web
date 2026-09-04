/**
 * 시각 검증 화면을 헤드리스로 찍는다 — 사람이 브라우저를 열지 않아도 결과가 파일로 남는다.
 *
 *   npm run dev            # 다른 터미널에서
 *   npm run verify:shot    # frontend/verify/out/ 에 png + 인쇄 pdf
 *
 * 시스템 Chrome 을 쓴다(별도 의존성 없음).
 */
import { execFileSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const CHROME =
  process.env.CHROME_PATH ??
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const BASE = process.env.VERIFY_URL ?? 'http://localhost:5180/verify.html';
const OUT = fileURLToPath(new URL('../frontend/verify/out/', import.meta.url));

mkdirSync(OUT, { recursive: true });

/** [파일명, 쿼리, 창 크기] */
const SHOTS = [
  ['all.png', '', '1280,2400'],
  ['overlay.png', '?only=A&zoom=2', '1200,1000'],
  ['special.png', '?only=B&zoom=1.6', '1150,1400'],
  ['sizes.png', '?only=C&zoom=1.6', '1200,700'],
  ['sheet-compare.png', '?only=G', '1250,1500'],
];

const common = [
  '--headless=new',
  '--disable-gpu',
  '--hide-scrollbars',
  '--force-device-scale-factor=2',
  '--virtual-time-budget=6000',
];

for (const [name, query, size] of SHOTS) {
  execFileSync(CHROME, [...common, `--window-size=${size}`, `--screenshot=${OUT}${name}`, BASE + query], {
    stdio: 'ignore',
  });
  console.log('shot', name);
}

execFileSync(
  CHROME,
  [...common, '--no-pdf-header-footer', `--print-to-pdf=${OUT}print.pdf`, `${BASE}?only=E`],
  { stdio: 'ignore' },
);
console.log('pdf  print.pdf');
console.log('→', OUT);
