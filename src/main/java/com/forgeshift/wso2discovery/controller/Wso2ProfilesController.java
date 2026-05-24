package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.domain.Wso2TenantProfile;
import com.forgeshift.wso2discovery.dto.Wso2TenantProfileDto;
import com.forgeshift.wso2discovery.service.Wso2TenantProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD for the multi-tenancy {@code profiles} collection.
 *
 *   POST   /profiles                                 — create / update
 *   GET    /profiles                                 — list (paged, optional companyName filter)
 *   GET    /profiles/{companyName}/{wso2Tenant}      — read one
 *   DELETE /profiles/{companyName}/{wso2Tenant}      — delete
 *
 * Secrets (password, clientSecret) are masked on every read.
 */
@Slf4j
@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
public class Wso2ProfilesController {

    private final Wso2TenantProfileService service;

    @PostMapping
    public ResponseEntity<Wso2TenantProfileDto> upsert(@Valid @RequestBody Wso2TenantProfileDto body) {
        log.info("POST /profiles company={} tenant={}", body.getCompanyName(), body.getWso2Tenant());
        Wso2TenantProfile saved = service.save(body.toDomain());
        return ResponseEntity.ok(Wso2TenantProfileDto.fromMasked(saved));
    }

    @GetMapping
    public Page<Wso2TenantProfileDto> list(
            @RequestParam(required = false) String companyName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size);
        Page<Wso2TenantProfile> rows = StringUtils.hasText(companyName)
                ? service.repository().findByCompanyName(companyName, pr)
                : service.repository().findAll(pr);
        return rows.map(Wso2TenantProfileDto::fromMasked);
    }

    @GetMapping("/{companyName}/{wso2Tenant}")
    public ResponseEntity<Wso2TenantProfileDto> get(@PathVariable String companyName,
                                                    @PathVariable String wso2Tenant) {
        return service.get(companyName, wso2Tenant)
                .map(Wso2TenantProfileDto::fromMasked)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{companyName}/{wso2Tenant}")
    public ResponseEntity<Void> delete(@PathVariable String companyName,
                                       @PathVariable String wso2Tenant) {
        service.delete(companyName, wso2Tenant);
        return ResponseEntity.noContent().build();
    }
}
