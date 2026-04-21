package com.brandedhustlers.electionbackend.audit.services;

import com.brandedhustlers.electionbackend.audit.dto.AuditEventDTO;
import com.brandedhustlers.electionbackend.audit.dto.AuditSearchFilter;
import com.brandedhustlers.electionbackend.audit.dto.PageResponse;

import java.util.UUID;

/**
 * Org-scoped search & get for dashboard
 */
public interface    AuditQueryService {
    PageResponse<AuditEventDTO> search(AuditSearchFilter filter);

    AuditEventDTO get(UUID orgId, UUID auditId);
}