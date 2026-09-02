package com.chominjungum.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 교실 LAN 수업 결과를 서버로 올리는 배치 형식.
 * 상세 규약은 docs/sync-protocol.md.
 */
public final class SyncDtos {

    private SyncDtos() {
    }

    /** 교사앱이 보관한 문항. 서버 UUID 를 모르므로 앱의 로컬 id 와 정답 문장을 함께 보낸다. */
    public record SyncItem(
            @NotBlank String id,
            @NotBlank String expectedText) {
    }

    public record SyncMatch(int i, boolean ok, String why) {
    }

    public record SyncAttempt(
            @NotNull UUID attemptId,
            @NotBlank String itemId,
            @NotBlank String deviceBindingId,
            String rawAnswer,
            Integer correctCount,
            Integer totalCount,
            String inputKind,
            @NotNull Long submittedAtMs,
            List<SyncMatch> matches) {
    }

    public record SyncSessionRequest(
            @NotNull UUID sessionId,
            @NotNull UUID classroomId,
            String source,
            Long startedAtMs,
            Long endedAtMs,
            List<SyncItem> items,
            List<SyncAttempt> attempts) {
    }

    public record RejectedAttempt(UUID attemptId, String reason) {
    }

    public record SyncSessionResponse(
            UUID sessionId,
            int accepted,
            int duplicated,
            List<RejectedAttempt> rejected,
            List<String> unassignedDevices) {
    }
}
