package com.chominjungum.hangul;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * jammin 실응답으로 채집한 골든 벡터와의 동치성.
 *
 * <p>같은 로직이 Java(여기) · Dart(앱) · TS(웹) 세 곳에 있다. 하나라도 어긋나면
 * 교실 모드와 온라인 모드의 채점이 달라진다.
 */
class HangulGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path GOLDEN = Path.of("..", "golden", "hangul-split.json");

    record Case(String input, List<Map<String, Object>> expected) {
    }

    static Stream<Case> cases() throws Exception {
        assertThat(Files.exists(GOLDEN))
                .as("골든 파일이 있어야 한다: %s", GOLDEN.toAbsolutePath())
                .isTrue();
        Map<String, Object> doc = MAPPER.readValue(Files.readString(GOLDEN), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) doc.get("cases");
        return raw.stream().map(c -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> expected = (List<Map<String, Object>>) c.get("expected");
            return new Case((String) c.get("input"), expected);
        });
    }

    @Test
    void 골든_파일이_비어있지_않다() throws Exception {
        assertThat(cases().count()).isGreaterThan(0);
    }

    @ParameterizedTest(name = "addWord({0})")
    @MethodSource("cases")
    void jammin_응답과_같다(Case c) {
        List<Map<String, Object>> actual = HangulUtil.addWord(c.input()).stream()
                .map(Hangul::toMap)
                .toList();

        assertThat(actual).hasSameSizeAs(c.expected());
        for (int i = 0; i < actual.size(); i++) {
            // 키 순서는 무관하나 키의 존재/부재와 값은 같아야 한다.
            assertThat(actual.get(i))
                    .as("%s 의 %d번째 글자", c.input(), i)
                    .containsExactlyInAnyOrderEntriesOf(normalize(c.expected().get(i)));
        }
    }

    /** 골든 JSON 의 숫자는 Integer 로 읽히므로 그대로 두고, 값 타입만 맞춘다. */
    private static Map<String, Object> normalize(Map<String, Object> expected) {
        return expected;
    }
}
