package com.chominjungum.repo;

import com.chominjungum.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictationItemRepository extends JpaRepository<DictationItem, UUID> {
    List<DictationItem> findByOwnerTeacherIdOrderByCreatedAtDesc(UUID ownerTeacherId);

    Optional<DictationItem> findByOwnerTeacherIdAndContentHash(UUID ownerTeacherId, String contentHash);
}
