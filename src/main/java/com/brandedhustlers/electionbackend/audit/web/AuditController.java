package com.brandedhustlers.electionbackend.audit.web;

import com.brandedhustlers.electionbackend.audit.dto.AuditEventDTO;
import com.brandedhustlers.electionbackend.audit.dto.AuditSearchFilter;
import com.brandedhustlers.electionbackend.audit.dto.PageResponse;
import com.brandedhustlers.electionbackend.audit.services.AuditQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditQueryService query;

    public AuditController(AuditQueryService query) {
        this.query = query;
    }

    @GetMapping("/trail")
    public ResponseEntity<PageResponse<AuditEventDTO>> trail(
            @RequestParam UUID orgId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "occurred_at,DESC") String sort
    ) {
        AuditSearchFilter f = AuditSearchFilter.builder()
                .orgId(orgId).eventType(eventType).actorId(actorId).targetType(targetType).targetId(targetId)
                .severity(severity).page(page).size(size).sort(sort)
                .build();
        return ResponseEntity.ok(query.search(f));
    }

    @GetMapping("/event/{auditId}")
    public ResponseEntity<AuditEventDTO> get(@RequestParam UUID orgId, @PathVariable UUID auditId) {
        return ResponseEntity.ok(query.get(orgId, auditId));
    }
}