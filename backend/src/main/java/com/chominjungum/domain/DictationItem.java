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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 받아쓰기 문항. jammin 분해 결과(glyphs)를 함께 보관해 클라이언트가 다시 분해하지 않아도 되게 한다. */
@Entity
@Table(name = "dictation_item")
@Getter
@Setter
@NoArgsConstructor
public class DictationItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_teacher_id", nullable = false)
    private UUID ownerTeacherId;

    @Column(name = "expected_text", nullable = false)
    private String expectedText;

    /** jammin addWord 응답 형식 그대로의 JSON 배열. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "glyphs_json", nullable = false)
    private String glyphsJson;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    private Integer grade;

    private String unit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public DictationItem(UUID ownerTeacherId, String expectedText, String glyphsJson, String contentHash) {
        this.ownerTeacherId = ownerTeacherId;
        this.expectedText = expectedText;
        this.glyphsJson = glyphsJson;
        this.contentHash = contentHash;
    }
}
