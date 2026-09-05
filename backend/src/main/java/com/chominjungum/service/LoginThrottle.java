package com.chominjungum.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 로그인 시도 제한 — 비밀번호 무차별 대입을 늦춘다.
 *
 * <p>BCrypt 가 느려서 초당 시도 수는 이미 제한되지만, 비밀번호 정책이 최소 8자뿐이라
 * 시간만 주면 약한 비밀번호는 결국 뚫린다. 교실 단위로 쓰는 동안은 위험이 낮지만
 * 학교 단위로 열면 필요하다.
 *
 * <p><b>계정 잠금이 아니라 지연을 쓴다.</b> 잠금은 남의 이메일로 반복 실패시켜
 * 그 교사를 못 들어오게 만드는 서비스 거부가 된다. 여기서는 같은 이메일에 대한
 * 실패가 쌓이면 잠시 거부하고, 시간이 지나면 저절로 풀린다.
 *
 * <p>단일 인스턴스 메모리 기준이다. 서버를 여러 대로 늘리면 공유 저장소(Redis 등)가
 * 필요하지만, 지금은 systemd 단일 프로세스라 이것으로 충분하다.
 */
@Component
public class LoginThrottle {

    /** 이 횟수까지는 그냥 틀린 것으로 본다(오타·기억 안 남). */
    static final int FREE_ATTEMPTS = 5;

    /** 초과하면 이 시간 동안 거부한다. */
    static final Duration BLOCK = Duration.ofMinutes(5);

    /** 마지막 실패 후 이만큼 지나면 처음부터 다시 센다. */
    static final Duration RESET_AFTER = Duration.ofMinutes(15);

    private final Map<String, Attempts> byKey = new ConcurrentHashMap<>();

    private record Attempts(int count, Instant lastFailure) {}

    /** 지금 시도해도 되는가. 막혀 있으면 남은 시간(초)을, 아니면 0 을 돌려준다. */
    public long blockedSeconds(String key, Instant now) {
        Attempts a = byKey.get(normalize(key));
        if (a == null || a.count() < FREE_ATTEMPTS) {
            return 0;
        }
        if (Duration.between(a.lastFailure(), now).compareTo(RESET_AFTER) >= 0) {
            return 0; // 오래 조용했으면 잊는다
        }
        long remain = BLOCK.getSeconds() - Duration.between(a.lastFailure(), now).getSeconds();
        return Math.max(remain, 0);
    }

    public void recordFailure(String key, Instant now) {
        byKey.compute(normalize(key), (k, prev) -> {
            if (prev == null || Duration.between(prev.lastFailure(), now).compareTo(RESET_AFTER) >= 0) {
                return new Attempts(1, now);
            }
            return new Attempts(prev.count() + 1, now);
        });
    }

    /** 로그인에 성공하면 기록을 지운다. */
    public void recordSuccess(String key) {
        byKey.remove(normalize(key));
    }

    /** 오래된 기록이 쌓이지 않게 정리한다(호출하는 쪽이 가끔 부르면 된다). */
    public void evictExpired(Instant now) {
        byKey.entrySet().removeIf(e ->
                Duration.between(e.getValue().lastFailure(), now).compareTo(RESET_AFTER) >= 0);
    }

    int size() {
        return byKey.size();
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }
}
