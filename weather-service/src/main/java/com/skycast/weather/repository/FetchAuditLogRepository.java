package com.skycast.weather.repository;

import com.skycast.weather.domain.FetchAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repository for the XML-mapped entity. */
public interface FetchAuditLogRepository extends JpaRepository<FetchAuditLog, Long> {

    long countBySuccessFalse();

    /** Backed by the named native query "FetchAuditLog.failuresByEndpoint" in META-INF/orm.xml. */
    List<Object[]> failuresByEndpoint();
}
