package com.chominjungum.service;

import com.chominjungum.domain.Classroom;
import com.chominjungum.domain.Student;
import com.chominjungum.domain.Teacher;
import com.chominjungum.repo.ClassroomRepository;
import com.chominjungum.repo.StudentRepository;
import com.chominjungum.repo.TeacherRepository;
import com.chominjungum.web.ApiException;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** 헷갈리는 글자(0/O, 1/I)를 뺀 참여코드 알파벳. 초등학생이 보고 입력한다. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TeacherRepository teachers;
    private final ClassroomRepository classrooms;
    private final StudentRepository students;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            TeacherRepository teachers,
            ClassroomRepository classrooms,
            StudentRepository students,
            PasswordEncoder passwordEncoder) {
        this.teachers = teachers;
        this.classrooms = classrooms;
        this.students = students;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Teacher register(String email, String rawPassword, String displayName) {
        if (teachers.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }
        return teachers.save(new Teacher(email, passwordEncoder.encode(rawPassword), displayName));
    }

    public Teacher login(String email, String rawPassword) {
        Teacher teacher = teachers.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));
        if (!passwordEncoder.matches(rawPassword, teacher.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return teacher;
    }

    /**
     * 학생 입장 — 계정을 만들지 않는다. 같은 학급에 같은 표시명이 있으면 그 학생으로 잇고,
     * 없으면 명단에 새로 추가한다.
     */
    @Transactional
    public Student join(String joinCode, String displayName) {
        Classroom classroom = classrooms.findByJoinCode(joinCode.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "참여코드를 찾을 수 없습니다."));

        String name = displayName.trim();
        if (name.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "이름을 입력하세요.");
        }

        List<Student> roster = students.findByClassroomIdOrderByStudentNoAscDisplayNameAsc(classroom.getId());
        return roster.stream()
                .filter(s -> s.getDisplayName().equals(name))
                .findFirst()
                .orElseGet(() -> students.save(new Student(classroom.getId(), name, null)));
    }

    /** 학급마다 유일한 참여코드를 만든다. */
    public String generateJoinCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!classrooms.existsByJoinCode(code)) {
                return code;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "참여코드를 생성하지 못했습니다.");
    }
}
