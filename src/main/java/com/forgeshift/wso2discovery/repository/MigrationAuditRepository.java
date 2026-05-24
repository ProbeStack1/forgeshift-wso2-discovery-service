package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.domain.MigrationAuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MigrationAuditRepository extends MongoRepository<MigrationAuditEntry, String> {

    Page<MigrationAuditEntry> findByCompanyName(String companyName, Pageable pageable);

    Page<MigrationAuditEntry> findByCompanyNameAndWso2Tenant(
            String companyName, String wso2Tenant, Pageable pageable);

    Page<MigrationAuditEntry> findByStatus(String status, Pageable pageable);
}
