package com.brandedhustlers.electionbackend.common.config.persistence;

import com.brandedhustlers.electionbackend.auth.domain.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AllRequestLoggingRepository extends JpaRepository<RequestLog, Long> {
    RequestLog findTopByOrderByIdDesc();

    @Transactional
    void deleteRequestResponseLogByResponseBodyNullAndResponseStatusEquals(int responseStatus);
}
