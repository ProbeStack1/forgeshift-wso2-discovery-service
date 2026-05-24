package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.domain.MigrationAuditEntry;
import com.forgeshift.wso2discovery.service.MigrationAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read surface over {@code wso2_migration_audit_info} — the async audit log.
 *
 *   GET /audit             — paged list with optional filters
 *   GET /audit/{id}        — single entry by Mongo _id
 */
@Slf4j
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class Wso2AuditController {

    private final MigrationAuditService service;

    @GetMapping
    public Page<MigrationAuditEntry> list(
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String wso2Tenant,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // Most-recent first
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt"));
        if (StringUtils.hasText(companyName) && StringUtils.hasText(wso2Tenant)) {
            return service.repository().findByCompanyNameAndWso2Tenant(companyName, wso2Tenant, pr);
        }
        if (StringUtils.hasText(companyName)) {
            return service.repository().findByCompanyName(companyName, pr);
        }
        if (StringUtils.hasText(status)) {
            return service.repository().findByStatus(status, pr);
        }
        return service.repository().findAll(pr);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MigrationAuditEntry> get(@PathVariable String id) {
        return service.repository().findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
