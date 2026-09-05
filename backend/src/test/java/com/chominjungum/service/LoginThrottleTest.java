package com.chominjungum.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 로그인 무차별 대입을 늦춘다.
 *
 * <p>계정 잠금이 아니라 **지연**인 것이 핵심이다 — 잠금이면 남의 이메일로 반복 실패시켜
 * 그 교사를 못 들어오게 만드는 서비스 거부가 된다.
 */
class LoginThrottleTest {

    private static final Instant T0 = Instant.parse("2026-09-05T10:00:00Z");

    @Test
    void 몇_번_틀리는_것은_그냥_통과시킨다() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 4; i++) {
            t.recordFailure("a@b.kr", T0);
        }
        assertThat(t.blockedSeconds("a@b.kr", T0)).isZero();
    }

    @Test
    void 계속_틀리면_잠시_거부한다() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 5; i++) {
            t.recordFailure("a@b.kr", T0);
        }
        assertThat(t.blockedSeconds("a@b.kr", T0)).isPositive();
    }

    @Test
    void 시간이_지나면_저절로_풀린다() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 5; i++) {
            t.recordFailure("a@b.kr", T0);
        }
        assertThat(t.blockedSeconds("a@b.kr", T0.plus(Duration.ofMinutes(6)))).isZero();
    }

    @Test
    void 성공하면_기록이_지워진다() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 5; i++) {
            t.recordFailure("a@b.kr", T0);
        }
        t.recordSuccess("a@b.kr");
        assertThat(t.blockedSeconds("a@b.kr", T0)).isZero();
    }

    @Test
    void 한_계정이_막혀도_다른_교사는_들어올_수_있다() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 10; i++) {
            t.recordFailure("victim@b.kr", T0);
        }
        assertThat(t.blockedSeconds("victim@b.kr", T0)).isPositive();
        assertThat(t.blockedSeconds("other@b.kr", T0)).isZero();
    }

    @Test
    void 오래_조용하면_처음부터_다시_센다() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 5; i++) {
            t.recordFailure("a@b.kr", T0);
        }
        // 20분 뒤에 한 번 틀림 → 누적이 아니라 1회부터
        Instant later = T0.plus(Duration.ofMinutes(20));
        t.recordFailure("a@b.kr", later);
        assertThat(t.blockedSeconds("a@b.kr", later)).isZero();
    }

    @Test
    void 대소문자와_공백은_같은_계정으로_본다() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 5; i++) {
            t.recordFailure("  A@B.kr ", T0);
        }
        assertThat(t.blockedSeconds("a@b.kr", T0)).isPositive();
    }

    @Test
    void 오래된_기록은_정리된다() {
        LoginThrottle t = new LoginThrottle();
        t.recordFailure("a@b.kr", T0);
        t.evictExpired(T0.plus(Duration.ofMinutes(20)));
        assertThat(t.size()).isZero();
    }
}
