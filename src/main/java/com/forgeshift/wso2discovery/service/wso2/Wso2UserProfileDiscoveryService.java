package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.client.Wso2Client;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.domain.Wso2UserProfileDocument;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDetail;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryResponse;
import com.forgeshift.wso2discovery.repository.Wso2UserProfileRepository;
import com.forgeshift.wso2discovery.service.Wso2TokenService;
import com.forgeshift.wso2discovery.util.PayloadCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates normalized WSO2 SCIM user-profile discovery for user migration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2UserProfileDiscoveryService {

    private static final String RESOURCE_TYPE = "user-profiles";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final Wso2Client wso2Client;
    private final Wso2TokenService tokenService;
    private final Wso2Properties wso2Properties;
    private final Wso2UserProfileRepository repository;

    /**
     * Parent orchestration method that validates the request, reads WSO2 SCIM
     * users, persists normalized profile documents, and builds the API response.
     */
    public Wso2UserProfileDiscoveryResponse discoverUserProfiles(Wso2UserProfileDiscoveryRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_user_profile_discovery_started", request, "discoverUserProfiles", "STARTED", start, null);
        try {
            // Step 1: Validate request
            validateRequest(request);

            // Step 2: Fetch WSO2 SCIM users
            List<Map<String, Object>> users = fetchUsers(request);

            // Step 3: Normalize user profiles
            List<Wso2UserProfileDocument> documents = transformUsers(users, request);

            // Step 4: Store normalized user profiles in MongoDB
            List<String> documentIds = saveUserProfiles(documents, request);

            // Step 5: Build response
            Wso2UserProfileDiscoveryResponse response = buildResponse(request, documents, documentIds, start);
            logEvent("wso2_user_profile_discovery_completed", request, "discoverUserProfiles", "SUCCESS", start, null);
            return response;
        } catch (RuntimeException ex) {
            logEvent("wso2_user_profile_discovery_failed", request, "discoverUserProfiles", "FAILED", start, ex.getMessage());
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
     * Gets a WSO2 token and calls the strict SCIM users API with one retry on 401.
     */
    private List<Map<String, Object>> fetchUsers(Wso2UserProfileDiscoveryRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_user_profile_fetch_started", request, "fetchUsers", "STARTED", start, null);

        String scope = wso2Properties.getAdminScope();
        String token = tokenService.getToken(scope, request.getCompanyName(), request.getWso2Tenant());
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Failed to acquire WSO2 access token");
        }

        try {
            List<Map<String, Object>> users = wso2Client.listUsersStrict(token);
            logEvent("wso2_user_profile_fetch_completed", request, "fetchUsers", "SUCCESS", start, null);
            return users;
        } catch (Exception ex) {
            if (!isUnauthorized(ex)) {
                throw new IllegalStateException("Failed to fetch user profiles from WSO2: " + sanitize(ex.getMessage()), ex);
            }
            logEvent("wso2_user_profile_token_retry_started", request, "fetchUsers", "STARTED", start,
                    "WSO2 returned 401; invalidating cached token and retrying once");
            tokenService.invalidate(request.getCompanyName(), request.getWso2Tenant(), scope);
            String freshToken = tokenService.getToken(scope, request.getCompanyName(), request.getWso2Tenant());
            if (!StringUtils.hasText(freshToken)) {
                throw new IllegalStateException("Failed to refresh WSO2 access token after 401", ex);
            }
            try {
                List<Map<String, Object>> users = wso2Client.listUsersStrict(freshToken);
                logEvent("wso2_user_profile_fetch_completed", request, "fetchUsers", "SUCCESS", start, null);
                return users;
            } catch (Exception retryException) {
                throw new IllegalStateException("Failed to fetch user profiles from WSO2 after retry: "
                        + sanitize(retryException.getMessage()), retryException);
            }
        }
    }

    /**
     * Converts raw SCIM user payloads into normalized MongoDB documents.
     */
    private List<Wso2UserProfileDocument> transformUsers(List<Map<String, Object>> users,
                                                         Wso2UserProfileDiscoveryRequest request) {
        Instant now = Instant.now();
        List<Map<String, Object>> safeUsers = users != null ? users : Collections.emptyList();
        return safeUsers.stream()
                .map(user -> toDocument(user, request, now))
                .collect(Collectors.toList());
    }

    /**
     * Converts one SCIM user payload into a normalized MongoDB document.
     */
    private Wso2UserProfileDocument toDocument(Map<String, Object> user,
                                               Wso2UserProfileDiscoveryRequest request,
                                               Instant now) {
        String sourceUserId = firstNonBlank(str(user.get("id")), str(user.get("userName")));
        String userName = str(user.get("userName"));
        String firstName = namePart(user, "givenName");
        String lastName = namePart(user, "familyName");
        List<String> emails = extractEmails(user);
        List<String> roles = extractRoles(user);
        return Wso2UserProfileDocument.builder()
                .id(documentId(request, sourceUserId))
                .companyName(request.getCompanyName())
                .wso2Tenant(request.getWso2Tenant())
                .environment(request.getEnvironment())
                .requestTransactionId(request.getRequestTransactionId())
                .sourceUserId(sourceUserId)
                .userName(userName)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(displayName(user, firstName, lastName, userName))
                .primaryEmail(emails != null && !emails.isEmpty() ? emails.get(0) : null)
                .emails(emails)
                .active(asBool(user.get("active")))
                .userType(str(user.get("userType")))
                .roles(roles)
                .rawPayload(PayloadCleaner.strip(user))
                .requestedBy(request.getUserEmail())
                .createdDate(now)
                .updatedDate(now)
                .build();
    }

    /**
     * Saves normalized user profile documents and returns their document ids.
     */
    private List<String> saveUserProfiles(List<Wso2UserProfileDocument> documents,
                                          Wso2UserProfileDiscoveryRequest request) {
        long start = System.currentTimeMillis();
        logEvent("wso2_user_profile_db_save_started", request, "saveUserProfiles", "STARTED", start, null);
        try {
            List<String> ids = repository.upsertAll(documents);
            logEvent("wso2_user_profile_db_save_completed", request, "saveUserProfiles", "SUCCESS", start, null);
            return ids;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store WSO2 user profiles: " + sanitize(ex.getMessage()), ex);
        }
    }

    /**
     * Builds the API response with normalized users and discovery metadata.
     */
    private Wso2UserProfileDiscoveryResponse buildResponse(Wso2UserProfileDiscoveryRequest request,
                                                           List<Wso2UserProfileDocument> documents,
                                                           List<String> documentIds,
                                                           long start) {
        return Wso2UserProfileDiscoveryResponse.builder()
                .companyName(request.getCompanyName())
                .wso2Tenant(request.getWso2Tenant())
                .environment(request.getEnvironment())
                .requestTransactionId(request.getRequestTransactionId())
                .discoveryStatus(STATUS_COMPLETED)
                .totalUsers(documents.size())
                .totalRoles(totalUniqueRoles(documents))
                .collectionName(repository.collectionName())
                .documentIds(documentIds)
                .users(documents.stream().map(this::toDetail).collect(Collectors.toList()))
                .timestamp(Instant.now().toString())
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
    }

    /**
     * Projects a MongoDB document into the public API response model.
     */
    private Wso2UserProfileDetail toDetail(Wso2UserProfileDocument document) {
        return Wso2UserProfileDetail.builder()
                .sourceUserId(document.getSourceUserId())
                .userName(document.getUserName())
                .firstName(document.getFirstName())
                .lastName(document.getLastName())
                .displayName(document.getDisplayName())
                .primaryEmail(document.getPrimaryEmail())
                .emails(document.getEmails())
                .active(document.getActive())
                .userType(document.getUserType())
                .roles(document.getRoles())
                .build();
    }

    /**
     * Extracts one named part from the SCIM name object.
     */
    @SuppressWarnings("unchecked")
    private String namePart(Map<String, Object> user, String key) {
        Object name = user.get("name");
        if (name instanceof Map<?, ?> map) {
            return str(((Map<String, Object>) map).get(key));
        }
        return null;
    }

    /**
     * Builds display name using SCIM displayName, name parts, then userName.
     */
    private String displayName(Map<String, Object> user, String firstName, String lastName, String userName) {
        String explicitDisplayName = str(user.get("displayName"));
        if (StringUtils.hasText(explicitDisplayName)) {
            return explicitDisplayName;
        }
        String combined = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return StringUtils.hasText(combined) ? combined : userName;
    }

    /**
     * Extracts SCIM email values with primary email first.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractEmails(Map<String, Object> user) {
        Object rawEmails = user.get("emails");
        if (!(rawEmails instanceof Collection<?> collection)) {
            return null;
        }
        List<String> primary = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                String value = str(((Map<String, Object>) map).get("value"));
                if (!StringUtils.hasText(value)) continue;
                if (Boolean.TRUE.equals(((Map<String, Object>) map).get("primary"))) primary.add(value);
                else rest.add(value);
            } else if (item != null && StringUtils.hasText(item.toString())) {
                rest.add(item.toString());
            }
        }
        primary.addAll(rest);
        return primary.isEmpty() ? null : primary;
    }

    /**
     * Extracts WSO2 role names from SCIM groups.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Map<String, Object> user) {
        Object rawGroups = user.get("groups");
        if (!(rawGroups instanceof Collection<?> collection)) {
            return null;
        }
        Set<String> roles = new LinkedHashSet<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                String display = str(((Map<String, Object>) map).get("display"));
                if (StringUtils.hasText(display)) roles.add(display);
            } else if (item != null && StringUtils.hasText(item.toString())) {
                roles.add(item.toString());
            }
        }
        return roles.isEmpty() ? null : new ArrayList<>(roles);
    }

    /**
     * Counts unique role names across all discovered user profiles.
     */
    private int totalUniqueRoles(List<Wso2UserProfileDocument> documents) {
        return documents.stream()
                .filter(document -> document.getRoles() != null)
                .flatMap(document -> document.getRoles().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .size();
    }

    /**
     * Creates a deterministic document id for one user profile discovery run.
     */
    private String documentId(Wso2UserProfileDiscoveryRequest request, String sourceUserId) {
        return String.join("|",
                request.getCompanyName(),
                request.getWso2Tenant(),
                RESOURCE_TYPE,
                StringUtils.hasText(sourceUserId) ? sourceUserId : "unknown",
                request.getRequestTransactionId());
    }

    /**
     * Returns true when an exception chain contains a WSO2 401 response.
     */
    private boolean isUnauthorized(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof WebClientResponseException responseException
                    && responseException.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Converts an object to string while normalizing blank values to null.
     */
    private static String str(Object value) {
        if (value == null) return null;
        String stringValue = value.toString();
        return StringUtils.hasText(stringValue) ? stringValue : null;
    }

    /**
     * Parses SCIM booleans that may arrive as Boolean or string values.
     */
    private static Boolean asBool(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Returns the first non-blank value from the supplied candidates.
     */
    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    /**
     * Sanitizes exception text before it is returned through global handlers.
     */
    private String sanitize(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[\\r\\n\\t]+", " ") : "Unexpected error";
    }

    /**
     * Writes structured service logs for discovery lifecycle events.
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
}
