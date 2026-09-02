package com.chominjungum.hangul;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Dart·TS 구현과 같은 기대치를 유지한다. */
class DictationCompareTest {

    @Test
    void 완전_일치() {
        var r = DictationCompare.score("가나", "가나");
        assertThat(r.correctCount()).isEqualTo(2);
        assertThat(r.scorePercent()).isEqualTo(100);
    }

    @Test
    void 부분_일치() {
        var r = DictationCompare.score("안녕하세요", "안녕하세오");
        assertThat(r.correctCount()).isEqualTo(4);
        assertThat(r.totalCount()).isEqualTo(5);
        assertThat(r.scorePercent()).isEqualTo(80);
        assertThat(r.matches().get(4).mismatchReason()).contains("글자 불일치");
    }

    @Test
    void 받침_차이를_잡는다() {
        var r = DictationCompare.score("간", "가");
        assertThat(r.correctCount()).isZero();
    }

    @Test
    void 답이_짧으면_누락() {
        var r = DictationCompare.score("안녕하세요", "안녕");
        assertThat(r.correctCount()).isEqualTo(2);
        assertThat(r.matches().get(2).mismatchReason()).isEqualTo("글자 누락");
    }

    @Test
    void 초과_입력은_별도_match() {
        var r = DictationCompare.score("가", "가나다");
        assertThat(r.totalCount()).isEqualTo(1);
        assertThat(r.hasExtraInput()).isTrue();
        assertThat(r.matches()).hasSize(3);
        assertThat(r.matches().get(1).mismatchReason()).isEqualTo("불필요한 글자");
    }

    @Test
    void 특수문자와_숫자도_채점한다() {
        var r = DictationCompare.score("1반!", "1반?");
        assertThat(r.correctCount()).isEqualTo(2);
        assertThat(r.matches().get(2).mismatchReason()).contains("기호·숫자 불일치");
    }

    @Test
    void 허용_밖_문자는_오류() {
        assertThat(DictationCompare.score("abc", "가").error()).contains("정답이");
        assertThat(DictationCompare.score("가", "abc").error()).contains("답안이");
    }

    @Test
    void 요약은_인덱스와_정오만_남긴다() {
        var summaries = DictationCompare.score("가나", "가다").matchSummaries();
        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0)).containsEntry("i", 0).containsEntry("ok", true);
        assertThat(summaries.get(1)).containsEntry("ok", false).containsKey("why");
    }
}
