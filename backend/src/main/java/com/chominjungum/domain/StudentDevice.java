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
 * 교실 LAN 제출은 익명 기기 ID 로 들어온다. 교사가 명단과 연결하면 이후 제출이 자동 매핑된다.
 */
@Entity
@Table(name = "student_device")
@Getter
@Setter
@NoArgsConstructor
public class StudentDevice {

    @Id
    @Column(name = "device_binding_id")
    private String deviceBindingId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "bound_at", nullable = false)
    private Instant boundAt = Instant.now();

    public StudentDevice(String deviceBindingId, UUID studentId) {
        this.deviceBindingId = deviceBindingId;
        this.studentId = studentId;
    }
}
