package com.brandedhustlers.electionbackend.audit.services;

import com.brandedhustlers.electionbackend.audit.domain.DomainEvent;
import com.brandedhustlers.electionbackend.audit.dto.AuditSearchFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification factory — converts AuditSearchFilter into a JPA Specification.
 * Kept small and readable; extend as needed for prefix/suffix matching rules.
 */
public class AuditSpecs {
    static Specification<DomainEvent> from(AuditSearchFilter f) {
        return (root,
                cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            ps.add(cb.equal(root.get("orgId"), f.getOrgId()));

            if (f.getEventType() != null && !f.getEventType().isBlank()) {
                // Prefix match support: treat trailing * as wildcard
                String et = f.getEventType();
                if (et.endsWith("*")){
                    ps.add(cb.like(root.get("eventType"), et.substring(0, et.length() - 1) + "%"));
                }
                else {
                    ps.add(cb.equal(root.get("eventType"), et));
                }
            }
            if (f.getActorId() != null) {
                ps.add(cb.equal(root.get("actorId"), f.getActorId()));
            }

            if (f.getTargetType() != null && !f.getTargetType().isBlank()) {
                ps.add(cb.equal(root.get("targetType"), f.getTargetType()));
            }
            if (f.getTargetId() != null) {
                ps.add(cb.equal(root.get("targetId"), f.getTargetId()));
            }
            if (f.getSeverity() != null && !f.getSeverity().isBlank()) {
                ps.add(cb.equal(root.get("severity"), f.getSeverity()));
            }
            if (f.getFrom() != null){
                ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), f.getFrom()));
            }
            if (f.getTo() != null){
                ps.add(cb.lessThanOrEqualTo(root.get("occurredAt"), f.getTo()));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
