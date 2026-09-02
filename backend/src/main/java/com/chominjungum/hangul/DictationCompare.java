package com.chominjungum.hangul;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 받아쓰기 채점 — Dart({@code hangul_core}) · TS({@code hangul-core-ts}) 구현과 동일 규칙.
 *
 * <p>서버는 학생이 보낸 점수를 믿지 않고 이 클래스로 <b>재채점</b>한다(PLAN §8.2).
 */
public final class DictationCompare {

    private DictationCompare() {
    }

    public record GlyphMatch(int index, boolean correct, String mismatchReason) {
        public Map<String, Object> toSummary() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("i", index);
            map.put("ok", correct);
            if (mismatchReason != null) {
                map.put("why", mismatchReason);
            }
            return map;
        }
    }

    public record Result(
            int correctCount,
            int totalCount,
            double ratio,
            List<GlyphMatch> matches,
            String error,
            boolean hasExtraInput) {

        public int scorePercent() {
            return (int) Math.round(ratio * 100);
        }

        public List<Map<String, Object>> matchSummaries() {
            return matches.stream().map(GlyphMatch::toSummary).toList();
        }
    }

    public static Result score(String expected, String actual) {
        if (!HangulUtil.isHangul(expected)) {
            return new Result(0, 0, 0, List.of(), "정답이 jammin 허용 문자 집합이 아닙니다.", false);
        }
        if (!HangulUtil.isHangul(actual)) {
            return new Result(
                    0, HangulUtil.hangulSplit(expected).size(), 0, List.of(),
                    "답안이 jammin 허용 문자 집합이 아닙니다.", false);
        }

        List<Hangul> e = HangulUtil.hangulSplit(expected);
        List<Hangul> a = HangulUtil.hangulSplit(actual);
        List<GlyphMatch> matches = new ArrayList<>();
        int correct = 0;

        for (int i = 0; i < e.size(); i++) {
            Hangul exp = e.get(i);
            Hangul act = i < a.size() ? a.get(i) : null;
            boolean ok = glyphEquals(exp, act);
            if (ok) {
                correct++;
            }
            matches.add(new GlyphMatch(i, ok, ok ? null : reason(exp, act)));
        }

        for (int i = e.size(); i < a.size(); i++) {
            matches.add(new GlyphMatch(i, false, "불필요한 글자"));
        }

        int total = e.size();
        double ratio = total == 0 ? 1.0 : (double) correct / total;
        return new Result(correct, total, ratio, matches, null, a.size() > e.size());
    }

    private static boolean glyphEquals(Hangul e, Hangul a) {
        if (a == null) {
            return false;
        }
        if (!Objects.equals(e.getSpecialFlag(), a.getSpecialFlag())) {
            return false;
        }
        if (Boolean.TRUE.equals(e.getSpecialFlag())) {
            return Objects.equals(e.getSpecialType(), a.getSpecialType());
        }
        if (!Objects.equals(e.getErrorFlag(), a.getErrorFlag())) {
            return false;
        }
        return Objects.equals(e.getWord(), a.getWord())
                && e.getChoCode() == a.getChoCode()
                && e.getJungCode() == a.getJungCode()
                && e.getJongCode() == a.getJongCode();
    }

    private static String reason(Hangul e, Hangul a) {
        if (a == null) {
            return "글자 누락";
        }
        if (!Objects.equals(e.getSpecialFlag(), a.getSpecialFlag())) {
            return "문자 종류 불일치(한글/특수·숫자)";
        }
        if (Boolean.TRUE.equals(e.getSpecialFlag())) {
            return "기호·숫자 불일치: \"" + e.getSpecialType() + "\" vs \"" + a.getSpecialType() + "\"";
        }
        if (!Objects.equals(e.getWord(), a.getWord())) {
            return "글자 불일치: \"" + e.getWord() + "\" vs \"" + a.getWord() + "\"";
        }
        return "자모 불일치";
    }
}
