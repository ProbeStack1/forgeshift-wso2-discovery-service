package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.domain.Wso2OrganizationEntity;
import com.forgeshift.wso2discovery.service.Wso2OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read surface over {@code wso2_organizations} — the auto-upserted list of
 * (companyName, wso2Tenant) pairs we have data for.
 *
 *   GET /organizations                                  — list paged
 *   GET /organizations/{companyName}/{wso2Tenant}       — get one
 */
@Slf4j
@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class Wso2OrganizationsController {

    private final Wso2OrganizationService service;

    @GetMapping
    public Page<Wso2OrganizationEntity> list(
            @RequestParam(required = false) String companyName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size);
        return StringUtils.hasText(companyName)
                ? service.repository().findByCompanyName(companyName, pr)
                : service.repository().findAll(pr);
    }

    @GetMapping("/{companyName}/{wso2Tenant}")
    public ResponseEntity<Wso2OrganizationEntity> get(@PathVariable String companyName,
                                                      @PathVariable String wso2Tenant) {
        return service.get(companyName, wso2Tenant)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
