package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.client.Wso2Credentials;
import com.forgeshift.wso2discovery.client.Wso2SoapUserProfile;
import com.forgeshift.wso2discovery.client.Wso2UserStoreSoapClient;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.domain.Wso2UserProfileDocument;
import com.forgeshift.wso2discovery.dto.Wso2DiscoveredUserRole;
import com.forgeshift.wso2discovery.dto.Wso2RolePermissionDetail;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDetail;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryResponse;
import com.forgeshift.wso2discovery.repository.Wso2UserProfileRepository;
import com.forgeshift.wso2discovery.service.Wso2TenantProfileService;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates SOAP-based WSO2 user discovery for WSO2 to Kong migration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2UserProfileDiscoveryService {

    private static final String RESOURCE_TYPE = "user-profiles";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String SOURCE_GATEWAY = "wso2";
    private static final String TARGET_GATEWAY = "kong";

    private final Wso2UserStoreSoapClient soapClient;
    private final Wso2TenantProfileService tenantProfileService;
    private final Wso2Properties wso2Properties;
    private final Wso2UserProfileRepository repository;

    /**
     * Parent orchestration method that discovers WSO2 users via SOAP, persists
     * normalized migration-ready documents, and builds the REST response.
     */
    public Wso2UserProfileDiscoveryResponse discoverUserProfiles(Wso2UserProfileDiscoveryRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_user_profile_soap_discovery_started", request, "discoverUserProfiles", "STARTED", start, null);
        try {
            // Step 1: Validate request
            validateRequest(request);

            // Step 2: Resolve WSO2 SOAP credentials
            Wso2Credentials credentials = resolveCredentials(request);

            // Step 3: Fetch users from WSO2 SOAP
            List<String> userNames = fetchUserNames(credentials, request);

            // Step 4: Fetch roles and claims for each user
            List<Wso2SoapUserProfile> profiles = fetchUserProfiles(credentials, userNames, request);

            // Step 5: Fetch permissions for each unique role
            Map<String, List<Wso2RolePermissionDetail>> rolePermissions = fetchRolePermissions(credentials, profiles, request);

            // Step 6: Normalize SOAP data into migration-ready documents
            List<Wso2UserProfileDocument> documents = transformProfiles(profiles, rolePermissions, request);

            // Step 7: Store normalized user profiles in MongoDB
            saveUserProfiles(documents, request);

            // Step 8: Build final response
            Wso2UserProfileDiscoveryResponse response = buildResponse(request, documents);
            logEvent("wso2_user_profile_soap_discovery_completed", request, "discoverUserProfiles", "SUCCESS", start, null);
            return response;
        } catch (RuntimeException ex) {
            logEvent("wso2_user_profile_soap_discovery_failed", request, "discoverUserProfiles", "FAILED", start, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Ensures the required tenant and transaction fields are present.
     */
    private void validateRequest(Wso2UserProfileDiscoveryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!StringUtils.hasText(request.getWso2Tenant())) {
            throw new IllegalArgumentException("wso2Tenant is required");
        }
        if (!StringUtils.hasText(request.getRequestTransactionId())) {
            throw new IllegalArgumentException("requestTransactionId is required");
        }
    }

    /**
     * Resolves per-company/per-tenant credentials for SOAP Basic Auth.
     */
    private Wso2Credentials resolveCredentials(Wso2UserProfileDiscoveryRequest request) {
        return tenantProfileService.resolve(request.getCompanyName(), request.getWso2Tenant());
    }

    /**
     * Calls SOAP listUsers. This is a top-level dependency and fails the request.
     */
    private List<String> fetchUserNames(Wso2Credentials credentials, Wso2UserProfileDiscoveryRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_user_profile_soap_list_users_started", request, "fetchUserNames", "STARTED", start, null);
        try {
            List<String> users = soapClient.listUsers(credentials);
            logEvent("wso2_user_profile_soap_list_users_completed", request, "fetchUserNames", "SUCCESS", start, null);
            return users;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fetch WSO2 users via SOAP: " + sanitize(ex.getMessage()), ex);
        }
    }

    /**
     * Builds a complete SOAP-derived profile for each user while preserving
     * partial success when one user's role or claim lookup fails.
     */
    private List<Wso2SoapUserProfile> fetchUserProfiles(Wso2Credentials credentials,
                                                        List<String> userNames,
                                                        Wso2UserProfileDiscoveryRequest request) {
        int parallelism = boundedParallelism(wso2Properties.getSoap().getUserDiscoveryParallelism(), userNames.size());
        return executeInParallel("wso2-user-profile-", parallelism, userNames,
                userName -> fetchSingleUserProfile(credentials, userName, request));
    }

    /**
     * Fetches roles and claims for one WSO2 user using SOAP operations.
     */
    private Wso2SoapUserProfile fetchSingleUserProfile(Wso2Credentials credentials,
                                                       String userName,
                                                       Wso2UserProfileDiscoveryRequest request) {
        List<String> roles = new ArrayList<>();
        Map<String, String> claims = new LinkedHashMap<>();
        String errorMessage = null;
        try {
            long start = System.currentTimeMillis();
            logEvent("wso2_user_profile_soap_user_roles_started", request, "fetchSingleUserProfile", "STARTED", start, null);
            roles = soapClient.getRoleListOfUser(credentials, userName);
        } catch (Exception ex) {
            errorMessage = appendError(errorMessage, "Role lookup failed: " + sanitize(ex.getMessage()));
        }
        try {
            long start = System.currentTimeMillis();
            logEvent("wso2_user_profile_soap_claims_started", request, "fetchSingleUserProfile", "STARTED", start, null);
            claims = soapClient.getUserClaimValues(credentials, userName);
        } catch (Exception ex) {
            errorMessage = appendError(errorMessage, "Claim lookup failed: " + sanitize(ex.getMessage()));
        }
        return Wso2SoapUserProfile.builder()
                .userName(userName)
                .roles(roles)
                .claims(claims)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * Fetches permissions once per unique role in this discovery request.
     * Permission lookup failures are non-fatal and return an empty permission list.
     */
    private Map<String, List<Wso2RolePermissionDetail>> fetchRolePermissions(Wso2Credentials credentials,
                                                                             List<Wso2SoapUserProfile> profiles,
                                                                             Wso2UserProfileDiscoveryRequest request) {
        if (!wso2Properties.getSoap().isIncludeRolePermissions()) {
            logEvent("wso2_user_profile_soap_role_permissions_skipped", request, "fetchRolePermissions", "SKIPPED",
                    System.currentTimeMillis(), "Role permission discovery disabled by configuration");
            return Map.of();
        }
        List<String> roleNames = new ArrayList<>(uniqueRoles(profiles));
        int parallelism = boundedParallelism(wso2Properties.getSoap().getRolePermissionParallelism(), roleNames.size());
        List<RolePermissionLookupResult> lookupResults = executeInParallel("wso2-role-permission-", parallelism, roleNames,
                roleName -> new RolePermissionLookupResult(roleName, fetchSingleRolePermissions(credentials, roleName, request)));
        Map<String, List<Wso2RolePermissionDetail>> permissionsByRole = new LinkedHashMap<>();
        for (RolePermissionLookupResult lookupResult : lookupResults) {
            permissionsByRole.put(lookupResult.roleName(), lookupResult.permissions());
        }
        return permissionsByRole;
    }

    /**
     * Runs independent SOAP lookups with bounded parallelism and returns results
     * in the same order as the input list.
     */
    private <T, R> List<R> executeInParallel(String threadNamePrefix,
                                             int parallelism,
                                             List<T> inputs,
                                             Function<T, R> task) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, namedThreadFactory(threadNamePrefix));
        try {
            List<CompletableFuture<R>> futures = inputs.stream()
                    .map(input -> CompletableFuture.supplyAsync(() -> task.apply(input), executor))
                    .toList();
            return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Keeps configured parallelism within a safe useful range for the workload.
     */
    private int boundedParallelism(int configuredParallelism, int itemCount) {
        if (itemCount <= 0) {
            return 1;
        }
        int safeConfiguredParallelism = Math.max(configuredParallelism, 1);
        return Math.min(safeConfiguredParallelism, itemCount);
    }

    /**
     * Creates clear thread names for troubleshooting parallel SOAP discovery.
     */
    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Calls SOAP UserAdmin#getRolePermissions for one role and applies partial
     * failure behavior by returning an empty list on lookup failure.
     */
    private List<Wso2RolePermissionDetail> fetchSingleRolePermissions(Wso2Credentials credentials,
                                                                      String roleName,
                                                                      Wso2UserProfileDiscoveryRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_user_profile_soap_role_permissions_started", request, "fetchSingleRolePermissions", "STARTED", start, null);
        try {
            List<Wso2RolePermissionDetail> permissions = soapClient.getRolePermissions(credentials, roleName);
            logEvent("wso2_user_profile_soap_role_permissions_completed", request, "fetchSingleRolePermissions", "SUCCESS", start, null);
            return permissions == null ? List.of() : permissions;
        } catch (Exception ex) {
            logEvent("wso2_user_profile_soap_role_permissions_failed", request, "fetchSingleRolePermissions", "FAILED", start,
                    "Permission lookup failed for role " + roleName + ": " + sanitize(ex.getMessage()));
            return List.of();
        }
    }

    /**
     * Returns role names in first-seen order so SOAP permission calls are
     * deterministic and duplicate role names are fetched once.
     */
    private Set<String> uniqueRoles(List<Wso2SoapUserProfile> profiles) {
        Set<String> uniqueRoles = new LinkedHashSet<>();
        if (profiles == null) {
            return uniqueRoles;
        }
        for (Wso2SoapUserProfile profile : profiles) {
            if (profile.getRoles() != null) {
                uniqueRoles.addAll(profile.getRoles());
            }
        }
        return uniqueRoles;
    }

    /**
     * Converts SOAP profiles into MongoDB documents.
     */
    private List<Wso2UserProfileDocument> transformProfiles(List<Wso2SoapUserProfile> profiles,
                                                            Map<String, List<Wso2RolePermissionDetail>> rolePermissions,
                                                            Wso2UserProfileDiscoveryRequest request) {
        Instant now = Instant.now();
        return profiles.stream()
                .map(profile -> toDocument(profile, rolePermissions, request, now))
                .collect(Collectors.toList());
    }

    /**
     * Converts one SOAP profile into a normalized persistence document.
     */
    private Wso2UserProfileDocument toDocument(Wso2SoapUserProfile profile,
                                               Map<String, List<Wso2RolePermissionDetail>> rolePermissions,
                                               Wso2UserProfileDiscoveryRequest request,
                                               Instant now) {
        String email = claim(profile, wso2Properties.getSoap().getEmailClaim());
        String firstName = claim(profile, wso2Properties.getSoap().getFirstNameClaim());
        String lastName = claim(profile, wso2Properties.getSoap().getLastNameClaim());
        return Wso2UserProfileDocument.builder()
                .id(documentId(request, profile.getUserName()))
                .companyName(request.getCompanyName())
                .sourceGateway(SOURCE_GATEWAY)
                .targetGateway(TARGET_GATEWAY)
                .wso2Tenant(request.getWso2Tenant())
                .environment(request.getEnvironment())
                .requestTransactionId(request.getRequestTransactionId())
                .sourceUserId(profile.getUserName())
                .userName(profile.getUserName())
                .firstName(firstName)
                .lastName(lastName)
                .displayName(displayName(firstName, lastName, profile.getUserName()))
                .primaryEmail(StringUtils.hasText(email) ? email : profile.getUserName())
                .emails(StringUtils.hasText(email) ? List.of(email) : List.of())
                .roles(profile.getRoles())
                .rolePermissions(filterRolePermissions(profile.getRoles(), rolePermissions))
                .errorMessage(profile.getErrorMessage())
                .rawPayload(rawPayload(profile, rolePermissions))
                .requestedBy(request.getUserEmail())
                .createdDate(now)
                .updatedDate(now)
                .build();
    }

    /**
     * Persists normalized user documents through the repository.
     */
    private void saveUserProfiles(List<Wso2UserProfileDocument> documents,
                                  Wso2UserProfileDiscoveryRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_user_profile_db_save_started", request, "saveUserProfiles", "STARTED", start, null);
        try {
            repository.upsertAll(documents);
            logEvent("wso2_user_profile_db_save_completed", request, "saveUserProfiles", "SUCCESS", start, null);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store WSO2 user profiles: " + sanitize(ex.getMessage()), ex);
        }
    }

    /**
     * Builds an Apigee-style migration discovery response for UI consumption.
     */
    private Wso2UserProfileDiscoveryResponse buildResponse(Wso2UserProfileDiscoveryRequest request,
                                                           List<Wso2UserProfileDocument> documents) {
        return Wso2UserProfileDiscoveryResponse.builder()
                .companyName(request.getCompanyName())
                .sourceGateway(SOURCE_GATEWAY)
                .targetGateway(TARGET_GATEWAY)
                .orgName(request.getWso2Tenant())
                .environment(request.getEnvironment())
                .requestTransactionId(request.getRequestTransactionId())
                .discoveryStatus(STATUS_COMPLETED)
                .totalUsers(documents.size())
                .totalRoles(totalUniqueRoles(documents))
                .users(documents.stream().map(this::toDetail).collect(Collectors.toList()))
                .build();
    }

    /**
     * Projects one persistence document into the public user response item.
     */
    private Wso2UserProfileDetail toDetail(Wso2UserProfileDocument document) {
        return Wso2UserProfileDetail.builder()
                .sourceUserId(document.getSourceUserId())
                .userName(document.getUserName())
                .userEmail(document.getPrimaryEmail())
                .firstName(document.getFirstName())
                .lastName(document.getLastName())
                .displayName(document.getDisplayName())
                .primaryEmail(document.getPrimaryEmail())
                .emails(document.getEmails())
                .roles(toRoleDetails(document.getRoles(), document.getRolePermissions()))
                .build();
    }

    /**
     * Converts WSO2 role names to role response objects with permission details.
     */
    private List<Wso2DiscoveredUserRole> toRoleDetails(List<String> roles,
                                                       Map<String, List<Wso2RolePermissionDetail>> permissionsByRole) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(role -> Wso2DiscoveredUserRole.builder()
                        .roleName(role)
                        .permissions(permissionsForRole(role, permissionsByRole))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Returns permission details for one role with an empty-list fallback.
     */
    private List<Wso2RolePermissionDetail> permissionsForRole(String role,
                                                             Map<String, List<Wso2RolePermissionDetail>> permissionsByRole) {
        if (permissionsByRole == null || !permissionsByRole.containsKey(role)) {
            return List.of();
        }
        List<Wso2RolePermissionDetail> permissions = permissionsByRole.get(role);
        return permissions == null ? List.of() : permissions;
    }

    /**
     * Keeps only permissions for roles assigned to the current user.
     */
    private Map<String, List<Wso2RolePermissionDetail>> filterRolePermissions(List<String> roles,
                                                                              Map<String, List<Wso2RolePermissionDetail>> permissionsByRole) {
        Map<String, List<Wso2RolePermissionDetail>> filtered = new LinkedHashMap<>();
        if (roles == null || permissionsByRole == null) {
            return filtered;
        }
        for (String role : roles) {
            filtered.put(role, permissionsForRole(role, permissionsByRole));
        }
        return filtered;
    }

    /**
     * Counts unique role names across all discovered users.
     */
    private int totalUniqueRoles(List<Wso2UserProfileDocument> documents) {
        Set<String> uniqueRoles = new LinkedHashSet<>();
        for (Wso2UserProfileDocument document : documents) {
            if (document.getRoles() != null) {
                uniqueRoles.addAll(document.getRoles());
            }
        }
        return uniqueRoles.size();
    }

    /**
     * Creates a deterministic document id for one discovery transaction.
     */
    private String documentId(Wso2UserProfileDiscoveryRequest request, String userName) {
        return String.join("|",
                request.getCompanyName(),
                request.getWso2Tenant(),
                RESOURCE_TYPE,
                StringUtils.hasText(userName) ? userName : "unknown",
                request.getRequestTransactionId());
    }

    /**
     * Extracts one configured claim value from a SOAP profile.
     */
    private String claim(Wso2SoapUserProfile profile, String claimUri) {
        if (profile.getClaims() == null) {
            return null;
        }
        return profile.getClaims().get(claimUri);
    }

    /**
     * Builds a display name from claim values with username fallback.
     */
    private String displayName(String firstName, String lastName, String userName) {
        String combined = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return StringUtils.hasText(combined) ? combined : userName;
    }

    /**
     * Keeps a compact SOAP-derived raw payload for troubleshooting.
     */
    private Map<String, Object> rawPayload(Wso2SoapUserProfile profile,
                                           Map<String, List<Wso2RolePermissionDetail>> rolePermissions) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("userName", profile.getUserName());
        raw.put("roles", profile.getRoles());
        raw.put("rolePermissions", filterRolePermissions(profile.getRoles(), rolePermissions));
        raw.put("claims", profile.getClaims());
        raw.put("errorMessage", profile.getErrorMessage());
        return raw;
    }

    /**
     * Appends one partial failure message without newline characters.
     */
    private String appendError(String existing, String next) {
        if (!StringUtils.hasText(existing)) {
            return next;
        }
        return existing + "; " + next;
    }

    /**
     * Sanitizes exception text before returning or logging it.
     */
    private String sanitize(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[\\r\\n\\t]+", " ") : "Unexpected error";
    }

    /**
     * Writes structured logs for user-profile SOAP discovery events.
     */
    private void logEvent(String eventType,
                          Wso2UserProfileDiscoveryRequest request,
                          String methodName,
                          String status,
                          long start,
                          String errorMessage) {
        log.info("[WSO2-USER-PROFILES] eventType={} companyName={} wso2Tenant={} environment={} requestTransactionId={} methodName={} status={} processingTimeMs={} errorMessage={}",
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

    /**
     * Internal value object for rebuilding a role-to-permissions map after
     * bounded parallel SOAP lookups complete.
     */
    private record RolePermissionLookupResult(String roleName, List<Wso2RolePermissionDetail> permissions) {
    }
}
