<script setup lang="ts">
/**
 * 자모 배치 시각 검증 — 조작 없이 한 화면에 모든 케이스를 펼친다.
 *
 * 수치는 `test/hangul-metrics.test.ts` 가 지키고, 여기서는 **눈으로만 알 수 있는 것**을 본다:
 * 원본과 겹쳤을 때 어긋나는지, 크기를 바꿔도 유지되는지, 인쇄하면 어떻게 나오는지.
 */
import { computed, ref } from 'vue';
import { addWord, EMPTY_HIDE_RULE, type HangulGlyph, type HideMode } from '@chominjungum/hangul-core';

import HangulCell from '../src/features/worksheet/HangulCell.vue';
import WorksheetItemRow from '../src/features/worksheet/WorksheetItemRow.vue';
import { BOX, CANVAS, CHO, JONG, JUNG, toPercent } from '../src/features/worksheet/hangul-metrics';
import { PROFILES as JAMMIN } from './jammin-ref/profiles.js';
import JamminCell from './JamminCell.vue';
import JamminSheet from './JamminSheet.vue';
import ServerScreens from './ServerScreens.vue';
import ScoreTrendChart from '../src/features/teacher/ScoreTrendChart.vue';

/** 원본 editor 프로필의 자모 상자 크기(역산값). 겹침 대조는 이 크기로 맞춘다. */
const REF_BOX = 93;

function glyphs(text: string): HangulGlyph[] {
  return addWord(text);
}
function one(ch: string): HangulGlyph {
  const g = glyphs(ch);
  if (!g.length) throw new Error(`분해 실패: ${ch}`);
  return g[0]!;
}

/** 대조용 글자 — 받침 없음 / 있음 / 겹받침 / 특수 */
const OVERLAY_CHARS = ['가', '하', '값', '없', '닭', '읽', '뷁'];
const overlayGlyphs = computed(() => OVERLAY_CHARS.map(one));

const SPECIALS = [' ', '!', ',', '.', '?', '0', '5', '9'];
const specialGlyphs = computed(() => SPECIALS.map(one));

const SIZES = [48, 64, 93, 128, 180];
const sweepGlyph = computed(() => one('값'));

/** 비율 오버레이 — 상수가 말하는 위치. 글자가 이 선에 맞아야 한다. */
const guides = {
  cho: toPercent(CHO),
  jung: toPercent(JUNG),
  jong: toPercent(JONG),
};

const HIDE_MODES: HideMode[] = ['ja', 'cho', 'jung', 'jong'];
/** 모드별로 '값'(ㄱ/ㅏ/ㅄ)에 해당하는 자모를 전부 가린 규칙 */
function ruleFor(mode: HideMode) {
  const g = one('값');
  const codes: number[] = [];
  if (g.choCode !== undefined) codes.push(g.choCode);
  if (g.jungCode !== undefined) codes.push(g.jungCode);
  if (g.jongCode !== undefined) codes.push(g.jongCode);
  return { mode, codes };
}

const SENTENCE = '학교에 갔다.';
const sentenceItem = computed(() => ({
  id: 'verify-1',
  text: SENTENCE,
  glyphs: glyphs(SENTENCE),
}));
const SENTENCE2 = '값을 읽고 답을 썼다';
const sentenceItem2 = computed(() => ({
  id: 'verify-2',
  text: SENTENCE2,
  glyphs: glyphs(SENTENCE2),
}));

/** 점수 추이 차트 — 라벨이 축·경계와 부딪히기 쉬운 경우들 */
const CHART_CASES: { name: string; points: { label: string; value: number }[] }[] = [
  { name: '보통 등락', points: [86, 82, 90, 88, 75].map((v, i) => ({ label: `${i + 1}회`, value: v })) },
  { name: '첫 회가 만점 (위쪽 끝)', points: [100, 92, 88, 96, 100].map((v, i) => ({ label: `${i + 1}회`, value: v })) },
  { name: '전부 만점', points: [100, 100, 100].map((v, i) => ({ label: `${i + 1}회`, value: v })) },
  { name: '전부 0점 (아래쪽 끝)', points: [0, 0, 0, 0].map((v, i) => ({ label: `${i + 1}회`, value: v })) },
  { name: '1회만', points: [72].map((v, i) => ({ label: `${i + 1}회`, value: v })) },
  { name: '많은 회차', points: [60, 72, 55, 80, 68, 90, 74, 88, 95, 100].map((v, i) => ({ label: `${i + 1}회`, value: v })) },
];

/** 학습지 전체 대조용 문장 — 받침·특수문자·숫자를 모두 포함시킨다 */
const COMPARE_SENTENCES = ['학교에 갔다.', '값이 5개, 없다?', '읽고 썼다!'];

/** 겹침 대조에서 배경(원고지)을 포함할지 */
const overlayGuide = ref(false);

/**
 * URL 로 화면을 좁히거나 키운다 — 스크린샷으로 한 부분만 크게 볼 때 쓴다.
 *   ?only=B&zoom=3&diagnose
 */
const params = new URLSearchParams(location.search);
const diagnose = ref(params.has('diagnose'));
const zoom = Number(params.get('zoom') ?? 1) || 1;
const only = params.get('only');
function shows(section: string): boolean {
  return !only || only.toUpperCase().includes(section);
}
if (params.has('guide')) overlayGuide.value = true;

/** 서버 화면(H) 은 로그인 정보가 URL 로 올 때만 띄운다 */
const serverArgs = {
  email: params.get('email') ?? undefined,
  pw: params.get('pw') ?? undefined,
  join: params.get('join') ?? undefined,
  name: params.get('name') ?? undefined,
  openStudent: params.has('student'),
  play: params.get('play') ?? undefined,
};
</script>

<template>
  <!-- 검은 SVG 를 색으로 물들이는 필터 (알파 유지, RGB 고정) -->
  <svg width="0" height="0" class="tint-defs" aria-hidden="true">
    <filter id="tintRed">
      <feColorMatrix
        type="matrix"
        values="0 0 0 0 0.85  0 0 0 0 0.11  0 0 0 0 0.14  0 0 0 1 0"
      />
    </filter>
    <filter id="tintBlue">
      <feColorMatrix
        type="matrix"
        values="0 0 0 0 0.09  0 0 0 0 0.32  0 0 0 0 0.88  0 0 0 1 0"
      />
    </filter>
  </svg>

  <main class="verify" :class="{ diagnose }" :style="{ zoom }">
    <header class="head no-print">
      <h1>자모 배치 시각 검증</h1>
      <p>
        <b class="red">빨강</b> = jammin 원본 렌더러 ·
        <b class="blue">파랑</b> = 새 구현(HangulCell). 겹쳐서 <b>보라 한 덩어리</b>로 보이면
        일치, 빨강·파랑이 따로 보이면 어긋난 것이다.
      </p>
    </header>

    <!-- A. 겹침 대조 -->
    <section v-if="shows('A')" class="no-print">
      <h2>A. 원본 겹침 대조 <small>상자 {{ REF_BOX }}px</small></h2>
      <label class="opt">
        <input v-model="overlayGuide" type="checkbox" />
        원고지 배경도 겹치기 (원본 배경 = <code>32.svg</code>, 새 구현 = <code>000000.svg</code>)
      </label>

      <div class="strip">
        <div v-for="(g, i) in overlayGlyphs" :key="i" class="overlay-item">
          <div class="overlay" :style="{ width: `${REF_BOX}px`, height: `${REF_BOX}px` }">
            <div class="layer ref">
              <JamminCell :glyph="g" :show-guide="overlayGuide" />
            </div>
            <div class="layer mine">
              <HangulCell :glyph="g" :size="REF_BOX" :show-guide="overlayGuide" />
            </div>
          </div>
          <span class="cap">{{ OVERLAY_CHARS[i] }}</span>
        </div>
      </div>

      <h3>나란히 보기</h3>
      <div class="strip">
        <div v-for="(g, i) in overlayGlyphs" :key="`s${i}`" class="pair">
          <div class="cell-box">
            <JamminCell :glyph="g" :show-guide="true" />
            <span class="tag">원본</span>
          </div>
          <div class="cell-box">
            <HangulCell :glyph="g" :size="REF_BOX" />
            <span class="tag">새 구현</span>
          </div>
        </div>
      </div>
    </section>

    <!-- B. 특수문자 -->
    <section v-if="shows('B')" class="no-print">
      <h2>B. 특수문자·숫자</h2>
      <p class="note">
        원본 <code>specialHeights</code>: 32/33/63 → 114 · 46 → 142 · 그 외(쉼표·숫자) →
        136. 숫자 SVG 는 정사각(598.28²)인데 default 136 이 걸려 다른 글자보다 크게 나온다.
        새 구현은 폭을 상자에 맞추므로 모두 같은 폭이다.
      </p>
      <div class="strip">
        <div v-for="(g, i) in specialGlyphs" :key="i" class="pair">
          <div class="cell-box">
            <JamminCell :glyph="g" />
            <span class="tag">원본</span>
          </div>
          <div class="cell-box">
            <HangulCell :glyph="g" :size="REF_BOX" />
            <span class="tag">새 구현</span>
          </div>
          <span class="cap">{{ SPECIALS[i] === ' ' ? '(공백)' : SPECIALS[i] }}</span>
        </div>
      </div>
    </section>

    <!-- C. 크기 스윕 -->
    <section v-if="shows('C')" class="no-print">
      <h2>C. 크기를 바꿔도 위치가 유지되는가</h2>
      <p class="note">
        점선 = 상수(<code>hangul-metrics.ts</code>)가 말하는 초/중/종 영역.
        어느 크기에서든 글자가 같은 선 안에 들어가야 한다.
      </p>
      <div class="strip bottom">
        <div v-for="s in SIZES" :key="s" class="sweep">
          <div class="guided" :style="{ width: `${s}px`, height: `${s}px` }">
            <HangulCell :glyph="sweepGlyph" :size="s" />
            <span class="g cho" :style="guides.cho" />
            <span class="g jung" :style="guides.jung" />
            <span class="g jong" :style="guides.jong" />
          </div>
          <span class="cap">{{ s }}px</span>
        </div>
      </div>
    </section>

    <!-- D. 가리기 -->
    <section v-if="shows('D')" class="no-print">
      <h2>D. 자모 가리기 4모드 <small>글자 '값'</small></h2>
      <div class="strip">
        <div v-for="m in HIDE_MODES" :key="m" class="pair">
          <div class="cell-box">
            <HangulCell :glyph="sweepGlyph" :size="REF_BOX" :hide-rule="ruleFor(m)" />
          </div>
          <span class="cap">{{ m }}</span>
        </div>
        <div class="pair">
          <div class="cell-box">
            <HangulCell :glyph="sweepGlyph" :size="REF_BOX" :hide-rule="EMPTY_HIDE_RULE" />
          </div>
          <span class="cap">가리기 없음</span>
        </div>
      </div>
    </section>

    <!-- G. 학습지 전체 대조 (원본 렌더러 vs 새 구현) -->
    <section v-if="shows('G')" class="no-print">
      <h2>G. 학습지 한 문항 전체 — 원본 vs 새 구현</h2>
      <p class="note">
        칸 하나가 아니라 <b>종이 위 모습</b>을 본다. 원본은 칸 100px·줄높이 170px,
        새 구현은 칸 92px 이므로 크기 자체는 다르다. 볼 것은
        <b>원고지 칸과 글자가 서로 맞는지</b>와 <b>특수문자·숫자가 칸을 넘치는지</b>다.
      </p>

      <div v-for="(it, si) in COMPARE_SENTENCES" :key="si" class="compare">
        <div class="side">
          <span class="side-tag">jammin 원본</span>
          <JamminSheet :glyphs="glyphs(it)" :text="it" />
        </div>
        <div class="side">
          <span class="side-tag">새 구현</span>
          <WorksheetItemRow
            :item="{ id: `cmp-${si}`, text: it, glyphs: glyphs(it) }"
            :index="si"
            :hide-rule="EMPTY_HIDE_RULE"
            :editable="false"
          />
        </div>
      </div>
    </section>

    <!-- E. 실제 학습지 (인쇄 대상) -->
    <section v-if="shows('E')" class="sheet">
      <h2 class="no-print">E. 실제 학습지 — 인쇄 대상</h2>
      <div class="paper">
        <h3 class="sheet-title">받아쓰기 급수표</h3>
        <WorksheetItemRow
          :item="sentenceItem"
          :index="0"
          :hide-rule="EMPTY_HIDE_RULE"
          :editable="false"
        />
        <WorksheetItemRow
          :item="sentenceItem2"
          :index="1"
          :hide-rule="EMPTY_HIDE_RULE"
          :editable="false"
        />
        <WorksheetItemRow
          :item="sentenceItem2"
          :index="2"
          :hide-rule="ruleFor('jong')"
          :editable="false"
        />
      </div>
    </section>

    <!-- I. 점수 추이 차트 -->
    <section v-if="shows('I')" class="no-print">
      <h2>I. 점수 추이 차트 — 라벨이 겹치지 않는가</h2>
      <p class="note">
        y축 눈금(100/50/0)과 직접 라벨이 부딪히기 쉬운 경우들. 숫자가 서로 겹치거나
        플롯 밖으로 잘려 나가면 안 된다.
      </p>
      <div class="charts">
        <figure v-for="c in CHART_CASES" :key="c.name" class="chart-case">
          <figcaption>{{ c.name }}</figcaption>
          <ScoreTrendChart :points="c.points" />
        </figure>
      </div>
    </section>

    <!-- H. 서버 화면 -->
    <section v-if="shows('H')" class="no-print">
      <h2>H. 서버가 필요한 화면</h2>
      <ServerScreens v-bind="serverArgs" />
    </section>

    <!-- F. 수치 요약 -->
    <section v-if="shows('F')" class="no-print">
      <h2>F. 수치 대조</h2>
      <table class="nums">
        <thead>
          <tr><th>항목</th><th>jammin 원본(px)</th><th>새 구현(상자 93 환산)</th><th>차이</th></tr>
        </thead>
        <tbody>
          <tr>
            <td>초성 높이</td>
            <td>{{ JAMMIN.editor.cho.height }}</td>
            <td>{{ ((CANVAS.cho.h / BOX) * REF_BOX).toFixed(2) }}</td>
            <td>{{ (((CANVAS.cho.h / BOX) * REF_BOX) - JAMMIN.editor.cho.height).toFixed(2) }}</td>
          </tr>
          <tr>
            <td>중성 높이</td>
            <td>{{ JAMMIN.editor.jung.height }}</td>
            <td>{{ ((CANVAS.jung.h / BOX) * REF_BOX).toFixed(2) }}</td>
            <td>{{ (((CANVAS.jung.h / BOX) * REF_BOX) - JAMMIN.editor.jung.height).toFixed(2) }}</td>
          </tr>
          <tr>
            <td>종성 높이</td>
            <td>{{ JAMMIN.editor.jong.height }}</td>
            <td>{{ ((CANVAS.jong.h / BOX) * REF_BOX).toFixed(2) }}</td>
            <td>{{ (((CANVAS.jong.h / BOX) * REF_BOX) - JAMMIN.editor.jong.height).toFixed(2) }}</td>
          </tr>
          <tr>
            <td>종성 top</td>
            <td>{{ JAMMIN.editor.jong.top }}</td>
            <td>{{ ((JONG.y / BOX) * REF_BOX).toFixed(2) }}</td>
            <td>{{ (((JONG.y / BOX) * REF_BOX) - JAMMIN.editor.jong.top).toFixed(2) }}</td>
          </tr>
          <tr>
            <td>중성 top</td>
            <td>{{ JAMMIN.editor.jung.top }}</td>
            <td>{{ ((JUNG.y / BOX) * REF_BOX).toFixed(2) }}</td>
            <td>0</td>
          </tr>
        </tbody>
      </table>
    </section>
  </main>
</template>

<style scoped>
.tint-defs {
  position: absolute;
}

.verify {
  padding: 20px 24px 60px;
  font-family: system-ui, -apple-system, sans-serif;
  color: #1e293b;
  background: #fff;
}

h1 {
  margin: 0 0 4px;
  font-size: 20px;
}

h2 {
  margin: 28px 0 8px;
  font-size: 16px;
  padding-bottom: 4px;
  border-bottom: 1px solid #e2e8f0;
}

h2 small {
  font-weight: 400;
  color: #64748b;
}

h3 {
  margin: 16px 0 6px;
  font-size: 14px;
  color: #475569;
}

.head p,
.note {
  margin: 4px 0 10px;
  font-size: 13px;
  color: #475569;
  max-width: 76ch;
  line-height: 1.6;
}

.red {
  color: #d91d24;
}

.blue {
  color: #1752e0;
}

.opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #475569;
}

code {
  padding: 1px 4px;
  border-radius: 4px;
  background: #f1f5f9;
  font-size: 12px;
}

.strip {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  align-items: flex-start;
}

.strip.bottom {
  align-items: flex-end;
}

/* --- 겹침 --- */
.overlay {
  position: relative;
  outline: 1px solid #cbd5e1;
}

.layer {
  position: absolute;
  inset: 0;
}

.layer.ref {
  filter: url(#tintRed);
  opacity: 0.62;
}

.layer.mine {
  filter: url(#tintBlue);
  opacity: 0.62;
  mix-blend-mode: multiply;
}

.overlay-item,
.sweep,
.pair {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.pair {
  flex-direction: row;
  align-items: flex-start;
  gap: 6px;
  position: relative;
  padding-bottom: 16px;
}

.cell-box {
  position: relative;
  outline: 1px dashed #cbd5e1;
  width: 93px;
  height: 93px;
}

.tag {
  position: absolute;
  left: 0;
  bottom: -15px;
  font-size: 10px;
  color: #94a3b8;
}

.cap {
  font-size: 12px;
  color: #64748b;
}

.pair > .cap {
  position: absolute;
  right: 0;
  bottom: -2px;
  font-weight: 600;
  color: #334155;
}

/* --- 크기 스윕 가이드 --- */
.guided {
  position: relative;
  outline: 1px solid #e2e8f0;
}

.g {
  position: absolute;
  pointer-events: none;
}

.g.cho {
  outline: 1px dashed #e11d48;
}

.g.jung {
  outline: 1px dashed #0ea5e9;
}

.g.jong {
  outline: 1px dashed #16a34a;
}

/* --- 학습지 --- */
.paper {
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  max-width: 820px;
}

.sheet-title {
  margin: 0 0 16px;
  font-family: var(--font-hand), system-ui, sans-serif;
  font-size: 24px;
  color: #1e293b;
}

/* --- 차트 케이스 --- */
.charts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 18px;
  max-width: 1000px;
}

.chart-case {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.chart-case > figcaption {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

/* --- 학습지 전체 대조 --- */
.compare {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 22px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.side {
  position: relative;
  padding-top: 14px;
  overflow-x: auto;
}

.side-tag {
  position: absolute;
  top: 0;
  left: 0;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}

/* --- 수치표 --- */
.nums {
  border-collapse: collapse;
  font-size: 13px;
}

.nums th,
.nums td {
  padding: 5px 12px;
  border: 1px solid #e2e8f0;
  text-align: right;
}

.nums th:first-child,
.nums td:first-child {
  text-align: left;
}

.nums thead th {
  background: #f8fafc;
}

/* 인쇄하면 학습지만 남는다 — 인쇄 검증용 */
@media print {
  .no-print {
    display: none !important;
  }

  .verify {
    padding: 0;
  }

  .paper {
    border: none;
    padding: 0;
    max-width: none;
  }
}
</style>
