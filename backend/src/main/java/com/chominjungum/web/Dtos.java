package com.chominjungum.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** 요청·응답 DTO 모음. */
public final class Dtos {

    private Dtos() {
    }

    // ── 인증 ────────────────────────────────────────────────
    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.") String password,
            @NotBlank String displayName) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record JoinRequest(@NotBlank String joinCode, @NotBlank String displayName) {
    }

    public record TokenResponse(String token, String role, UUID id, String displayName) {
    }

    // ── 학급 ────────────────────────────────────────────────
    public record ClassroomRequest(@NotBlank String name, Integer grade, Integer schoolYear) {
    }

    public record ClassroomResponse(
            UUID id, String name, Integer grade, Integer schoolYear, String joinCode) {
    }

    public record StudentRequest(@NotBlank String displayName, Integer studentNo) {
    }

    public record StudentResponse(UUID id, String displayName, Integer studentNo) {
    }

    public record BindDeviceRequest(@NotBlank String deviceBindingId, UUID studentId) {
    }

    public record BindDeviceResponse(String deviceBindingId, UUID studentId, int backfilled) {
    }

    // ── 문항 ────────────────────────────────────────────────
    public record ItemRequest(@NotBlank String expectedText, Integer grade, String unit) {
    }

    public record ItemResponse(
            UUID id, String expectedText, int glyphCount, Integer grade, String unit) {
    }

    // ── 과제 ────────────────────────────────────────────────
    public record AssignmentRequest(
            UUID classroomId,
            @NotBlank String title,
            String mode,
            @NotEmpty(message = "문항을 하나 이상 선택하세요.") List<UUID> itemIds) {
    }

    public record AssignmentResponse(
            UUID id, UUID classroomId, String title, String mode, boolean open, int itemCount) {
    }

    // ── 응시 ────────────────────────────────────────────────
    /** 받아쓰기라서 문제 텍스트를 내려주지 않는다. 교사가 읽어주고 학생은 듣고 쓴다. */
    public record ExamItemResponse(UUID itemId, int orderNo, int glyphCount) {
    }

    public record ExamStartResponse(
            UUID assignmentId, String title, List<ExamItemResponse> items) {
    }

    public record AttemptRequest(
            UUID itemId, @NotBlank String rawAnswer, String inputKind) {
    }

    public record GlyphMatchResponse(int index, boolean correct, String why) {
    }

    public record AttemptResponse(
            UUID attemptId,
            UUID itemId,
            String expectedText,
            String rawAnswer,
            int correctCount,
            int totalCount,
            int scorePercent,
            List<GlyphMatchResponse> matches) {
    }
}
