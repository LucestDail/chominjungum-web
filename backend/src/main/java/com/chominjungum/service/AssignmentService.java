package com.chominjungum.service;

import com.chominjungum.domain.Assignment;
import com.chominjungum.domain.AssignmentItem;
import com.chominjungum.domain.DictationItem;
import com.chominjungum.repo.AssignmentItemRepository;
import com.chominjungum.repo.AssignmentRepository;
import com.chominjungum.repo.DictationItemRepository;
import com.chominjungum.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final AssignmentItemRepository assignmentItems;
    private final DictationItemRepository items;
    private final ClassroomService classroomService;
    private final ItemService itemService;

    public AssignmentService(
            AssignmentRepository assignments,
            AssignmentItemRepository assignmentItems,
            DictationItemRepository items,
            ClassroomService classroomService,
            ItemService itemService) {
        this.assignments = assignments;
        this.assignmentItems = assignmentItems;
        this.items = items;
        this.classroomService = classroomService;
        this.itemService = itemService;
    }

    @Transactional
    public Assignment create(
            UUID teacherId, UUID classroomId, String title, Assignment.Mode mode, List<UUID> itemIds) {
        classroomService.getOwned(teacherId, classroomId);
        if (itemIds == null || itemIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "문항을 하나 이상 선택하세요.");
        }

        Assignment assignment = assignments.save(new Assignment(classroomId, title, mode));
        int order = 0;
        for (UUID itemId : itemIds) {
            itemService.getOwned(teacherId, itemId); // 소유 검증
            assignmentItems.save(new AssignmentItem(assignment.getId(), itemId, order++));
        }
        return assignment;
    }

    public List<Assignment> listByClassroom(UUID teacherId, UUID classroomId) {
        classroomService.getOwned(teacherId, classroomId);
        return assignments.findByClassroomIdOrderByCreatedAtDesc(classroomId);
    }

    public Assignment getOwned(UUID teacherId, UUID assignmentId) {
        Assignment assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다."));
        classroomService.getOwned(teacherId, assignment.getClassroomId());
        return assignment;
    }

    @Transactional
    public Assignment open(UUID teacherId, UUID assignmentId) {
        Assignment assignment = getOwned(teacherId, assignmentId);
        assignment.setOpenedAt(Instant.now());
        assignment.setClosedAt(null);
        return assignments.save(assignment);
    }

    @Transactional
    public Assignment close(UUID teacherId, UUID assignmentId) {
        Assignment assignment = getOwned(teacherId, assignmentId);
        assignment.setClosedAt(Instant.now());
        return assignments.save(assignment);
    }

    public List<AssignmentItem> itemsOf(UUID assignmentId) {
        return assignmentItems.findByIdAssignmentIdOrderByOrderNoAsc(assignmentId);
    }

    public DictationItem item(UUID itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "문항을 찾을 수 없습니다."));
    }

    /** 지금 열려 있는 과제 중 이 학급의 가장 최근 것. 학생 응시 진입점. */
    public Assignment findOpenForClassroom(UUID classroomId) {
        Instant now = Instant.now();
        return assignments.findByClassroomIdOrderByCreatedAtDesc(classroomId).stream()
                .filter(a -> a.isOpen(now))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "지금 열린 과제가 없습니다."));
    }
}
