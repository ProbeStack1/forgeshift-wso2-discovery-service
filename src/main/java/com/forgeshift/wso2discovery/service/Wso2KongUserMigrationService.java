package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.client.KonnectWriteOutcome;
import com.forgeshift.wso2discovery.client.KonnectCredentials;
import com.forgeshift.wso2discovery.client.KonnectIdentityClient;
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
import java.util.Locale;
import java.util.Map;
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
    private static final String NO_EMAIL_REASON =
            "No email address on the WSO2 user. Konnect identifies people by email, so this user cannot be matched.";
    private static final String NOT_IN_KONNECT_REASON =
            "No Konnect user with the email %s. Invite them, or let them sign in through your identity provider, then re-run.";
    private static final String NO_SUCH_TEAM_REASON =
            "No Konnect team named %s. Available teams: %s";
    private static final int MAX_ERROR_BODY = 300;

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ALREADY_EXISTS = "ALREADY_EXISTS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";

    private final Wso2KongUserMigrationRepository repository;
    private final KonnectIdentityClient konnectIdentityClient;
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

            // Step 3: Add users to the Konnect teams their roles map to.
            // Teams are organization-wide, so one credential covers them all -
            // there is no control plane in any identity path.
            List<Wso2UserMigrationResult> results = processUsers(request, targets.get(0));

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
     * The Konnect teams available to map WSO2 roles onto.
     */
    public List<KonnectIdentityClient.KonnectTeam> listKonnectTeams(String companyName, String profileName) {
        List<KonnectCredentials> targets = konnectProfileReader.resolveTargets(companyName, profileName, null);
        return konnectIdentityClient.listTeams(targets.get(0));
    }

    /**
     * How the Konnect organization signs people in.
     *
     * <p>Read before a run so the operator knows whether anyone can arrive in
     * Konnect at all. Without an identity provider every user resolves to
     * nothing, and a wall of failures hides what is really a precondition.
     */
    public KonnectIdentityClient.KonnectAuthSettings getKonnectAuthSettings(String companyName, String profileName) {
        List<KonnectCredentials> targets = konnectProfileReader.resolveTargets(companyName, profileName, null);
        return konnectIdentityClient.getAuthSettings(targets.get(0));
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
     * Adds each WSO2 user to the Konnect teams their roles map to.
     *
     * <p>Konnect teams are organization-wide, so unlike consumers there is no
     * control plane to fan out across: one pass covers the whole organization.
     *
     * <p>This does not create Konnect users. A person exists in Konnect only
     * once they have been invited or have signed in through the identity
     * provider; until then there is nothing to add to a team, and the row says
     * so rather than inventing an account.
     */
    private List<Wso2UserMigrationResult> processUsers(Wso2UserMigrationRequest request,
                                                       KonnectCredentials credentials) {
        Map<String, KonnectIdentityClient.KonnectTeam> teamsByName;
        try {
            teamsByName = konnectIdentityClient.listTeams(credentials).stream()
                    .filter(team -> StringUtils.hasText(team.getName()))
                    .collect(Collectors.toMap(
                            team -> team.getName().trim().toLowerCase(Locale.ROOT),
                            team -> team,
                            (first, second) -> first));
        } catch (RuntimeException ex) {
            String message = failureMessage("Could not read Konnect teams", ex);
            return request.getUsers().stream()
                    .flatMap(user -> failedRowsForUser(user, message).stream())
                    .collect(Collectors.toList());
        }

        log.info("[WSO2-KONG-USER-MIGRATION] eventType=wso2_kong_user_migration_teams_loaded companyName={} "
                        + "requestTransactionId={} teamCount={}",
                request.getCompanyName(), request.getRequestTransactionId(), teamsByName.size());

        List<Wso2UserMigrationResult> results = new ArrayList<>();
        for (Wso2UserMigrationUserRequest user : request.getUsers()) {
            results.addAll(processUser(user, credentials, teamsByName));
        }
        return results;
    }

    /**
     * Resolves one WSO2 user to a Konnect user by email, then joins the teams
     * their roles map to.
     */
    private List<Wso2UserMigrationResult> processUser(Wso2UserMigrationUserRequest user,
                                                      KonnectCredentials credentials,
                                                      Map<String, KonnectIdentityClient.KonnectTeam> teamsByName) {
        if (!StringUtils.hasText(user.getUserEmail())) {
            return failedRowsForUser(user, NO_EMAIL_REASON);
        }

        String konnectUserId;
        try {
            konnectUserId = konnectIdentityClient.findUserIdByEmail(credentials, user.getUserEmail());
        } catch (RuntimeException ex) {
            return failedRowsForUser(user, failureMessage("Konnect user lookup failed", ex));
        }
        if (!StringUtils.hasText(konnectUserId)) {
            return failedRowsForUser(user, String.format(NOT_IN_KONNECT_REASON, user.getUserEmail()));
        }

        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return List.of(buildResult(user, null, null, STATUS_SUCCESS, STATUS_SKIPPED, NO_ROLES_REASON));
        }

        List<Wso2UserMigrationResult> rows = new ArrayList<>();
        for (Wso2UserMigrationRoleRequest role : user.getRoles()) {
            rows.add(joinTeam(user, role, credentials, konnectUserId, teamsByName));
        }
        return rows;
    }

    /**
     * Adds the user to the one team this role maps to.
     */
    private Wso2UserMigrationResult joinTeam(Wso2UserMigrationUserRequest user,
                                             Wso2UserMigrationRoleRequest role,
                                             KonnectCredentials credentials,
                                             String konnectUserId,
                                             Map<String, KonnectIdentityClient.KonnectTeam> teamsByName) {
        if (role == null || !StringUtils.hasText(role.getKongRoleName())) {
            return buildResult(user, role, null, STATUS_SUCCESS, STATUS_FAILED, INVALID_MAPPING_REASON);
        }

        KonnectIdentityClient.KonnectTeam team =
                teamsByName.get(role.getKongRoleName().trim().toLowerCase(Locale.ROOT));
        if (team == null) {
            return buildResult(user, role, null, STATUS_SUCCESS, STATUS_FAILED,
                    String.format(NO_SUCH_TEAM_REASON, role.getKongRoleName(),
                            String.join(", ", new java.util.TreeSet<>(teamsByName.keySet()))));
        }

        try {
            KonnectWriteOutcome outcome =
                    konnectIdentityClient.addUserToTeam(credentials, team.getId(), konnectUserId);
            String assignmentStatus = outcome == KonnectWriteOutcome.CREATED
                    ? STATUS_SUCCESS : STATUS_ALREADY_EXISTS;
            return buildResult(user, role, team, STATUS_SUCCESS, assignmentStatus, null);
        } catch (RuntimeException ex) {
            return buildResult(user, role, team, STATUS_SUCCESS, STATUS_FAILED,
                    failureMessage("Adding the user to the Konnect team failed", ex));
        }
    }


    /**
     * Builds one FAILED row per role when the consumer could not be created.
     */
    private List<Wso2UserMigrationResult> failedRowsForUser(Wso2UserMigrationUserRequest user, String message) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return List.of(buildResult(user, null, null, STATUS_FAILED, STATUS_FAILED, message));
        }
        return user.getRoles().stream()
                .map(role -> buildResult(user, role, null, STATUS_FAILED, STATUS_FAILED, message))
                .collect(Collectors.toList());
    }

    /**
     * Builds one user-role result row.
     */
    private Wso2UserMigrationResult buildResult(Wso2UserMigrationUserRequest user,
                                                Wso2UserMigrationRoleRequest role,
                                                KonnectIdentityClient.KonnectTeam team,
                                                String migrationStatus,
                                                String assignmentStatus,
                                                String errorMessage) {
        return Wso2UserMigrationResult.builder()
                .konnectTeamId(team != null ? team.getId() : null)
                .konnectTeamName(team != null ? team.getName() : null)
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
                // Kept as run context. Konnect teams are organization-wide, so
                // this no longer identifies where the membership landed.
                .kongControlPlane(defaultIfBlank(request.getKongControlPlane(), "default"))
                .konnectTeamId(result.getKonnectTeamId())
                .konnectTeamName(result.getKonnectTeamName())
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
                .konnectTeamId(document.getKonnectTeamId())
                .konnectTeamName(document.getKonnectTeamName())
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
     * <p>A row is one team join. It counts as ALREADY_EXISTS when the person
     * was in that team already, so a re-run reports nothing changed rather
     * than claiming fresh work.
     */
    private String rowStatus(Wso2UserMigrationResult result) {
        String migration = result.getMigrationStatus();
        String assignment = result.getAssignmentStatus();
        if (STATUS_FAILED.equals(migration) || STATUS_FAILED.equals(assignment)) {
            return STATUS_FAILED;
        }
        // A user with no mapped roles had nothing to join, so the row is judged
        // on resolving the person alone.
        if (STATUS_SKIPPED.equals(assignment)) {
            return migration;
        }
        // Otherwise the row is one team join, and its outcome is the row's:
        // ALREADY_EXISTS means the person was in the team already.
        return assignment;
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
