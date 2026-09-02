package com.chominjungum.repo;

import com.chominjungum.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentItemRepository extends JpaRepository<AssignmentItem, AssignmentItem.Key> {
    List<AssignmentItem> findByIdAssignmentIdOrderByOrderNoAsc(UUID assignmentId);
}
