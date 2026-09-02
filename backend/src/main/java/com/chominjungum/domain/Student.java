package com.chominjungum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 학생은 계정이 아니다. 교실 표시명(예: "3번", "김OO")과 번호만 둔다 — PLAN §6.1 개인정보 원칙.
 */
@Entity
@Table(name = "student")
@Getter
@Setter
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "classroom_id", nullable = false)
    private UUID classroomId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "student_no")
    private Integer studentNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Student(UUID classroomId, String displayName, Integer studentNo) {
        this.classroomId = classroomId;
        this.displayName = displayName;
        this.studentNo = studentNo;
    }
}
