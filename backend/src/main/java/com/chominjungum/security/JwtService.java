package com.chominjungum.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 토큰 두 종류를 발급한다.
 * <ul>
 *   <li><b>교사</b> — 계정 로그인. 학급·문항·성적을 다룬다.</li>
 *   <li><b>학생</b> — 계정 없이 참여코드로 입장. 응시에만 쓰이고 수명이 짧다(PLAN §6.1).</li>
 * </ul>
 */
@Service
public class JwtService {

    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_STUDENT = "STUDENT";

    private final SecretKey key;
    private final Duration teacherTtl;
    private final Duration studentTtl;

    public JwtService(
            @Value("${chominjungum.jwt.secret}") String secret,
            @Value("${chominjungum.jwt.teacher-ttl:PT12H}") Duration teacherTtl,
            @Value("${chominjungum.jwt.student-ttl:PT4H}") Duration studentTtl) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "chominjungum.jwt.secret 은 최소 32바이트여야 합니다 (현재 " + bytes.length + ")");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.teacherTtl = teacherTtl;
        this.studentTtl = studentTtl;
    }

    public String issueTeacherToken(UUID teacherId) {
        return build(teacherId.toString(), Map.of("role", ROLE_TEACHER), teacherTtl);
    }

    public String issueStudentToken(UUID studentId, UUID classroomId) {
        return build(
                studentId.toString(),
                Map.of("role", ROLE_STUDENT, "classroomId", classroomId.toString()),
                studentTtl);
    }

    private String build(String subject, Map<String, String> claims, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** 서명·만료를 검증하고 주체 정보를 돌려준다. 실패하면 null. */
    public AuthPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String role = claims.get("role", String.class);
            UUID subject = UUID.fromString(claims.getSubject());
            String classroomId = claims.get("classroomId", String.class);
            return new AuthPrincipal(
                    subject, role, classroomId == null ? null : UUID.fromString(classroomId));
        } catch (Exception e) {
            return null;
        }
    }
}
