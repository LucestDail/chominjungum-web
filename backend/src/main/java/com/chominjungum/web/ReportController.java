package com.chominjungum.web;

import com.chominjungum.security.AuthPrincipal;
import com.chominjungum.service.ReportService;
import com.chominjungum.web.ReportDtos.ClassroomReport;
import com.chominjungum.web.ReportDtos.StudentReport;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/classroom/{classroomId}")
    public ClassroomReport classroom(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID classroomId) {
        return reportService.classroomReport(me.id(), classroomId);
    }

    @GetMapping("/student/{studentId}")
    public StudentReport student(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID studentId) {
        return reportService.studentReport(me.id(), studentId);
    }

    @GetMapping("/classroom/{classroomId}/csv")
    public ResponseEntity<String> csv(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID classroomId) {
        String body = reportService.classroomCsv(me.id(), classroomId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.csv\"")
                .body(body);
    }
}
