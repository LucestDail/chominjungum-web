package com.chominjungum.web;

import com.chominjungum.domain.Assignment;
import com.chominjungum.domain.Classroom;
import com.chominjungum.domain.DictationItem;
import com.chominjungum.hangul.HangulUtil;
import com.chominjungum.security.AuthPrincipal;
import com.chominjungum.service.AssignmentService;
import com.chominjungum.service.AttemptService;
import com.chominjungum.service.ClassroomService;
import com.chominjungum.service.ItemService;
import com.chominjungum.web.Dtos.AssignmentRequest;
import com.chominjungum.web.Dtos.AssignmentResponse;
import com.chominjungum.web.Dtos.BindDeviceRequest;
import com.chominjungum.web.Dtos.BindDeviceResponse;
import com.chominjungum.web.Dtos.ClassroomRequest;
import com.chominjungum.web.Dtos.ClassroomResponse;
import com.chominjungum.web.Dtos.ItemRequest;
import com.chominjungum.web.Dtos.ItemResponse;
import com.chominjungum.web.Dtos.StudentRequest;
import com.chominjungum.web.Dtos.StudentResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 교사 콘솔 API — 학급·명단·문항·과제. */
@RestController
@RequestMapping("/api")
public class TeacherController {

    private final ClassroomService classroomService;
    private final ItemService itemService;
    private final AssignmentService assignmentService;
    private final AttemptService attemptService;

    public TeacherController(
            ClassroomService classroomService,
            ItemService itemService,
            AssignmentService assignmentService,
            AttemptService attemptService) {
        this.classroomService = classroomService;
        this.itemService = itemService;
        this.assignmentService = assignmentService;
        this.attemptService = attemptService;
    }

    // ── 학급 ────────────────────────────────────────────────
    @PostMapping("/classrooms")
    public ClassroomResponse createClassroom(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody ClassroomRequest req) {
        Classroom c = classroomService.create(me.id(), req.name(), req.grade(), req.schoolYear());
        return toResponse(c);
    }

    @GetMapping("/classrooms")
    public List<ClassroomResponse> listClassrooms(@AuthenticationPrincipal AuthPrincipal me) {
        return classroomService.listByTeacher(me.id()).stream().map(TeacherController::toResponse).toList();
    }

    @GetMapping("/classrooms/{classroomId}/students")
    public List<StudentResponse> roster(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID classroomId) {
        return classroomService.roster(me.id(), classroomId).stream()
                .map(s -> new StudentResponse(s.getId(), s.getDisplayName(), s.getStudentNo()))
                .toList();
    }

    @PostMapping("/classrooms/{classroomId}/students")
    public StudentResponse addStudent(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID classroomId,
            @Valid @RequestBody StudentRequest req) {
        var s = classroomService.addStudent(me.id(), classroomId, req.displayName(), req.studentNo());
        return new StudentResponse(s.getId(), s.getDisplayName(), s.getStudentNo());
    }

    /** 아직 명단에 연결되지 않은 기기 — 업싱크 후 교사가 처리할 대기열. */
    @GetMapping("/classrooms/{classroomId}/unassigned-devices")
    public List<Dtos.UnassignedDevice> unassignedDevices(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID classroomId) {
        return classroomService.unassignedDevices(me.id(), classroomId);
    }

    /** 교실 LAN 제출의 익명 기기를 명단에 연결하고, 그 기기의 과거 제출을 소급 매핑한다. */
    @PostMapping("/classrooms/{classroomId}/devices")
    public BindDeviceResponse bindDevice(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID classroomId,
            @Valid @RequestBody BindDeviceRequest req) {
        classroomService.bindDevice(me.id(), classroomId, req.deviceBindingId(), req.studentId());
        int backfilled = attemptService.backfillStudent(req.deviceBindingId(), req.studentId());
        return new BindDeviceResponse(req.deviceBindingId(), req.studentId(), backfilled);
    }

    // ── 문항 ────────────────────────────────────────────────
    @PostMapping("/items")
    public ItemResponse createItem(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody ItemRequest req) {
        DictationItem item = itemService.create(me.id(), req.expectedText(), req.grade(), req.unit());
        return toResponse(item);
    }

    @GetMapping("/items")
    public List<ItemResponse> listItems(@AuthenticationPrincipal AuthPrincipal me) {
        return itemService.listByTeacher(me.id()).stream().map(TeacherController::toResponse).toList();
    }

    // ── 과제 ────────────────────────────────────────────────
    @PostMapping("/assignments")
    public AssignmentResponse createAssignment(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody AssignmentRequest req) {
        Assignment.Mode mode =
                req.mode() == null ? Assignment.Mode.ONLINE : Assignment.Mode.valueOf(req.mode());
        Assignment a = assignmentService.create(me.id(), req.classroomId(), req.title(), mode, req.itemIds());
        return toResponse(a, req.itemIds().size());
    }

    @GetMapping("/classrooms/{classroomId}/assignments")
    public List<AssignmentResponse> listAssignments(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID classroomId) {
        return assignmentService.listByClassroom(me.id(), classroomId).stream()
                .map(a -> toResponse(a, assignmentService.itemsOf(a.getId()).size()))
                .toList();
    }

    @PostMapping("/assignments/{assignmentId}/open")
    public AssignmentResponse open(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID assignmentId) {
        Assignment a = assignmentService.open(me.id(), assignmentId);
        return toResponse(a, assignmentService.itemsOf(a.getId()).size());
    }

    @PostMapping("/assignments/{assignmentId}/close")
    public AssignmentResponse close(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID assignmentId) {
        Assignment a = assignmentService.close(me.id(), assignmentId);
        return toResponse(a, assignmentService.itemsOf(a.getId()).size());
    }

    private static ClassroomResponse toResponse(Classroom c) {
        return new ClassroomResponse(c.getId(), c.getName(), c.getGrade(), c.getSchoolYear(), c.getJoinCode());
    }

    private static ItemResponse toResponse(DictationItem item) {
        return new ItemResponse(
                item.getId(),
                item.getExpectedText(),
                HangulUtil.addWord(item.getExpectedText()).size(),
                item.getGrade(),
                item.getUnit());
    }

    private static AssignmentResponse toResponse(Assignment a, int itemCount) {
        return new AssignmentResponse(
                a.getId(), a.getClassroomId(), a.getTitle(), a.getMode(), a.isOpen(Instant.now()), itemCount);
    }
}
