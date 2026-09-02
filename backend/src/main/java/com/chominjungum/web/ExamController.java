package com.chominjungum.web;

import com.chominjungum.domain.Assignment;
import com.chominjungum.domain.Attempt;
import com.chominjungum.domain.DictationItem;
import com.chominjungum.domain.ExamSession;
import com.chominjungum.hangul.HangulUtil;
import com.chominjungum.security.AuthPrincipal;
import com.chominjungum.service.AssignmentService;
import com.chominjungum.service.AttemptService;
import com.chominjungum.web.Dtos.AttemptRequest;
import com.chominjungum.web.Dtos.AttemptResponse;
import com.chominjungum.web.Dtos.ExamItemResponse;
import com.chominjungum.web.Dtos.ExamStartResponse;
import com.chominjungum.web.Dtos.GlyphMatchResponse;
import com.chominjungum.hangul.DictationCompare;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생 웹 응시 API.
 *
 * <p><b>정답 텍스트를 내려주지 않는다.</b> 받아쓰기는 듣고 쓰는 활동이라, 문제는 교사가 읽어주고
 * 화면에는 몇 번 문항인지와 칸 수만 보인다. 정답은 제출 직후 채점 결과로만 공개된다.
 */
@RestController
@RequestMapping("/api/exam")
public class ExamController {

    private final AssignmentService assignmentService;
    private final AttemptService attemptService;

    public ExamController(AssignmentService assignmentService, AttemptService attemptService) {
        this.assignmentService = assignmentService;
        this.attemptService = attemptService;
    }

    @GetMapping("/current")
    public ExamStartResponse current(@AuthenticationPrincipal AuthPrincipal me) {
        Assignment assignment = assignmentService.findOpenForClassroom(me.classroomId());
        List<ExamItemResponse> items = assignmentService.itemsOf(assignment.getId()).stream()
                .map(ai -> {
                    DictationItem item = assignmentService.item(ai.getId().getItemId());
                    return new ExamItemResponse(
                            item.getId(), ai.getOrderNo(), HangulUtil.addWord(item.getExpectedText()).size());
                })
                .toList();
        return new ExamStartResponse(assignment.getId(), assignment.getTitle(), items);
    }

    @PostMapping("/attempts")
    public AttemptResponse submit(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody AttemptRequest req) {

        Assignment assignment = assignmentService.findOpenForClassroom(me.classroomId());
        boolean belongs = assignmentService.itemsOf(assignment.getId()).stream()
                .anyMatch(ai -> ai.getId().getItemId().equals(req.itemId()));
        if (!belongs) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "이 과제의 문항이 아닙니다.");
        }

        DictationItem item = assignmentService.item(req.itemId());
        ExamSession session = attemptService.webSessionFor(assignment, me.id());

        // 웹 응시는 기기 ID 가 없어 학생별 고정 키를 쓴다(재제출 멱등).
        String deviceKey = "web:" + me.id();

        Attempt attempt = attemptService.submit(
                session,
                item,
                me.id(),
                deviceKey,
                req.rawAnswer().trim(),
                req.inputKind(),
                Instant.now(),
                null);

        DictationCompare.Result result = DictationCompare.score(item.getExpectedText(), attempt.getRawAnswer());
        List<GlyphMatchResponse> matches = result.matches().stream()
                .map(m -> new GlyphMatchResponse(m.index(), m.correct(), m.mismatchReason()))
                .toList();

        return new AttemptResponse(
                attempt.getId(),
                item.getId(),
                item.getExpectedText(), // 제출 후이므로 정답 공개
                attempt.getRawAnswer(),
                attempt.getCorrectCount(),
                attempt.getTotalCount(),
                attempt.scorePercent(),
                matches);
    }
}
