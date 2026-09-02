package com.chominjungum.web;

import com.chominjungum.security.AuthPrincipal;
import com.chominjungum.service.SyncService;
import com.chominjungum.web.SyncDtos.SyncSessionRequest;
import com.chominjungum.web.SyncDtos.SyncSessionResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 교사앱(Flutter)이 교실 수업 결과를 올리는 창구. */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sessions")
    public SyncSessionResponse ingest(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody SyncSessionRequest req) {
        return syncService.ingest(me.id(), req);
    }
}
