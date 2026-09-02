package com.chominjungum.service;

import com.chominjungum.domain.Assignment;
import com.chominjungum.domain.Attempt;
import com.chominjungum.domain.DictationItem;
import com.chominjungum.domain.ExamSession;
import com.chominjungum.hangul.DictationCompare;
import com.chominjungum.repo.AttemptRepository;
import com.chominjungum.repo.ExamSessionRepository;
import com.chominjungum.repo.StudentDeviceRepository;
import com.chominjungum.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttemptService {

    private final AttemptRepository attempts;
    private final ExamSessionRepository sessions;
    private final StudentDeviceRepository devices;
    private final ObjectMapper objectMapper;

    public AttemptService(
            AttemptRepository attempts,
            ExamSessionRepository sessions,
            StudentDeviceRepository devices,
            ObjectMapper objectMapper) {
        this.attempts = attempts;
        this.sessions = sessions;
        this.devices = devices;
        this.objectMapper = objectMapper;
    }

    /** 학생 한 명의 웹 응시 세션. 과제·학생 조합마다 하나만 만든다. */
    @Transactional
    public ExamSession webSessionFor(Assignment assignment, UUID studentId) {
        UUID sessionId = UUID.nameUUIDFromBytes(
                ("web:" + assignment.getId() + ":" + studentId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return sessions.findById(sessionId).orElseGet(() -> {
            ExamSession session = new ExamSession(
                    sessionId, assignment.getClassroomId(), assignment.getId(), ExamSession.Source.WEB);
            session.setStartedAt(Instant.now());
            return sessions.save(session);
        });
    }

    /**
     * 답안 저장 — <b>서버가 다시 채점한다</b>. 클라이언트가 보낸 점수는 신뢰하지 않는다(PLAN §8.2).
     * 같은 (세션, 기기, 문항) 조합이면 최신 제출로 대체한다.
     */
    @Transactional
    public Attempt submit(
            ExamSession session,
            DictationItem item,
            UUID studentId,
            String deviceBindingId,
            String rawAnswer,
            String inputKind,
            Instant submittedAt,
            UUID attemptId) {

        DictationCompare.Result result = DictationCompare.score(item.getExpectedText(), rawAnswer);

        Optional<Attempt> existing =
                attempts.findBySessionIdAndDeviceBindingIdAndItemId(session.getId(), deviceBindingId, item.getId());

        Attempt attempt = existing.orElseGet(Attempt::new);
        if (existing.isPresent() && attempt.getSubmittedAt() != null
                && attempt.getSubmittedAt().isAfter(submittedAt)) {
            // 더 최신 제출이 이미 있으면 덮어쓰지 않는다(업싱크 순서가 뒤바뀔 수 있다).
            return attempt;
        }

        if (existing.isEmpty()) {
            attempt.setId(attemptId != null ? attemptId : UUID.randomUUID());
        }
        attempt.setSessionId(session.getId());
        attempt.setItemId(item.getId());
        attempt.setDeviceBindingId(deviceBindingId);
        attempt.setStudentId(studentId != null ? studentId : resolveStudent(deviceBindingId));
        attempt.setRawAnswer(rawAnswer);
        attempt.setCorrectCount(result.correctCount());
        attempt.setTotalCount(result.totalCount());
        attempt.setInputKind(inputKind == null ? "keyboard" : inputKind);
        attempt.setGlyphMatches(toJson(result));
        attempt.setSubmittedAt(submittedAt);
        return attempts.save(attempt);
    }

    /** 기기가 명단에 연결돼 있으면 학생을 찾아준다. 없으면 미배정(null)으로 남긴다. */
    private UUID resolveStudent(String deviceBindingId) {
        if (deviceBindingId == null) {
            return null;
        }
        return devices.findById(deviceBindingId).map(d -> d.getStudentId()).orElse(null);
    }

    /**
     * 기기를 명단에 연결한 뒤, 그 기기의 미배정 제출을 소급해서 학생에게 붙인다.
     * 매핑 전 제출도 버리지 않는다는 약속(docs/sync-protocol.md §5)의 구현.
     */
    @Transactional
    public int backfillStudent(String deviceBindingId, UUID studentId) {
        List<Attempt> orphans = attempts.findByDeviceBindingId(deviceBindingId).stream()
                .filter(a -> a.getStudentId() == null)
                .toList();
        orphans.forEach(a -> a.setStudentId(studentId));
        attempts.saveAll(orphans);
        return orphans.size();
    }

    public List<Attempt> bySession(UUID sessionId) {
        return attempts.findBySessionId(sessionId);
    }

    private String toJson(DictationCompare.Result result) {
        try {
            return objectMapper.writeValueAsString(result.matchSummaries());
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "채점 결과 직렬화에 실패했습니다.");
        }
    }
}
