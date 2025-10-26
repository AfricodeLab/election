package com.brandedhustlers.electionbackend.audit.repository;

import com.brandedhustlers.electionbackend.audit.domain.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface DomainEventRepository extends JpaRepository<DomainEvent, UUID>, JpaSpecificationExecutor<DomainEvent> {
}
