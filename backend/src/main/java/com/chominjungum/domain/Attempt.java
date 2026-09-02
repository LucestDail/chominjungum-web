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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 학생 답안 한 건. {@code id} 는 클라이언트가 만든 attemptId 이며 <b>멱등 키</b>다
 * — 같은 배치를 여러 번 업싱크해도 중복되지 않는다(docs/sync-protocol.md §4).
 */
@Entity
@Table(name = "attempt")
@Getter
@Setter
@NoArgsConstructor
public class Attempt {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** 명단 매핑 전에는 null 이다. 매핑되면 소급 채워진다. */
    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "device_binding_id")
    private String deviceBindingId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "raw_answer", nullable = false)
    private String rawAnswer;

    /** 서버가 재채점한 값. 클라이언트가 보낸 점수는 신뢰하지 않는다. */
    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "input_kind", nullable = false)
    private String inputKind = "keyboard";

    /** 글자별 정오 요약 — 취약 자모 분석의 원천. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "glyph_matches")
    private String glyphMatches;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public int scorePercent() {
        return totalCount == 0 ? 0 : Math.round((float) correctCount * 100 / totalCount);
    }
}
