package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingResolveRequest;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingResolveResponse;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingUpsertRequest;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingUpsertResponse;
import com.forgeshift.wso2discovery.service.Wso2KongRoleMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for WSO2 role to Kong role/group mapping rules.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class Wso2KongRoleMappingController {

    private final Wso2KongRoleMappingService service;
    private final DiscoveryProperties discoveryProperties;

    /**
     * Creates or updates WSO2 to Kong role mapping rules.
     */
    @PostMapping("/role-mappings")
    public ResponseEntity<Wso2RoleMappingUpsertResponse> upsertRoleMappings(
            @Valid @RequestBody Wso2RoleMappingUpsertRequest request) {
        applyDefaults(request);
        log.info("[WSO2-KONG-ROLE-MAPPING] eventType=wso2_kong_role_mapping_request_received companyName={} wso2Tenant={} environment={} requestTransactionId={} methodName=upsertRoleMappings status=RECEIVED",
                request.getCompanyName(), request.getWso2Tenant(), request.getEnvironment(), request.getRequestTransactionId());
        return ResponseEntity.ok(service.upsertRoleMappings(request));
    }

    /**
     * Resolves discovered WSO2 roles to existing Kong role mappings.
     */
    @PostMapping("/role-mappings/resolve")
    public ResponseEntity<Wso2RoleMappingResolveResponse> resolveRoleMappings(
            @Valid @RequestBody Wso2RoleMappingResolveRequest request) {
        applyDefaults(request);
        log.info("[WSO2-KONG-ROLE-MAPPING] eventType=wso2_kong_role_mapping_resolve_request_received companyName={} wso2Tenant={} environment={} requestTransactionId={} methodName=resolveRoleMappings status=RECEIVED",
                request.getCompanyName(), request.getWso2Tenant(), request.getEnvironment(), request.getRequestTransactionId());
        return ResponseEntity.ok(service.resolveRoleMappings(request));
    }

    /**
     * Applies configured default company name when omitted by callers.
     */
    private void applyDefaults(com.forgeshift.wso2discovery.dto.DiscoverResourceRequest request) {
        if (!StringUtils.hasText(request.getCompanyName())) {
            request.setCompanyName(discoveryProperties.getDefaultCompanyName());
        }
    }
}
