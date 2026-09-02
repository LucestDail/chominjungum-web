package com.chominjungum.repo;

import com.chominjungum.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {
    List<Classroom> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);

    Optional<Classroom> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);
}
