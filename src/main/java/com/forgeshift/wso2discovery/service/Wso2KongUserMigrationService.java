package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.domain.Wso2KongUserMigrationDocument;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationHistoryResponse;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationResponse;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationResult;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationRoleRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationUserRequest;
import com.forgeshift.wso2discovery.repository.Wso2KongUserMigrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates WSO2 user migration and Kong role/group assignment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2KongUserMigrationService {

    private static final String SOURCE_GATEWAY = "wso2";
    private static final String TARGET_GATEWAY = "kong";
    private static final String MIGRATION_DISABLED_REASON =
            "Kong user migration is not enabled. IDP is not configured to migrate WSO2 users to Kong.";
    private static final String INVALID_MAPPING_REASON =
            "Creation failed due to invalid role mapping. Kong role is required for migration.";

    private final Wso2KongUserMigrationRepository repository;

    /**
     * Parent orchestration method for migrating WSO2 users to Kong.
     */
    public Wso2UserMigrationResponse migrateUsers(Wso2UserMigrationRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_kong_user_migration_started", request, "migrateUsers", "STARTED", start, null);
        try {
            // Step 1: Validate request
            validateMigrationRequest(request);

            // Step 2: Process each user-role migration assignment
            List<Wso2UserMigrationResult> results = processUsers(request);

            // Step 3: Save one migration status document per user-role
            saveResults(request, results);

            // Step 4: Build final migration response
            Wso2UserMigrationResponse response = buildResponse(request, results);
            logEvent("wso2_kong_user_migration_completed", request, "migrateUsers", "SUCCESS", start, null);
            return response;
        } catch (RuntimeException ex) {
            logEvent("wso2_kong_user_migration_failed", request, "migrateUsers", "FAILED", start, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Parent orchestration method for reading migration history.
     */
    public Wso2UserMigrationHistoryResponse getMigrationHistory(String companyName,
                                                                String wso2Tenant,
                                                                String environment,
                                                                String requestTransactionId,
                                                                int limit) {
        if (!StringUtils.hasText(companyName) || !StringUtils.hasText(wso2Tenant)) {
            throw new IllegalArgumentException("companyName and wso2Tenant are required");
        }
        List<Wso2KongUserMigrationDocument> documents = repository.findHistory(
                companyName, wso2Tenant, environment, requestTransactionId, limit);
        List<Wso2UserMigrationResult> records = documents.stream()
                .map(this::toResult)
                .collect(Collectors.toList());
        return Wso2UserMigrationHistoryResponse.builder()
                .companyName(companyName)
                .wso2Tenant(wso2Tenant)
                .environment(environment)
                .totalRecords(records.size())
                .records(records)
                .build();
    }

    /**
     * Validates migration request and nested user/role values.
     */
    private void validateMigrationRequest(Wso2UserMigrationRequest request) {
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
        if (request.getUsers() == null || request.getUsers().isEmpty()) {
            throw new IllegalArgumentException("users must not be empty");
        }
        for (Wso2UserMigrationUserRequest user : request.getUsers()) {
            if (!StringUtils.hasText(user.getUserName())) {
                throw new IllegalArgumentException("userName is required");
            }
        }
    }

    /**
     * Processes every requested user and role assignment.
     */
    private List<Wso2UserMigrationResult> processUsers(Wso2UserMigrationRequest request) {
        List<Wso2UserMigrationResult> results = new ArrayList<>();
        for (Wso2UserMigrationUserRequest user : request.getUsers()) {
            if (user.getRoles() == null || user.getRoles().isEmpty()) {
                results.add(skippedResult(user, null, "No mapped Kong roles supplied"));
                continue;
            }
            for (Wso2UserMigrationRoleRequest role : user.getRoles()) {
                results.add(processSingleAssignment(user, role));
            }
        }
        return results;
    }

    /**
     * Builds a demo-safe result for one user-role assignment without invoking
     * Kong Admin API until IDP and migration configuration are finalized.
     */
    private Wso2UserMigrationResult processSingleAssignment(Wso2UserMigrationUserRequest user,
                                                            Wso2UserMigrationRoleRequest role) {
        String errorMessage = migrationErrorMessage(role);
        return Wso2UserMigrationResult.builder()
                .userName(user.getUserName())
                .userEmail(user.getUserEmail())
                .wso2RoleName(role.getWso2RoleName())
                .kongRoleName(role.getKongRoleName())
                .migrationStatus("FAILED")
                .assignmentStatus("FAILED")
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * Returns the most useful user-facing failure reason for the assignment.
     */
    private String migrationErrorMessage(Wso2UserMigrationRoleRequest role) {
        if (role == null || !StringUtils.hasText(role.getKongRoleName())) {
            return INVALID_MAPPING_REASON;
        }
        return MIGRATION_DISABLED_REASON;
    }

    /**
     * Builds a skipped migration result for a user without mapped roles.
     */
    private Wso2UserMigrationResult skippedResult(Wso2UserMigrationUserRequest user,
                                                  Wso2UserMigrationRoleRequest role,
                                                  String message) {
        return Wso2UserMigrationResult.builder()
                .userName(user.getUserName())
                .userEmail(user.getUserEmail())
                .wso2RoleName(role != null ? role.getWso2RoleName() : null)
                .kongRoleName(role != null ? role.getKongRoleName() : null)
                .migrationStatus("SKIPPED")
                .assignmentStatus("SKIPPED")
                .errorMessage(message)
                .build();
    }

    /**
     * Persists every per-user-role migration result.
     */
    private void saveResults(Wso2UserMigrationRequest request, List<Wso2UserMigrationResult> results) {
        Instant now = Instant.now();
        List<Wso2KongUserMigrationDocument> documents = results.stream()
                .map(result -> toDocument(request, result, now))
                .collect(Collectors.toList());
        repository.upsertAll(documents);
    }

    /**
     * Converts one migration result into a MongoDB status document.
     */
    private Wso2KongUserMigrationDocument toDocument(Wso2UserMigrationRequest request,
                                                     Wso2UserMigrationResult result,
                                                     Instant now) {
        String id = String.join("|",
                request.getCompanyName(),
                request.getWso2Tenant(),
                request.getRequestTransactionId(),
                safe(result.getUserName()),
                safe(result.getKongRoleName()));
        return Wso2KongUserMigrationDocument.builder()
                .id(id)
                .migrationId("WSO2-KONG-MIG-" + UUID.randomUUID())
                .companyName(request.getCompanyName())
                .sourceGateway(sourceGateway(request))
                .targetGateway(targetGateway(request))
                .wso2Tenant(request.getWso2Tenant())
                .environment(request.getEnvironment())
                .requestTransactionId(request.getRequestTransactionId())
                .kongControlPlane(defaultIfBlank(request.getKongControlPlane(), "default"))
                .userName(result.getUserName())
                .userEmail(result.getUserEmail())
                .wso2RoleName(result.getWso2RoleName())
                .kongRoleName(result.getKongRoleName())
                .migrationStatus(result.getMigrationStatus())
                .assignmentStatus(result.getAssignmentStatus())
                .errorMessage(result.getErrorMessage())
                .requestedBy(request.getUserEmail())
                .createdDate(now)
                .updatedDate(now)
                .build();
    }

    /**
     * Builds the migration API response with aggregate counts.
     */
    private Wso2UserMigrationResponse buildResponse(Wso2UserMigrationRequest request,
                                                    List<Wso2UserMigrationResult> results) {
        int success = (int) results.stream().filter(result -> "SUCCESS".equals(result.getMigrationStatus())).count();
        int alreadyExists = (int) results.stream().filter(result -> "ALREADY_EXISTS".equals(result.getMigrationStatus())).count();
        int failed = (int) results.stream().filter(result -> "FAILED".equals(result.getMigrationStatus())
                || "SKIPPED".equals(result.getMigrationStatus())).count();
        return Wso2UserMigrationResponse.builder()
                .requestTransactionId(request.getRequestTransactionId())
                .companyName(request.getCompanyName())
                .sourceGateway(sourceGateway(request))
                .targetGateway(targetGateway(request))
                .orgName(request.getWso2Tenant())
                .environment(request.getEnvironment())
                .totalRequested(results.size())
                .totalSuccess(success)
                .totalAlreadyExists(alreadyExists)
                .totalFailed(failed)
                .overallStatus(overallStatus(results.size(), failed))
                .results(results)
                .build();
    }

    /**
     * Returns the caller-provided source gateway or the WSO2 default.
     */
    private String sourceGateway(Wso2UserMigrationRequest request) {
        return StringUtils.hasText(request.getSourceGateway()) ? request.getSourceGateway().trim() : SOURCE_GATEWAY;
    }

    /**
     * Returns the caller-provided target gateway or the Kong default.
     */
    private String targetGateway(Wso2UserMigrationRequest request) {
        return StringUtils.hasText(request.getTargetGateway()) ? request.getTargetGateway().trim() : TARGET_GATEWAY;
    }

    /**
     * Projects a history document into a response result item.
     */
    private Wso2UserMigrationResult toResult(Wso2KongUserMigrationDocument document) {
        return Wso2UserMigrationResult.builder()
                .userName(document.getUserName())
                .userEmail(document.getUserEmail())
                .wso2RoleName(document.getWso2RoleName())
                .kongRoleName(document.getKongRoleName())
                .migrationStatus(document.getMigrationStatus())
                .assignmentStatus(document.getAssignmentStatus())
                .errorMessage(document.getErrorMessage())
                .build();
    }

    /**
     * Resolves overall status from failed count.
     */
    private String overallStatus(int total, int failed) {
        if (failed == 0) {
            return "SUCCESS";
        }
        return failed == total ? "FAILED" : "PARTIAL_SUCCESS";
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "none";
    }

    private String sanitize(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[\\r\\n\\t]+", " ") : "Unexpected error";
    }

    /**
     * Writes structured logs for migration lifecycle events.
     */
    private void logEvent(String eventType,
                          Wso2UserMigrationRequest request,
                          String methodName,
                          String status,
                          long start,
                          String errorMessage) {
        log.info("[WSO2-KONG-USER-MIGRATION] eventType={} companyName={} wso2Tenant={} environment={} requestTransactionId={} methodName={} status={} processingTimeMs={} errorMessage={}",
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
