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
@Table(name = "assignment")
@Getter
@Setter
@NoArgsConstructor
public class Assignment {

    /** ONLINE = 웹 응시, CLASSROOM = 교실 LAN 수업(업싱크로 채워짐) */
    public enum Mode {
        ONLINE,
        CLASSROOM
    }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "classroom_id", nullable = false)
    private UUID classroomId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String mode = Mode.ONLINE.name();

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Assignment(UUID classroomId, String title, Mode mode) {
        this.classroomId = classroomId;
        this.title = title;
        this.mode = mode.name();
    }

    /** 지금 응시를 받을 수 있는가. */
    public boolean isOpen(Instant now) {
        if (openedAt == null || openedAt.isAfter(now)) {
            return false;
        }
        return closedAt == null || closedAt.isAfter(now);
    }
}
