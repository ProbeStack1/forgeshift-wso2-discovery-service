package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.MigrationAuditEntry;
import com.forgeshift.wso2discovery.repository.MigrationAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronously persists audit rows. Mirrors Apigee's MigrationAuditService.
 *
 * Disabled when {@code forgeshift.discovery.audit-enabled = false}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationAuditService {

    private final MigrationAuditRepository repository;
    private final DiscoveryProperties props;

    @Async("discoveryExecutor")
    public void record(MigrationAuditEntry entry) {
        if (!props.isAuditEnabled() || entry == null) return;
        try {
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit entry {}: {}", entry.getServiceTransactionId(), e.getMessage());
        }
    }

    public MigrationAuditRepository repository() {
        return repository;
    }
}
