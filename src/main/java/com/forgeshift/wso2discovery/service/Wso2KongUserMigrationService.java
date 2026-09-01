package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.client.KongAdminClient;
import com.forgeshift.wso2discovery.client.KonnectCredentials;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
    private static final String INVALID_MAPPING_REASON =
            "Creation failed due to invalid role mapping. Kong role is required for migration.";
    private static final String NO_ROLES_REASON = "No mapped Kong roles supplied";
    private static final int MAX_ERROR_BODY = 300;

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ALREADY_EXISTS = "ALREADY_EXISTS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";

    private final Wso2KongUserMigrationRepository repository;
    private final KongAdminClient kongAdminClient;
    private final KonnectProfileReader konnectProfileReader;

    /**
     * Parent orchestration method for migrating WSO2 users to Kong.
     */
    public Wso2UserMigrationResponse migrateUsers(Wso2UserMigrationRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_kong_user_migration_started", request, "migrateUsers", "STARTED", start, null);
        try {
            // Step 1: Validate request
            validateMigrationRequest(request);

            // Step 2: Resolve the target control planes. Users are org-wide, so
            // with none named this is every control plane on the profile.
            List<KonnectCredentials> targets = konnectProfileReader.resolveTargets(
                    request.getCompanyName(), request.getProfileName(), request.getKongControlPlane());

            // Step 3: Create consumers and assign ACL groups in each control plane
            List<Wso2UserMigrationResult> results = processTargets(request, targets);

            // Step 4: Save one migration status document per user-role
            saveResults(request, results);

            // Step 5: Build final migration response
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
     * Applies the whole user set to every target control plane.
     *
     * <p>A user is not scoped to one environment, so the same person is created
     * in each control plane. One row is produced per user, role and control
     * plane, which is what the status collection is keyed on.
     */
    private List<Wso2UserMigrationResult> processTargets(Wso2UserMigrationRequest request,
                                                         List<KonnectCredentials> targets) {
        List<Wso2UserMigrationResult> results = new ArrayList<>();
        for (KonnectCredentials credentials : targets) {
            logTarget(request, credentials);
            for (Wso2UserMigrationUserRequest user : request.getUsers()) {
                results.addAll(processUser(user, request.getWso2Tenant(), credentials));
            }
        }
        return results;
    }

    /**
     * Records which control plane a batch is being applied to, so a run
     * spanning several is traceable in the logs.
     */
    private void logTarget(Wso2UserMigrationRequest request, KonnectCredentials credentials) {
        log.info("[WSO2-KONG-USER-MIGRATION] eventType=wso2_kong_user_migration_control_plane companyName={} "
                        + "requestTransactionId={} controlPlaneId={} controlPlaneName={} userCount={}",
                request.getCompanyName(), request.getRequestTransactionId(),
                credentials.getControlPlaneId(), credentials.getControlPlaneName(), request.getUsers().size());
    }

    /**
     * Creates the Kong consumer for one WSO2 user, then assigns each mapped
     * role to it as an ACL group.
     *
     * <p>The consumer is created once per user rather than once per role, so a
     * user with three roles produces one consumer and three ACL assignments.
     * Every returned row still represents one user-role pair, which is what the
     * response counts and the history collection are keyed on.
     */
    private List<Wso2UserMigrationResult> processUser(Wso2UserMigrationUserRequest user,
                                                      String wso2Tenant,
                                                      KonnectCredentials credentials) {
        KongAdminClient.ConsumerRef consumer;
        try {
            consumer = kongAdminClient.ensureConsumer(credentials, wso2Tenant, user.getUserName());
        } catch (RuntimeException ex) {
            // The consumer is a prerequisite for every ACL, so a failure here
            // fails all rows for that user instead of half-applying them.
            return failedRowsForUser(credentials, user, failureMessage("Kong consumer creation failed", ex));
        }

        String migrationStatus = consumer.getOutcome() == KongAdminClient.WriteOutcome.CREATED
                ? STATUS_SUCCESS : STATUS_ALREADY_EXISTS;
        // Prefer the uuid when Kong returned one, else the namespaced username
        // the client actually created - never the raw WSO2 name, which is not
        // what the consumer is called in Kong.
        String consumerRef = StringUtils.hasText(consumer.getId()) ? consumer.getId() : consumer.getUsername();

        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return List.of(buildResult(credentials, user, null, migrationStatus, STATUS_SKIPPED, NO_ROLES_REASON));
        }

        List<Wso2UserMigrationResult> rows = new ArrayList<>();
        for (Wso2UserMigrationRoleRequest role : user.getRoles()) {
            rows.add(assignRole(user, role, credentials, consumerRef, migrationStatus));
        }
        return rows;
    }

    /**
     * Assigns one Kong ACL group and maps the outcome onto a result row.
     */
    private Wso2UserMigrationResult assignRole(Wso2UserMigrationUserRequest user,
                                               Wso2UserMigrationRoleRequest role,
                                               KonnectCredentials credentials,
                                               String consumerRef,
                                               String migrationStatus) {
        if (role == null || !StringUtils.hasText(role.getKongRoleName())) {
            return buildResult(credentials, user, role, migrationStatus, STATUS_FAILED, INVALID_MAPPING_REASON);
        }
        try {
            KongAdminClient.WriteOutcome outcome =
                    kongAdminClient.assignGroup(credentials, consumerRef, role.getKongRoleName());
            String assignmentStatus = outcome == KongAdminClient.WriteOutcome.CREATED
                    ? STATUS_SUCCESS : STATUS_ALREADY_EXISTS;
            return buildResult(credentials, user, role, migrationStatus, assignmentStatus, null);
        } catch (RuntimeException ex) {
            return buildResult(credentials, user, role, migrationStatus, STATUS_FAILED,
                    failureMessage("Kong role assignment failed", ex));
        }
    }

    /**
     * Builds one FAILED row per role when the consumer could not be created.
     */
    private List<Wso2UserMigrationResult> failedRowsForUser(KonnectCredentials credentials,
                                                            Wso2UserMigrationUserRequest user,
                                                            String message) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return List.of(buildResult(credentials, user, null, STATUS_FAILED, STATUS_FAILED, message));
        }
        return user.getRoles().stream()
                .map(role -> buildResult(credentials, user, role, STATUS_FAILED, STATUS_FAILED, message))
                .collect(Collectors.toList());
    }

    /**
     * Builds one user-role result row.
     */
    private Wso2UserMigrationResult buildResult(KonnectCredentials credentials,
                                                Wso2UserMigrationUserRequest user,
                                                Wso2UserMigrationRoleRequest role,
                                                String migrationStatus,
                                                String assignmentStatus,
                                                String errorMessage) {
        return Wso2UserMigrationResult.builder()
                .kongControlPlane(credentials.getControlPlaneId())
                .kongControlPlaneName(credentials.getControlPlaneName())
                .userName(user.getUserName())
                .userEmail(user.getUserEmail())
                .wso2RoleName(role != null ? role.getWso2RoleName() : null)
                .kongRoleName(role != null ? role.getKongRoleName() : null)
                .migrationStatus(migrationStatus)
                .assignmentStatus(assignmentStatus)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * Turns a Kong failure into a message naming the HTTP status and body,
     * since a bare exception message hides which of auth, control plane or
     * payload was actually wrong.
     */
    private String failureMessage(String prefix, RuntimeException ex) {
        if (ex instanceof WebClientResponseException wcre) {
            String body = wcre.getResponseBodyAsString();
            if (StringUtils.hasText(body) && body.length() > MAX_ERROR_BODY) {
                body = body.substring(0, MAX_ERROR_BODY) + "...";
            }
            return prefix + ": HTTP " + wcre.getStatusCode().value()
                    + (StringUtils.hasText(body) ? " " + body : "");
        }
        return prefix + ": " + ex.getMessage();
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
        // The control plane is part of the key: the same user and role is now
        // applied to every control plane, and without it those rows would
        // share an id and overwrite each other, leaving only the last one.
        String id = String.join("|",
                request.getCompanyName(),
                request.getWso2Tenant(),
                request.getRequestTransactionId(),
                safe(result.getKongControlPlane()),
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
                .kongControlPlane(defaultIfBlank(result.getKongControlPlane(),
                        defaultIfBlank(request.getKongControlPlane(), "default")))
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
        int success = (int) results.stream().filter(result -> STATUS_SUCCESS.equals(rowStatus(result))).count();
        int alreadyExists = (int) results.stream().filter(result -> STATUS_ALREADY_EXISTS.equals(rowStatus(result))).count();
        int failed = (int) results.stream().filter(result -> STATUS_FAILED.equals(rowStatus(result))).count();
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
                .kongControlPlane(document.getKongControlPlane())
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
     * Collapses one row down to a single outcome for the response counters.
     *
     * <p>A row covers two writes - the consumer and its ACL group - so either
     * one failing makes the row a failure. A row only counts as ALREADY_EXISTS
     * when nothing at all had to change; if the consumer was already there but
     * the group was newly assigned, real work happened and it counts as
     * success. A user with no mapped roles is judged on the consumer alone.
     */
    private String rowStatus(Wso2UserMigrationResult result) {
        String migration = result.getMigrationStatus();
        String assignment = result.getAssignmentStatus();
        if (STATUS_FAILED.equals(migration) || STATUS_FAILED.equals(assignment)) {
            return STATUS_FAILED;
        }
        if (STATUS_SKIPPED.equals(assignment)) {
            return migration;
        }
        if (STATUS_ALREADY_EXISTS.equals(migration) && STATUS_ALREADY_EXISTS.equals(assignment)) {
            return STATUS_ALREADY_EXISTS;
        }
        return STATUS_SUCCESS;
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
