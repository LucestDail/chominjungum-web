package com.chominjungum.service;

import com.chominjungum.domain.Attempt;
import com.chominjungum.domain.DictationItem;
import com.chominjungum.domain.ExamSession;
import com.chominjungum.repo.AttemptRepository;
import com.chominjungum.repo.ExamSessionRepository;
import com.chominjungum.repo.StudentDeviceRepository;
import com.chominjungum.web.SyncDtos.RejectedAttempt;
import com.chominjungum.web.SyncDtos.SyncAttempt;
import com.chominjungum.web.SyncDtos.SyncItem;
import com.chominjungum.web.SyncDtos.SyncSessionRequest;
import com.chominjungum.web.SyncDtos.SyncSessionResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교실 LAN 수업 결과 업싱크.
 *
 * <p>세 가지 약속을 지킨다(docs/sync-protocol.md):
 * <ol>
 *   <li><b>멱등</b> — 같은 배치를 여러 번 보내도 결과가 같다.</li>
 *   <li><b>부분 성공</b> — 일부 attempt 가 거부돼도 나머지는 저장된다.</li>
 *   <li><b>버리지 않음</b> — 명단에 없는 기기의 제출도 미배정으로 보관한다.</li>
 * </ol>
 */
@Service
public class SyncService {

    private final ExamSessionRepository sessions;
    private final AttemptRepository attempts;
    private final StudentDeviceRepository devices;
    private final ClassroomService classroomService;
    private final ItemService itemService;
    private final AttemptService attemptService;

    public SyncService(
            ExamSessionRepository sessions,
            AttemptRepository attempts,
            StudentDeviceRepository devices,
            ClassroomService classroomService,
            ItemService itemService,
            AttemptService attemptService) {
        this.sessions = sessions;
        this.attempts = attempts;
        this.devices = devices;
        this.classroomService = classroomService;
        this.itemService = itemService;
        this.attemptService = attemptService;
    }

    @Transactional
    public SyncSessionResponse ingest(UUID teacherId, SyncSessionRequest req) {
        // 이 학급이 정말 이 교사 것인지 반드시 확인한다.
        classroomService.getOwned(teacherId, req.classroomId());

        ExamSession session = upsertSession(req);

        // 앱의 로컬 문항 id → 서버 문항. 같은 문장은 contentHash 로 재사용된다.
        Map<String, DictationItem> itemMap = new HashMap<>();
        for (SyncItem item : nullSafe(req.items())) {
            try {
                itemMap.put(item.id(), itemService.create(teacherId, item.expectedText(), null, null));
            } catch (RuntimeException e) {
                // 허용 밖 문자·길이 초과 문항은 건너뛴다. 해당 attempt 는 아래에서 거부로 집계된다.
            }
        }

        int accepted = 0;
        int duplicated = 0;
        List<RejectedAttempt> rejected = new ArrayList<>();
        Set<String> unassigned = new LinkedHashSet<>();

        for (SyncAttempt a : nullSafe(req.attempts())) {
            DictationItem item = itemMap.get(a.itemId());
            if (item == null) {
                rejected.add(new RejectedAttempt(a.attemptId(), "UNKNOWN_ITEM"));
                continue;
            }
            if (a.rawAnswer() == null) {
                rejected.add(new RejectedAttempt(a.attemptId(), "EMPTY_ANSWER"));
                continue;
            }

            boolean alreadyStored = attempts.existsById(a.attemptId());

            attemptService.submit(
                    session,
                    item,
                    null, // 학생은 기기 매핑으로 결정된다
                    a.deviceBindingId(),
                    a.rawAnswer(),
                    a.inputKind(),
                    Instant.ofEpochMilli(a.submittedAtMs()),
                    a.attemptId());

            if (alreadyStored) {
                duplicated++;
            } else {
                accepted++;
            }

            if (devices.findById(a.deviceBindingId()).isEmpty()) {
                unassigned.add(a.deviceBindingId());
            }
        }

        session.setSyncedAt(Instant.now());
        sessions.save(session);

        return new SyncSessionResponse(
                session.getId(), accepted, duplicated, rejected, List.copyOf(unassigned));
    }

    private ExamSession upsertSession(SyncSessionRequest req) {
        ExamSession session = sessions.findById(req.sessionId()).orElseGet(() -> {
            ExamSession.Source source = req.source() == null
                    ? ExamSession.Source.LAN
                    : ExamSession.Source.valueOf(req.source());
            return new ExamSession(req.sessionId(), req.classroomId(), null, source);
        });
        if (req.startedAtMs() != null) {
            session.setStartedAt(Instant.ofEpochMilli(req.startedAtMs()));
        }
        if (req.endedAtMs() != null) {
            session.setEndedAt(Instant.ofEpochMilli(req.endedAtMs()));
        }
        return sessions.save(session);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
