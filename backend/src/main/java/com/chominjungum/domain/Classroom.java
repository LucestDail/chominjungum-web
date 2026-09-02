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

@Entity
@Table(name = "classroom")
@Getter
@Setter
@NoArgsConstructor
public class Classroom {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(nullable = false)
    private String name;

    private Integer grade;

    @Column(name = "school_year")
    private Integer schoolYear;

    /** 학생이 웹 응시에 입장할 때 쓰는 코드. 계정 없이 이것만으로 참여한다. */
    @Column(name = "join_code", nullable = false, unique = true)
    private String joinCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Classroom(UUID teacherId, String name, Integer grade, Integer schoolYear, String joinCode) {
        this.teacherId = teacherId;
        this.name = name;
        this.grade = grade;
        this.schoolYear = schoolYear;
        this.joinCode = joinCode;
    }
}
