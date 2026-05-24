package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.ComparisonResponse;
import com.forgeshift.wso2discovery.service.Wso2ComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /wso2/compare — diff two discoveries by their discoveryIds.
 *
 * Mirrors the Apigee /apigee/compare endpoint. Required query params:
 *   wso2Tenant, sourceDiscoveryId, targetDiscoveryId
 * Optional: companyName (defaults to forgeshift.discovery.default-company-name),
 *           resourceType (limits the diff to one resource type)
 */
@Slf4j
@RestController
@RequestMapping("/wso2")
@RequiredArgsConstructor
public class Wso2ComparisonController {

    private final Wso2ComparisonService service;
    private final DiscoveryProperties discoveryProps;

    @GetMapping("/compare")
    public ResponseEntity<ComparisonResponse> compare(
            @RequestParam(required = false) String companyName,
            @RequestParam String wso2Tenant,
            @RequestParam String sourceDiscoveryId,
            @RequestParam String targetDiscoveryId,
            @RequestParam(required = false) String resourceType) {
        String c = StringUtils.hasText(companyName) ? companyName : discoveryProps.getDefaultCompanyName();
        log.info("GET /wso2/compare company={} tenant={} source={} target={} resourceType={}",
                c, wso2Tenant, sourceDiscoveryId, targetDiscoveryId, resourceType);
        return ResponseEntity.ok(service.compare(c, wso2Tenant, sourceDiscoveryId, targetDiscoveryId, resourceType));
    }
}
