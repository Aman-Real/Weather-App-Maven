package com.skycast.weather.service;

import com.skycast.weather.domain.FetchAuditLog;
import com.skycast.weather.repository.FetchAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit rows are written in their OWN transaction (REQUIRES_NEW): the caller's transaction is
 * suspended, so the audit entry is committed even if the caller later rolls back.
 */
@Service
public class FetchAuditService {

    private final FetchAuditLogRepository repository;

    public FetchAuditService(FetchAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String endpoint, double lat, double lon, boolean success, long startedNanos, String message) {
        long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
        repository.save(new FetchAuditLog(endpoint, lat, lon, success, durationMs, message));
    }

    @Transactional(readOnly = true)
    public long failures() {
        return repository.countBySuccessFalse();
    }

    @Transactional(readOnly = true)
    public long total() {
        return repository.count();
    }
}
