package com.chominjungum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 응시 세션. 교실 모드에서는 <b>교사앱이 만든 허브 세션 ID 를 그대로</b> 받는다(업싱크 멱등의 축).
 */
@Entity
@Table(name = "exam_session")
@Getter
@Setter
@NoArgsConstructor
public class ExamSession {

    public enum Source {
        LAN,
        WEB
    }

    @Id
    private UUID id;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "classroom_id", nullable = false)
    private UUID classroomId;

    @Column(nullable = false)
    private String source;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ExamSession(UUID id, UUID classroomId, UUID assignmentId, Source source) {
        this.id = id;
        this.classroomId = classroomId;
        this.assignmentId = assignmentId;
        this.source = source.name();
    }
}
