package com.chominjungum.repo;

import com.chominjungum.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {
    List<Attempt> findBySessionId(UUID sessionId);

    Optional<Attempt> findBySessionIdAndDeviceBindingIdAndItemId(
            UUID sessionId, String deviceBindingId, UUID itemId);

    List<Attempt> findByStudentId(UUID studentId);

    List<Attempt> findBySessionIdIn(List<UUID> sessionIds);

    List<Attempt> findByDeviceBindingId(String deviceBindingId);
}
