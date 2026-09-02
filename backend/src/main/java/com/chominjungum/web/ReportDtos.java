package com.chominjungum.web;

import java.util.List;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    /** 학급 성적 요약의 한 줄. */
    public record StudentRow(
            UUID studentId,
            String displayName,
            int attemptCount,
            int averagePercent,
            int perfectCount) {
    }

    /** 문항별 정답률 — 쌓이면 그대로 난이도 지표가 된다. */
    public record ItemRow(
            UUID itemId,
            String expectedText,
            int attemptCount,
            int averagePercent) {
    }

    public record ClassroomReport(
            UUID classroomId,
            String classroomName,
            int studentCount,
            int attemptCount,
            int averagePercent,
            List<StudentRow> students,
            List<ItemRow> items,
            int unassignedDeviceCount) {
    }

    /** 자주 틀리는 자모. 초성/중성/종성을 나눠서 본다. */
    public record WeakJamo(String kind, int code, String label, int missCount) {
    }

    public record AttemptRow(
            UUID attemptId,
            String expectedText,
            String rawAnswer,
            int correctCount,
            int totalCount,
            int scorePercent,
            long submittedAtMs) {
    }

    public record StudentReport(
            UUID studentId,
            String displayName,
            int attemptCount,
            int averagePercent,
            List<AttemptRow> attempts,
            List<WeakJamo> weakJamos) {
    }
}
