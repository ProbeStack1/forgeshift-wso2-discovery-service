package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.AppApiRelation;
import com.forgeshift.wso2discovery.service.AppApiRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read surface over the denormalized {@code app_api_relations} collection.
 *
 *   GET /relations                         - list paged (filter by companyName + wso2Tenant)
 *   GET /relations/by-app?applicationId=   - list rows for one application
 *   GET /relations/by-api?apiId=           - list rows for one API
 */
@Slf4j
@RestController
@RequestMapping("/relations")
@RequiredArgsConstructor
public class Wso2RelationsController {

    private final AppApiRelationService service;
    private final DiscoveryProperties discoveryProps;

    @GetMapping
    public Page<AppApiRelation> list(
            @RequestParam(required = false) String companyName,
            @RequestParam String wso2Tenant,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String c = StringUtils.hasText(companyName) ? companyName : discoveryProps.getDefaultCompanyName();
        return service.repository().findByCompanyNameAndWso2Tenant(c, wso2Tenant, PageRequest.of(page, size));
    }

    @GetMapping("/by-app")
    public ResponseEntity<List<AppApiRelation>> byApp(
            @RequestParam(required = false) String companyName,
            @RequestParam String wso2Tenant,
            @RequestParam String applicationId) {
        String c = StringUtils.hasText(companyName) ? companyName : discoveryProps.getDefaultCompanyName();
        return ResponseEntity.ok(
                service.repository().findByCompanyNameAndWso2TenantAndApplicationId(c, wso2Tenant, applicationId));
    }

    @GetMapping("/by-api")
    public ResponseEntity<List<AppApiRelation>> byApi(
            @RequestParam(required = false) String companyName,
            @RequestParam String wso2Tenant,
            @RequestParam String apiId) {
        String c = StringUtils.hasText(companyName) ? companyName : discoveryProps.getDefaultCompanyName();
        return ResponseEntity.ok(
                service.repository().findByCompanyNameAndWso2TenantAndApiId(c, wso2Tenant, apiId));
    }
}
