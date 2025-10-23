package com.brandedhustlers.electionbackend.notifications.repository;

import com.brandedhustlers.electionbackend.notifications.domain.MessageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageLogRepository extends JpaRepository<MessageLog, UUID> {
}
