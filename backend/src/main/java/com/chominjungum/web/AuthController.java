package com.chominjungum.web;

import com.chominjungum.domain.Student;
import com.chominjungum.domain.Teacher;
import com.chominjungum.security.JwtService;
import com.chominjungum.service.AuthService;
import com.chominjungum.web.Dtos.JoinRequest;
import com.chominjungum.web.Dtos.LoginRequest;
import com.chominjungum.web.Dtos.RegisterRequest;
import com.chominjungum.web.Dtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/teacher/register")
    public TokenResponse register(@Valid @RequestBody RegisterRequest req) {
        Teacher teacher = authService.register(req.email(), req.password(), req.displayName());
        return new TokenResponse(
                jwtService.issueTeacherToken(teacher.getId()),
                JwtService.ROLE_TEACHER,
                teacher.getId(),
                teacher.getDisplayName());
    }

    @PostMapping("/teacher/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        Teacher teacher = authService.login(req.email(), req.password());
        return new TokenResponse(
                jwtService.issueTeacherToken(teacher.getId()),
                JwtService.ROLE_TEACHER,
                teacher.getId(),
                teacher.getDisplayName());
    }

    /** 학생 입장 — 계정 없이 참여코드 + 표시명만. */
    @PostMapping("/join")
    public TokenResponse join(@Valid @RequestBody JoinRequest req) {
        Student student = authService.join(req.joinCode(), req.displayName());
        return new TokenResponse(
                jwtService.issueStudentToken(student.getId(), student.getClassroomId()),
                JwtService.ROLE_STUDENT,
                student.getId(),
                student.getDisplayName());
    }
}
