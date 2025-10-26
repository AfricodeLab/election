package com.brandedhustlers.electionbackend.audit.services;

import com.brandedhustlers.electionbackend.audit.domain.DomainEvent;
import com.brandedhustlers.electionbackend.audit.dto.AuditEventDTO;
import com.brandedhustlers.electionbackend.audit.dto.AuditSearchFilter;
import com.brandedhustlers.electionbackend.audit.dto.PageResponse;
import com.brandedhustlers.electionbackend.audit.repository.DomainEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuditQueryServiceImpl implements AuditQueryService {
    private final DomainEventRepository repo;

    public AuditQueryServiceImpl(DomainEventRepository repo) {
        this.repo = repo;
    }

    @Override
    public PageResponse<AuditEventDTO> search(AuditSearchFilter f) {
        Objects.requireNonNull(f.getOrgId(), "orgId is required");
        // paging
        Sort sort = parseSort(f.getSort());
        Pageable pageable = PageRequest.of(Math.max(0, f.getPage()), Math.max(1, f.getSize()), sort);
        Page<DomainEvent> page = repo.findAll(AuditSpecs.from(f), pageable);
        List<AuditEventDTO> items = page.getContent().stream().map(AuditQueryServiceImpl::toDto).toList();
        return PageResponse.<AuditEventDTO>builder()
                .items(items).page(page.getNumber()).size(page.getSize()).total(page.getTotalElements())
                .build();
    }

    @Override
    public AuditEventDTO get(UUID orgId, UUID auditId) {
        DomainEvent e = repo.findById(auditId).orElseThrow(() -> new NoSuchElementException("AUDIT-404 not found"));
        if (!e.getOrgId().equals(orgId)){
            throw new SecurityException("AUDIT-403 org mismatch");
        }
        return toDto(e);
    }

    private static Sort parseSort(String s) {
        if (s == null || s.isBlank()) {
            return Sort.by(Sort.Order.desc("occurredAt"));
        }
        String[] parts = s.split(",");
        String field = parts[0];
        boolean asc = parts.length < 2 || parts[1].equalsIgnoreCase("ASC");
        // map external field name to entity property if needed
        if (field.equals("occurred_at")) {
            field = "occurredAt";
        }
        return Sort.by(asc ? Sort.Order.asc(field) : Sort.Order.desc(field));
    }

    private static AuditEventDTO toDto(DomainEvent e) {
        return AuditEventDTO.builder()
                .auditId(e.getAuditId()).eventId(e.getEventId()).orgId(e.getOrgId())
                .occurredAt(e.getOccurredAt()).eventType(e.getEventType()).actionLabel(e.getActionLabel())
                .actorId(e.getActorId()).actorName(e.getActorName()).actorRole(e.getActorRole()).actorType(e.getActorType())
                .targetType(e.getTargetType()).targetId(e.getTargetId()).targetLabel(e.getTargetLabel())
                .severity(e.getSeverity()).source(e.getSource()).correlationId(e.getCorrelationId())
                .build();
    }
}