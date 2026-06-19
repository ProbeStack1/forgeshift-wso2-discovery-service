package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.domain.Wso2KongRoleMappingDocument;
import com.forgeshift.wso2discovery.dto.Wso2ResolvedRoleMapping;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingEntryRequest;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingResolveRequest;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingResolveResponse;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingResult;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingUpsertRequest;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingUpsertResponse;
import com.forgeshift.wso2discovery.repository.Wso2KongRoleMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages and resolves WSO2 role to Kong role/group mapping rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2KongRoleMappingService {

    private static final String SOURCE_GATEWAY = "wso2";
    private static final String TARGET_GATEWAY = "kong";
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final Wso2KongRoleMappingRepository repository;

    /**
     * Parent orchestration method for bulk create/update role mappings.
     */
    public Wso2RoleMappingUpsertResponse upsertRoleMappings(Wso2RoleMappingUpsertRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_kong_role_mapping_upsert_started", request, "upsertRoleMappings", "STARTED", start, null);
        try {
            // Step 1: Validate request
            validateUpsertRequest(request);

            // Step 2: Normalize and check duplicate role names
            validateDuplicateMappings(request);

            // Step 3: Process each mapping independently
            List<Wso2RoleMappingResult> results = processMappings(request);

            // Step 4: Build final response
            Wso2RoleMappingUpsertResponse response = buildUpsertResponse(request, results);
            logEvent("wso2_kong_role_mapping_upsert_completed", request, "upsertRoleMappings", "SUCCESS", start, null);
            return response;
        } catch (RuntimeException ex) {
            logEvent("wso2_kong_role_mapping_upsert_failed", request, "upsertRoleMappings", "FAILED", start, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Parent orchestration method for resolving WSO2 roles to Kong mappings.
     */
    public Wso2RoleMappingResolveResponse resolveRoleMappings(Wso2RoleMappingResolveRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_kong_role_mapping_resolve_started", request, "resolveRoleMappings", "STARTED", start, null);
        try {
            // Step 1: Validate request
            validateResolveRequest(request);

            // Step 2: Normalize and check duplicate role names
            validateDuplicateRoles(request.getWso2Roles());

            // Step 3: Fetch mappings from MongoDB
            Map<String, Wso2KongRoleMappingDocument> mappings = fetchMappings(request);

            // Step 4: Build response in request order
            Wso2RoleMappingResolveResponse response = buildResolveResponse(request, mappings);
            logEvent("wso2_kong_role_mapping_resolve_completed", request, "resolveRoleMappings", "SUCCESS", start, null);
            return response;
        } catch (RuntimeException ex) {
            logEvent("wso2_kong_role_mapping_resolve_failed", request, "resolveRoleMappings", "FAILED", start, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Validates top-level and entry-level upsert fields.
     */
    private void validateUpsertRequest(Wso2RoleMappingUpsertRequest request) {
        validateBase(request);
        if (request.getRoleMappings() == null || request.getRoleMappings().isEmpty()) {
            throw new IllegalArgumentException("roleMappings must not be empty");
        }
        for (Wso2RoleMappingEntryRequest entry : request.getRoleMappings()) {
            if (!StringUtils.hasText(entry.getWso2RoleName())) {
                throw new IllegalArgumentException("wso2RoleName is required");
            }
            if (!StringUtils.hasText(entry.getKongRoleName())) {
                throw new IllegalArgumentException("kongRoleName is required");
            }
            if (!ACTIVE.equalsIgnoreCase(entry.getStatus()) && !INACTIVE.equalsIgnoreCase(entry.getStatus())) {
                throw new IllegalArgumentException("status must be ACTIVE or INACTIVE");
            }
        }
    }

    /**
     * Validates top-level resolve fields.
     */
    private void validateResolveRequest(Wso2RoleMappingResolveRequest request) {
        validateBase(request);
        if (request.getWso2Roles() == null || request.getWso2Roles().isEmpty()) {
            throw new IllegalArgumentException("wso2Roles must not be empty");
        }
    }

    /**
     * Validates common request fields shared by role mapping APIs.
     */
    private void validateBase(com.forgeshift.wso2discovery.dto.DiscoverResourceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!StringUtils.hasText(request.getCompanyName())) {
            throw new IllegalArgumentException("companyName is required");
        }
        if (!StringUtils.hasText(request.getWso2Tenant())) {
            throw new IllegalArgumentException("wso2Tenant is required");
        }
        if (!StringUtils.hasText(request.getRequestTransactionId())) {
            throw new IllegalArgumentException("requestTransactionId is required");
        }
    }

    /**
     * Rejects duplicate WSO2 role names in upsert payloads after normalization.
     */
    private void validateDuplicateMappings(Wso2RoleMappingUpsertRequest request) {
        validateDuplicateRoles(request.getRoleMappings().stream()
                .map(Wso2RoleMappingEntryRequest::getWso2RoleName)
                .collect(Collectors.toList()));
    }

    /**
     * Rejects duplicate WSO2 role names after trim/lowercase normalization.
     */
    private void validateDuplicateRoles(List<String> roles) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String role : roles) {
            String normalized = normalize(role);
            if (!StringUtils.hasText(normalized)) {
                throw new IllegalArgumentException("WSO2 role name must not be blank");
            }
            if (!seen.add(normalized)) {
                throw new IllegalArgumentException("Duplicate WSO2 role in request: " + role);
            }
        }
    }

    /**
     * Processes each mapping and continues when an individual mapping fails.
     */
    private List<Wso2RoleMappingResult> processMappings(Wso2RoleMappingUpsertRequest request) {
        List<Wso2RoleMappingResult> results = new ArrayList<>();
        for (Wso2RoleMappingEntryRequest entry : request.getRoleMappings()) {
            try {
                results.add(processSingleMapping(request, entry));
            } catch (Exception ex) {
                results.add(Wso2RoleMappingResult.builder()
                        .wso2RoleName(trim(entry.getWso2RoleName()))
                        .kongRoleName(trim(entry.getKongRoleName()))
                        .operationType("FAILED")
                        .status("FAILED")
                        .errorMessage(sanitize(ex.getMessage()))
                        .build());
            }
        }
        return results;
    }

    /**
     * Creates or updates one mapping document based on the business key.
     */
    private Wso2RoleMappingResult processSingleMapping(Wso2RoleMappingUpsertRequest request,
                                                       Wso2RoleMappingEntryRequest entry) {
        String normalizedRole = normalize(entry.getWso2RoleName());
        Wso2KongRoleMappingDocument existing = repository.findByBusinessKey(
                request.getCompanyName(), request.getWso2Tenant(), kongControlPlane(request), normalizedRole);
        String operationType = existing == null ? "CREATED" : "UPDATED";
        Wso2KongRoleMappingDocument document = buildDocument(request, entry, existing, normalizedRole);
        repository.upsert(document);
        return Wso2RoleMappingResult.builder()
                .mappingId(document.getMappingId())
                .wso2RoleName(document.getWso2RoleName())
                .kongRoleName(document.getKongRoleName())
                .operationType(operationType)
                .status("SUCCESS")
                .build();
    }

    /**
     * Builds a new or updated role mapping document.
     */
    private Wso2KongRoleMappingDocument buildDocument(Wso2RoleMappingUpsertRequest request,
                                                      Wso2RoleMappingEntryRequest entry,
                                                      Wso2KongRoleMappingDocument existing,
                                                      String normalizedRole) {
        Instant now = Instant.now();
        return Wso2KongRoleMappingDocument.builder()
                .mappingId(existing != null ? existing.getMappingId() : "WSO2-KONG-MAP-" + UUID.randomUUID())
                .companyName(request.getCompanyName())
                .sourceGateway(SOURCE_GATEWAY)
                .targetGateway(TARGET_GATEWAY)
                .wso2Tenant(request.getWso2Tenant())
                .environment(request.getEnvironment())
                .kongControlPlane(kongControlPlane(request))
                .wso2RoleName(trim(entry.getWso2RoleName()))
                .wso2RoleNameNormalized(normalizedRole)
                .kongRoleName(trim(entry.getKongRoleName()))
                .scopeType(defaultIfBlank(entry.getScopeType(), "GLOBAL"))
                .status(trim(entry.getStatus()).toUpperCase())
                .createdBy(existing != null ? existing.getCreatedBy() : request.getUserEmail())
                .createdDate(existing != null ? existing.getCreatedDate() : now)
                .updatedBy(request.getUserEmail())
                .updatedDate(now)
                .build();
    }

    /**
     * Fetches mappings and indexes them by normalized role name.
     */
    private Map<String, Wso2KongRoleMappingDocument> fetchMappings(Wso2RoleMappingResolveRequest request) {
        List<String> normalizedRoles = request.getWso2Roles().stream().map(this::normalize).collect(Collectors.toList());
        return repository.findByNormalizedRoles(request.getCompanyName(), request.getWso2Tenant(),
                        kongControlPlane(request), normalizedRoles)
                .stream()
                .collect(Collectors.toMap(Wso2KongRoleMappingDocument::getWso2RoleNameNormalized,
                        document -> document,
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    /**
     * Builds resolve response with totals and request-order role items.
     */
    private Wso2RoleMappingResolveResponse buildResolveResponse(Wso2RoleMappingResolveRequest request,
                                                                Map<String, Wso2KongRoleMappingDocument> mappings) {
        List<Wso2ResolvedRoleMapping> roles = request.getWso2Roles().stream()
                .map(role -> resolvedRole(role, mappings.get(normalize(role))))
                .collect(Collectors.toList());
        int mapped = (int) roles.stream().filter(role -> "MAPPED".equals(role.getMappingStatus())).count();
        int inactive = (int) roles.stream().filter(role -> "INACTIVE_MAPPING".equals(role.getMappingStatus())).count();
        int unmapped = (int) roles.stream().filter(role -> "MAPPING_REQUIRED".equals(role.getMappingStatus())).count();
        return Wso2RoleMappingResolveResponse.builder()
                .requestTransactionId(request.getRequestTransactionId())
                .companyName(request.getCompanyName())
                .sourceGateway(SOURCE_GATEWAY)
                .targetGateway(TARGET_GATEWAY)
                .orgName(request.getWso2Tenant())
                .environment(request.getEnvironment())
                .totalRequestedRoles(roles.size())
                .mappedRoles(mapped)
                .unmappedRoles(unmapped)
                .inactiveMappings(inactive)
                .roles(roles)
                .build();
    }

    /**
     * Builds one resolve response item.
     */
    private Wso2ResolvedRoleMapping resolvedRole(String requestedRole, Wso2KongRoleMappingDocument mapping) {
        if (mapping == null) {
            return Wso2ResolvedRoleMapping.builder()
                    .wso2RoleName(trim(requestedRole))
                    .mappingStatus("MAPPING_REQUIRED")
                    .build();
        }
        boolean active = ACTIVE.equalsIgnoreCase(mapping.getStatus());
        return Wso2ResolvedRoleMapping.builder()
                .mappingId(mapping.getMappingId())
                .wso2RoleName(mapping.getWso2RoleName())
                .kongRoleName(active ? mapping.getKongRoleName() : null)
                .scopeType(active ? mapping.getScopeType() : null)
                .mappingStatus(active ? "MAPPED" : "INACTIVE_MAPPING")
                .build();
    }

    /**
     * Builds the upsert response with aggregate counts.
     */
    private Wso2RoleMappingUpsertResponse buildUpsertResponse(Wso2RoleMappingUpsertRequest request,
                                                              List<Wso2RoleMappingResult> results) {
        int created = (int) results.stream().filter(result -> "CREATED".equals(result.getOperationType())).count();
        int updated = (int) results.stream().filter(result -> "UPDATED".equals(result.getOperationType())).count();
        int failed = (int) results.stream().filter(result -> "FAILED".equals(result.getStatus())).count();
        return Wso2RoleMappingUpsertResponse.builder()
                .requestTransactionId(request.getRequestTransactionId())
                .companyName(request.getCompanyName())
                .sourceGateway(SOURCE_GATEWAY)
                .targetGateway(TARGET_GATEWAY)
                .orgName(request.getWso2Tenant())
                .environment(request.getEnvironment())
                .totalRequested(results.size())
                .totalCreated(created)
                .totalUpdated(updated)
                .totalFailed(failed)
                .overallStatus(overallStatus(results.size(), failed))
                .results(results)
                .build();
    }

    /**
     * Resolves aggregate status from total and failed counts.
     */
    private String overallStatus(int total, int failed) {
        if (failed == 0) {
            return "SUCCESS";
        }
        return failed == total ? "FAILED" : "PARTIAL_SUCCESS";
    }

    private String kongControlPlane(Wso2RoleMappingUpsertRequest request) {
        return defaultIfBlank(request.getKongControlPlane(), "default");
    }

    private String kongControlPlane(Wso2RoleMappingResolveRequest request) {
        return defaultIfBlank(request.getKongControlPlane(), "default");
    }

    private String normalize(String value) {
        return trim(value).toLowerCase();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String sanitize(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[\\r\\n\\t]+", " ") : "Unexpected error";
    }

    /**
     * Writes structured logs for role mapping lifecycle events.
     */
    private void logEvent(String eventType,
                          com.forgeshift.wso2discovery.dto.DiscoverResourceRequest request,
                          String methodName,
                          String status,
                          long start,
                          String errorMessage) {
        log.info("[WSO2-KONG-ROLE-MAPPING] eventType={} companyName={} wso2Tenant={} environment={} requestTransactionId={} methodName={} status={} processingTimeMs={} errorMessage={}",
                eventType,
                request != null ? request.getCompanyName() : null,
                request != null ? request.getWso2Tenant() : null,
                request != null ? request.getEnvironment() : null,
                request != null ? request.getRequestTransactionId() : null,
                methodName,
                status,
                System.currentTimeMillis() - start,
                errorMessage != null ? sanitize(errorMessage) : "");
    }
}
