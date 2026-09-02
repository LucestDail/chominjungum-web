package com.chominjungum.service;

import com.chominjungum.domain.Attempt;
import com.chominjungum.domain.Classroom;
import com.chominjungum.domain.DictationItem;
import com.chominjungum.domain.ExamSession;
import com.chominjungum.domain.Student;
import com.chominjungum.hangul.Hangul;
import com.chominjungum.hangul.HangulUtil;
import com.chominjungum.repo.AttemptRepository;
import com.chominjungum.repo.DictationItemRepository;
import com.chominjungum.repo.ExamSessionRepository;
import com.chominjungum.repo.StudentRepository;
import com.chominjungum.web.ApiException;
import com.chominjungum.web.ReportDtos.AttemptRow;
import com.chominjungum.web.ReportDtos.ClassroomReport;
import com.chominjungum.web.ReportDtos.ItemRow;
import com.chominjungum.web.ReportDtos.StudentReport;
import com.chominjungum.web.ReportDtos.StudentRow;
import com.chominjungum.web.ReportDtos.WeakJamo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 성적·리포트.
 *
 * <p>취약 자모 분석이 이 계층의 핵심이다. {@code attempt.glyph_matches} 의 틀린 인덱스를
 * 해당 문항의 자모 분해와 맞춰, 어떤 초성·중성·종성에서 자주 틀리는지 센다.
 */
@Service
public class ReportService {

    private final StudentRepository students;
    private final AttemptRepository attempts;
    private final ExamSessionRepository sessions;
    private final DictationItemRepository items;
    private final ClassroomService classroomService;
    private final ObjectMapper objectMapper;

    public ReportService(
            StudentRepository students,
            AttemptRepository attempts,
            ExamSessionRepository sessions,
            DictationItemRepository items,
            ClassroomService classroomService,
            ObjectMapper objectMapper) {
        this.students = students;
        this.attempts = attempts;
        this.sessions = sessions;
        this.items = items;
        this.classroomService = classroomService;
        this.objectMapper = objectMapper;
    }

    public ClassroomReport classroomReport(UUID teacherId, UUID classroomId) {
        Classroom classroom = classroomService.getOwned(teacherId, classroomId);
        List<Attempt> all = attemptsOf(classroomId);
        List<Student> roster = students.findByClassroomIdOrderByStudentNoAscDisplayNameAsc(classroomId);

        Map<UUID, List<Attempt>> byStudent = new HashMap<>();
        for (Attempt a : all) {
            if (a.getStudentId() != null) {
                byStudent.computeIfAbsent(a.getStudentId(), k -> new ArrayList<>()).add(a);
            }
        }

        List<StudentRow> studentRows = roster.stream()
                .map(s -> {
                    List<Attempt> mine = byStudent.getOrDefault(s.getId(), List.of());
                    return new StudentRow(
                            s.getId(),
                            s.getDisplayName(),
                            mine.size(),
                            averagePercent(mine),
                            (int) mine.stream()
                                    .filter(a -> a.getTotalCount() > 0
                                            && a.getCorrectCount() == a.getTotalCount())
                                    .count());
                })
                .toList();

        Map<UUID, List<Attempt>> byItem = new LinkedHashMap<>();
        for (Attempt a : all) {
            byItem.computeIfAbsent(a.getItemId(), k -> new ArrayList<>()).add(a);
        }
        List<ItemRow> itemRows = byItem.entrySet().stream()
                .map(e -> {
                    DictationItem item = items.findById(e.getKey()).orElse(null);
                    return new ItemRow(
                            e.getKey(),
                            item == null ? "(삭제된 문항)" : item.getExpectedText(),
                            e.getValue().size(),
                            averagePercent(e.getValue()));
                })
                .sorted(Comparator.comparingInt(ItemRow::averagePercent))
                .toList();

        long unassigned = all.stream().filter(a -> a.getStudentId() == null).count();

        return new ClassroomReport(
                classroomId,
                classroom.getName(),
                roster.size(),
                all.size(),
                averagePercent(all),
                studentRows,
                itemRows,
                (int) unassigned);
    }

    public StudentReport studentReport(UUID teacherId, UUID studentId) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "학생을 찾을 수 없습니다."));
        classroomService.getOwned(teacherId, student.getClassroomId());

        List<Attempt> mine = attempts.findByStudentId(studentId).stream()
                .sorted(Comparator.comparing(Attempt::getSubmittedAt))
                .toList();

        List<AttemptRow> rows = mine.stream()
                .map(a -> {
                    DictationItem item = items.findById(a.getItemId()).orElse(null);
                    return new AttemptRow(
                            a.getId(),
                            item == null ? "(삭제된 문항)" : item.getExpectedText(),
                            a.getRawAnswer(),
                            a.getCorrectCount(),
                            a.getTotalCount(),
                            a.scorePercent(),
                            a.getSubmittedAt().toEpochMilli());
                })
                .toList();

        return new StudentReport(
                studentId,
                student.getDisplayName(),
                mine.size(),
                averagePercent(mine),
                rows,
                weakJamos(mine));
    }

    /**
     * 틀린 글자의 자모를 세어 취약점을 뽑는다.
     * 자모 코드는 jammin 분해 결과 그대로라 학습지 가리기 기능과 바로 이어진다.
     */
    List<WeakJamo> weakJamos(List<Attempt> attemptList) {
        Map<Integer, Integer> misses = new HashMap<>();

        for (Attempt a : attemptList) {
            if (a.getGlyphMatches() == null) {
                continue;
            }
            DictationItem item = items.findById(a.getItemId()).orElse(null);
            if (item == null) {
                continue;
            }
            List<Hangul> glyphs = HangulUtil.addWord(item.getExpectedText());

            try {
                JsonNode matches = objectMapper.readTree(a.getGlyphMatches());
                for (JsonNode m : matches) {
                    if (m.path("ok").asBoolean(true)) {
                        continue;
                    }
                    int index = m.path("i").asInt(-1);
                    if (index < 0 || index >= glyphs.size()) {
                        continue; // 초과 입력 등은 특정 자모로 귀속되지 않는다
                    }
                    Hangul g = glyphs.get(index);
                    if (Boolean.TRUE.equals(g.getSpecialFlag())) {
                        continue;
                    }
                    countIfPresent(misses, g.getChoCode());
                    countIfPresent(misses, g.getJungCode());
                    countIfPresent(misses, g.getJongCode());
                }
            } catch (Exception e) {
                // 손상된 기록은 통계에서 제외한다
            }
        }

        return misses.entrySet().stream()
                .map(e -> new WeakJamo(kindOf(e.getKey()), e.getKey(), labelOf(e.getKey()), e.getValue()))
                .filter(w -> w.kind() != null)
                .sorted(Comparator.comparingInt(WeakJamo::missCount).reversed())
                .limit(10)
                .toList();
    }

    private static void countIfPresent(Map<Integer, Integer> misses, int code) {
        if (code > 0) {
            misses.merge(code, 1, Integer::sum);
        }
    }

    private static String kindOf(int code) {
        if (code >= 0x1100 && code <= 0x1112) {
            return "cho";
        }
        if (code >= 0x1161 && code <= 0x1175) {
            return "jung";
        }
        if (code >= 0x11a8 && code <= 0x11c2) {
            return "jong";
        }
        return null;
    }

    private static String labelOf(int code) {
        return String.valueOf((char) code);
    }

    private List<Attempt> attemptsOf(UUID classroomId) {
        List<UUID> sessionIds = sessions.findByClassroomIdOrderByCreatedAtDesc(classroomId).stream()
                .map(ExamSession::getId)
                .toList();
        return sessionIds.isEmpty() ? List.of() : attempts.findBySessionIdIn(sessionIds);
    }

    private static int averagePercent(List<Attempt> list) {
        if (list.isEmpty()) {
            return 0;
        }
        return (int) Math.round(list.stream().mapToInt(Attempt::scorePercent).average().orElse(0));
    }

    /** 학급 성적표 CSV. 엑셀에서 열 수 있도록 BOM 을 붙인다. */
    public String classroomCsv(UUID teacherId, UUID classroomId) {
        ClassroomReport report = classroomReport(teacherId, classroomId);
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("학생,제출수,평균점수,만점횟수\n");
        for (StudentRow row : report.students()) {
            sb.append(escape(row.displayName())).append(',')
                    .append(row.attemptCount()).append(',')
                    .append(row.averagePercent()).append(',')
                    .append(row.perfectCount()).append('\n');
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
