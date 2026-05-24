package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.domain.Wso2OrganizationEntity;
import com.forgeshift.wso2discovery.repository.Wso2OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

/**
 * Auto-upserts a row in {@code wso2_organizations} after every discovery so
 * the UI can list "tenants we have data for" with one indexed read.
 *
 * Mirrors Apigee's {@code apigee_organizations}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2OrganizationService {

    private final Wso2OrganizationRepository repository;
    private final Wso2Properties wso2Props;

    /** Called by BaseDiscoveryService / inventory after a successful run. */
    public void recordSeen(String companyName, String wso2Tenant,
                           String discoveryId, Integer revision,
                           String userEmail, String operation) {
        if (!StringUtils.hasText(companyName) || !StringUtils.hasText(wso2Tenant)) return;
        String id = companyName + "|" + wso2Tenant;
        Instant now = Instant.now();

        Optional<Wso2OrganizationEntity> existing = repository.findById(id);
        Wso2OrganizationEntity row = existing.orElseGet(() -> Wso2OrganizationEntity.builder()
                .id(id)
                .companyName(companyName)
                .wso2Tenant(wso2Tenant)
                .wso2BaseUrl(wso2Props.getBaseUrl())
                .firstSeenAt(now)
                .build());

        row.setLastSeenAt(now);
        if (discoveryId != null) row.setLastDiscoveryId(discoveryId);
        if (revision != null) row.setLastRevision(revision);
        if (userEmail != null) row.setLastUserEmail(userEmail);
        if (operation != null) row.setLastOperation(operation);

        try {
            repository.save(row);
        } catch (Exception e) {
            // never let an audit-style write fail the parent discovery
            log.warn("Failed to upsert wso2_organizations row for {}: {}", id, e.getMessage());
        }
    }

    public Optional<Wso2OrganizationEntity> get(String companyName, String wso2Tenant) {
        return repository.findById(companyName + "|" + wso2Tenant);
    }

    public Wso2OrganizationRepository repository() {
        return repository;
    }
}
