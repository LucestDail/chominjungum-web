package com.chominjungum.security;

import java.util.UUID;

/**
 * 인증 주체. 교사면 {@code id} 가 teacherId, 학생이면 studentId 다.
 */
public record AuthPrincipal(UUID id, String role, UUID classroomId) {

    public boolean isTeacher() {
        return JwtService.ROLE_TEACHER.equals(role);
    }

    public boolean isStudent() {
        return JwtService.ROLE_STUDENT.equals(role);
    }
}
