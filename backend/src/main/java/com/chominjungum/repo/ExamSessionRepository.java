package com.chominjungum.repo;

import com.chominjungum.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
    List<ExamSession> findByClassroomIdOrderByCreatedAtDesc(UUID classroomId);
}
