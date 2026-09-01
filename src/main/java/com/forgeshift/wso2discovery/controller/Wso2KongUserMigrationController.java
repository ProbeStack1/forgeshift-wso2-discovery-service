package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.client.KonnectIdentityClient;
import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationHistoryResponse;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationResponse;
import com.forgeshift.wso2discovery.service.Wso2KongUserMigrationService;
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

import java.util.List;

/**
 * REST endpoints for WSO2 user migration and Kong role/group assignment.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class Wso2KongUserMigrationController {

    private final Wso2KongUserMigrationService service;
    private final DiscoveryProperties discoveryProperties;

    /**
     * Migrates WSO2 users to Kong and records per user-role migration status.
     */
    @PostMapping("/users/migration")
    public ResponseEntity<Wso2UserMigrationResponse> migrateUsers(
            @Valid @RequestBody Wso2UserMigrationRequest request) {
        applyDefaults(request);
        log.info("[WSO2-KONG-USER-MIGRATION] eventType=wso2_kong_user_migration_request_received companyName={} wso2Tenant={} environment={} requestTransactionId={} methodName=migrateUsers status=RECEIVED",
                request.getCompanyName(), request.getWso2Tenant(), request.getEnvironment(), request.getRequestTransactionId());
        return ResponseEntity.ok(service.migrateUsers(request));
    }

    /**
     * Lists the Konnect teams a WSO2 role can be mapped onto.
     *
     * <p>Konnect predefines these, so the mapping screen offers real team names
     * instead of free text that silently fails at migration time.
     */
    @GetMapping("/users/migration/konnect-teams")
    public ResponseEntity<List<KonnectIdentityClient.KonnectTeam>> listKonnectTeams(
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String profileName) {
        String resolvedCompanyName = StringUtils.hasText(companyName)
                ? companyName : discoveryProperties.getDefaultCompanyName();
        return ResponseEntity.ok(service.listKonnectTeams(resolvedCompanyName, profileName));
    }

    /**
     * Reports whether the Konnect organization has single sign-on configured.
     */
    @GetMapping("/users/migration/konnect-auth")
    public ResponseEntity<KonnectIdentityClient.KonnectAuthSettings> getKonnectAuthSettings(
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String profileName) {
        String resolvedCompanyName = StringUtils.hasText(companyName)
                ? companyName : discoveryProperties.getDefaultCompanyName();
        return ResponseEntity.ok(service.getKonnectAuthSettings(resolvedCompanyName, profileName));
    }

    /**
     * Returns WSO2 to Kong migration history records.
     */
    @GetMapping("/users/migration/history")
    public ResponseEntity<Wso2UserMigrationHistoryResponse> getMigrationHistory(
            @RequestParam(required = false) String companyName,
            @RequestParam String wso2Tenant,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String requestTransactionId,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        String resolvedCompanyName = StringUtils.hasText(companyName) ? companyName : discoveryProperties.getDefaultCompanyName();
        return ResponseEntity.ok(service.getMigrationHistory(resolvedCompanyName, wso2Tenant, environment, requestTransactionId, limit));
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
