package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryResponse;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileHistoryResponse;
import com.forgeshift.wso2discovery.service.wso2.Wso2UserProfileDiscoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
     * Discovers WSO2 user profiles through SOAP and persists normalized migration-ready documents.
     */
    @PostMapping({"/users/discovery", "/user-profiles"})
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
    /**
     * Lists past user-profile discovery runs for one tenant.
     *
     * <p>{@code GET /wso2/history} cannot answer this: it aggregates the
     * per-resource {@code discovery_wso2_*} collections, and user profiles are
     * written to their own store. A run made here was previously unreadable by
     * anything, so the UI could only show full discovery runs and report zero
     * users against every one of them.
     */
    @GetMapping("/users/discovery/history")
    public ResponseEntity<Wso2UserProfileHistoryResponse> history(
            @RequestParam(required = false) String companyName,
            @RequestParam String wso2Tenant,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        String resolved = StringUtils.hasText(companyName)
                ? companyName : discoveryProperties.getDefaultCompanyName();
        log.info("[WSO2-USER-PROFILES] eventType=wso2_user_profile_history_requested companyName={} wso2Tenant={} limit={} methodName=history status=RECEIVED",
                resolved, wso2Tenant, limit);
        return ResponseEntity.ok(service.history(resolved, wso2Tenant, limit));
    }

    /**
     * Returns the users captured by one past run.
     */
    @GetMapping("/users/discovery/details")
    public ResponseEntity<Wso2UserProfileDiscoveryResponse> runDetails(
            @RequestParam(required = false) String companyName,
            @RequestParam String wso2Tenant,
            @RequestParam String requestTransactionId) {
        String resolved = StringUtils.hasText(companyName)
                ? companyName : discoveryProperties.getDefaultCompanyName();
        log.info("[WSO2-USER-PROFILES] eventType=wso2_user_profile_run_details_requested companyName={} wso2Tenant={} requestTransactionId={} methodName=runDetails status=RECEIVED",
                resolved, wso2Tenant, requestTransactionId);
        return ResponseEntity.ok(service.runDetails(resolved, wso2Tenant, requestTransactionId));
    }

    private void applyDefaults(Wso2UserProfileDiscoveryRequest request) {
        if (!StringUtils.hasText(request.getCompanyName())) {
            request.setCompanyName(discoveryProperties.getDefaultCompanyName());
        }
        if (!StringUtils.hasText(request.getSourceGateway())) {
            request.setSourceGateway("wso2");
        }
        if (!StringUtils.hasText(request.getTargetGateway())) {
            request.setTargetGateway("kong-konnect");
        }
    }
}
