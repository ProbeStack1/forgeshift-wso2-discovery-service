package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryResponse;
import com.forgeshift.wso2discovery.service.wso2.Wso2UserProfileDiscoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dedicated user-profile discovery endpoint for WSO2 to Kong user migration.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class Wso2UserProfilesController {

    private final Wso2UserProfileDiscoveryService service;
    private final DiscoveryProperties discoveryProperties;

    /**
     * Discovers WSO2 SCIM user profiles and persists normalized migration-ready documents.
     */
    @PostMapping("/user-profiles")
    public ResponseEntity<Wso2UserProfileDiscoveryResponse> discoverUserProfiles(
            @Valid @RequestBody Wso2UserProfileDiscoveryRequest request) {
        applyDefaults(request);
        log.info("[WSO2-USER-PROFILES] eventType=wso2_user_profile_request_received companyName={} wso2Tenant={} environment={} requestTransactionId={} methodName=discoverUserProfiles status=RECEIVED",
                request.getCompanyName(), request.getWso2Tenant(), request.getEnvironment(), request.getRequestTransactionId());
        return ResponseEntity.ok(service.discoverUserProfiles(request));
    }

    /**
     * Applies the configured default company name when callers omit it.
     */
    private void applyDefaults(Wso2UserProfileDiscoveryRequest request) {
        if (!StringUtils.hasText(request.getCompanyName())) {
            request.setCompanyName(discoveryProperties.getDefaultCompanyName());
        }
    }
}
