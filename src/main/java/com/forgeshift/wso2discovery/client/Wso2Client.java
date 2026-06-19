package com.forgeshift.wso2discovery.client;

import com.forgeshift.wso2discovery.config.Wso2Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP client for the WSO2 API Manager management plane.
 *
 * Owns:
 *   - OAuth2 password-grant token acquisition against {@code /oauth2/token}
 *   - Publisher REST API calls (list APIs, get API, get swagger)
 *
 * Admin and DevPortal calls land in follow-up commits as new resource types
 * are added (throttling policies, applications, etc.).
 */
@Slf4j
@Component
public class Wso2Client {

    private final WebClient wso2WebClient;
    private final Wso2Properties props;

    public Wso2Client(@Qualifier("wso2WebClient") WebClient wso2WebClient, Wso2Properties props) {
        this.wso2WebClient = wso2WebClient;
        this.props = props;
    }

    // =====================================================================
    // Token
    // =====================================================================

    /**
     * Acquire an OAuth2 access token via password grant.
     *
     * Mirrors the prototype in Archive/export_wso2_apis.sh:
     *   POST /oauth2/token
     *   Authorization: Basic base64(clientId:clientSecret)
     *   Content-Type: application/x-www-form-urlencoded
     *   grant_type=password & username=... & password=... & scope=...
     *
     * @param scope OAuth2 scope (e.g. apim:api_view). When null, the
     *              {@code forgeshift.wso2.publisher-scope} default is used.
     * @return the access_token string, or null on failure (errors are logged).
     */
    public String acquireToken(String scope) {
        return acquireToken(scope, defaultCreds());
    }

    /**
     * Overload for multi-tenancy: uses the supplied {@link Wso2Credentials}
     * instead of the static {@code forgeshift.wso2.*} config. Callsites that
     * have a (companyName, wso2Tenant) in hand should prefer this overload
     * via {@link com.forgeshift.wso2discovery.service.Wso2TenantProfileService#resolve}.
     */
    public String acquireToken(String scope, Wso2Credentials creds) {
        if (creds == null) creds = defaultCreds();
        if (!StringUtils.hasText(creds.getClientId()) || !StringUtils.hasText(creds.getClientSecret())) {
            log.error("WSO2 client_id / client_secret are not configured ({} credentials).", creds.getSource());
            return null;
        }
        String resolvedScope = StringUtils.hasText(scope) ? scope : props.getPublisherScope();
        String basic = Base64.getEncoder().encodeToString(
                (creds.getClientId() + ":" + creds.getClientSecret()).getBytes());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("username", creds.getUsername());
        form.add("password", creds.getPassword());
        form.add("scope", resolvedScope);

        // Use the resolved base URL if it differs from the WebClient's
        // default. Building a per-call URI keeps the WebClient bean
        // single-instance while still supporting multi-tenant endpoints.
        String tokenUri = effectiveBaseUrl(creds) + props.getTokenPath();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = wso2WebClient.post()
                    .uri(tokenUri)
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                    .block();

            Object token = body != null ? body.get("access_token") : null;
            if (token == null) {
                log.error("WSO2 token response missing access_token: {}", body);
                return null;
            }
            log.debug("Acquired WSO2 token (scope={} source={}).", resolvedScope, creds.getSource());
            return token.toString();
        } catch (WebClientResponseException e) {
            log.error("WSO2 token request failed: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("WSO2 token request failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /** Build credentials from the static forgeshift.wso2.* config. */
    private Wso2Credentials defaultCreds() {
        return Wso2Credentials.builder()
                .source("static")
                .baseUrl(props.getBaseUrl())
                .username(props.getUsername())
                .password(props.getPassword())
                .clientId(props.getClientId())
                .clientSecret(props.getClientSecret())
                .trustSelfSigned(props.isTrustSelfSigned())
                .build();
    }

    private String effectiveBaseUrl(Wso2Credentials creds) {
        if (creds != null && StringUtils.hasText(creds.getBaseUrl())) return creds.getBaseUrl();
        return props.getBaseUrl();
    }

    // =====================================================================
    // Publisher API
    // =====================================================================

    /**
     * List all APIs from the Publisher REST API. Handles pagination using the
     * {@code limit} and {@code offset} query parameters. Returns the union of
     * all pages.
     */
    public List<Map<String, Object>> listApis(String accessToken) {
        Objects.requireNonNull(accessToken, "accessToken");

        int pageSize = props.getPageSize();
        int offset = 0;
        java.util.List<Map<String, Object>> all = new java.util.ArrayList<>();

        while (true) {
            final int currentOffset = offset;
            @SuppressWarnings("unchecked")
            Map<String, Object> page = wso2WebClient.get()
                    .uri(uri -> uri.path(props.getPublisherApiBase() + "/apis")
                            .queryParam("limit", pageSize)
                            .queryParam("offset", currentOffset)
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                    .block();

            if (page == null) break;
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) page.get("list");
            if (list == null || list.isEmpty()) break;
            all.addAll(list);

            int returned = list.size();
            if (returned < pageSize) break;
            offset += returned;

            // Safety cap to avoid runaway pagination on unexpected payloads.
            if (offset > 100_000) {
                log.warn("Publisher API listApis exceeded 100k items, stopping pagination.");
                break;
            }
        }
        return all;
    }

    /**
     * Fetch one API's full definition. Mirrors the prototype's GET call to
     * {@code /api/am/publisher/v4/apis/{apiId}}.
     */
    public Map<String, Object> getApi(String accessToken, String apiId) {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(apiId, "apiId");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = wso2WebClient.get()
                    .uri(props.getPublisherApiBase() + "/apis/" + apiId)
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                    .block();
            return body;
        } catch (WebClientResponseException e) {
            log.warn("getApi({}) failed: status={} body={}", apiId, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        }
    }

    /**
     * Full detail of one API Product, {@code GET /api/am/publisher/v4/api-products/{id}}.
     * Unlike the {@code /api-products} LIST endpoint, the detail includes the {@code apis}
     * array (the member APIs) — needed so the migration can route the product's members.
     * Returns null on failure (caller keeps the list-only payload).
     */
    public Map<String, Object> getApiProduct(String accessToken, String apiProductId) {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(apiProductId, "apiProductId");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = wso2WebClient.get()
                    .uri(props.getPublisherApiBase() + "/api-products/" + apiProductId)
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                    .block();
            return body;
        } catch (WebClientResponseException e) {
            log.warn("getApiProduct({}) failed: status={} body={}", apiProductId,
                    e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        }
    }

    // =====================================================================
    // Inventory / list-only calls (used by POST /wso2/inventory)
    //
    // Every public listX method delegates to listPaginated so pagination,
    // timeout, error handling and the bearer-token plumbing live in one
    // place. One broken endpoint never fails the whole inventory call:
    // listX returns the items collected so far (possibly empty) and logs
    // the failure.
    // =====================================================================

    /** Same as listPaginated but with an extra static query parameter (e.g. applicationId=X). */
    private List<Map<String, Object>> listPaginatedWithQuery(String accessToken, String path, String extraKey, String extraValue) {
        Objects.requireNonNull(accessToken, "accessToken");
        int pageSize = props.getPageSize();
        int offset = 0;
        java.util.List<Map<String, Object>> all = new java.util.ArrayList<>();

        while (true) {
            final int currentOffset = offset;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> page = wso2WebClient.get()
                        .uri(uri -> uri.path(path)
                                .queryParam(extraKey, extraValue)
                                .queryParam("limit", pageSize)
                                .queryParam("offset", currentOffset)
                                .build())
                        .header("Authorization", "Bearer " + accessToken)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                        .block();

                if (page == null) break;
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) page.get("list");
                if (list == null || list.isEmpty()) break;
                all.addAll(list);
                if (list.size() < pageSize) break;
                offset += list.size();
                if (offset > 100_000) {
                    log.warn("listPaginatedWithQuery({}) exceeded 100k items, stopping pagination.", path);
                    break;
                }
            } catch (WebClientResponseException e) {
                // Let an expired/revoked token (401) propagate so BaseDiscoveryService can
                // invalidate the cached token and retry once with a fresh one — same self-heal
                // listApis() gets for free. Swallowing it here turned a stale cached token into
                // a silent empty list (e.g. apiproducts/applications showing 0 after a WSO2
                // restart, until the 55-min token cache expired on its own).
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    throw e;
                }
                log.warn("WSO2 list call to {}?{}={} failed: status={} body={}",
                        path, extraKey, extraValue, e.getStatusCode(), e.getResponseBodyAsString());
                return all;
            } catch (Exception e) {
                log.warn("WSO2 list call to {}?{}={} failed: {}", path, extraKey, extraValue, e.getMessage());
                return all;
            }
        }
        return all;
    }

    /** Pages through a list endpoint that follows WSO2's standard {list, count, pagination} shape. */
    private List<Map<String, Object>> listPaginated(String accessToken, String path) {
        Objects.requireNonNull(accessToken, "accessToken");
        int pageSize = props.getPageSize();
        int offset = 0;
        java.util.List<Map<String, Object>> all = new java.util.ArrayList<>();

        while (true) {
            final int currentOffset = offset;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> page = wso2WebClient.get()
                        .uri(uri -> uri.path(path)
                                .queryParam("limit", pageSize)
                                .queryParam("offset", currentOffset)
                                .build())
                        .header("Authorization", "Bearer " + accessToken)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                        .block();

                if (page == null) break;
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) page.get("list");
                if (list == null || list.isEmpty()) break;
                all.addAll(list);

                if (list.size() < pageSize) break;
                offset += list.size();
                if (offset > 100_000) {
                    log.warn("listPaginated({}) exceeded 100k items, stopping pagination.", path);
                    break;
                }
            } catch (WebClientResponseException e) {
                // Let an expired/revoked token (401) propagate so BaseDiscoveryService can
                // invalidate the cached token and retry once with a fresh one — same self-heal
                // listApis() gets for free. Swallowing it here turned a stale cached token into
                // a silent empty list (e.g. apiproducts/applications showing 0 after a WSO2
                // restart, until the 55-min token cache expired on its own).
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    throw e;
                }
                log.warn("WSO2 list call to {} failed: status={} body={}",
                        path, e.getStatusCode(), e.getResponseBodyAsString());
                return all;
            } catch (Exception e) {
                log.warn("WSO2 list call to {} failed: {}", path, e.getMessage());
                return all;
            }
        }
        return all;
    }

    /** GET /api/am/devportal/v3/applications  (requires devportal scope). */
    public List<Map<String, Object>> listApplications(String accessToken) {
        return listPaginated(accessToken, props.getDevportalApiBase() + "/applications");
    }

    /**
     * GET /api/am/devportal/v3/subscriptions  (requires devportal scope).
     *
     * WSO2 4.x requires either {@code applicationId} or {@code apiId} as a query
     * parameter — calling without one returns 400 "Either applicationId or apiId
     * should be available". Callers that want all subscriptions in the tenant
     * should iterate applications and aggregate via
     * {@link #listSubscriptionsForApplication(String, String)}.
     */
    public List<Map<String, Object>> listSubscriptions(String accessToken) {
        return listPaginated(accessToken, props.getDevportalApiBase() + "/subscriptions");
    }

    /**
     * GET /api/am/devportal/v3/subscriptions?applicationId={appId}.
     *
     * The only reliable way to list all subscriptions on WSO2 4.x — iterate
     * applications and call this per-app.
     */
    public List<Map<String, Object>> listSubscriptionsForApplication(String accessToken, String applicationId) {
        return listPaginatedWithQuery(accessToken,
                props.getDevportalApiBase() + "/subscriptions",
                "applicationId", applicationId);
    }

    /** GET /api/am/publisher/v4/api-products  (requires publisher scope). */
    public List<Map<String, Object>> listApiProducts(String accessToken) {
        return listPaginated(accessToken, props.getPublisherApiBase() + "/api-products");
    }

    /**
     * GET /api/am/admin/v4/throttling/policies/{policyType}.
     * For the inventory we fetch subscription-tier policies, which is what
     * matters for downstream rate-limiting translation. Other tiers
     * (application, advanced, custom) ship as separate listX methods later.
     */
    public List<Map<String, Object>> listSubscriptionThrottlingPolicies(String accessToken) {
        return listPaginated(accessToken,
                props.getAdminApiBase() + "/throttling/policies/subscription");
    }

    public List<Map<String, Object>> listApplicationThrottlingPolicies(String accessToken) {
        return listPaginated(accessToken,
                props.getAdminApiBase() + "/throttling/policies/application");
    }

    public List<Map<String, Object>> listAdvancedThrottlingPolicies(String accessToken) {
        return listPaginated(accessToken,
                props.getAdminApiBase() + "/throttling/policies/advanced");
    }

    /** GET /api/am/admin/v4/key-managers  (requires admin scope). */
    public List<Map<String, Object>> listKeyManagers(String accessToken) {
        return listPaginated(accessToken, props.getAdminApiBase() + "/key-managers");
    }

    /** GET /api/am/admin/v4/scopes  (requires admin scope). */
    public List<Map<String, Object>> listScopes(String accessToken) {
        return listPaginated(accessToken, props.getAdminApiBase() + "/scopes");
    }

    /** GET /api/am/publisher/v4/endpoint-certificates  (requires publisher scope). */
    public List<Map<String, Object>> listCertificates(String accessToken) {
        return listPaginated(accessToken, props.getPublisherApiBase() + "/endpoint-certificates");
    }

    /**
     * GET /api/am/publisher/v4/apis/{apiId}/mediation-policies  (publisher scope).
     * Mediation policies are per-API in WSO2 4.x - there is no tenant-wide list
     * endpoint. The caller (Wso2MediationPoliciesDiscoveryService) loops over
     * every API and aggregates.
     */
    public List<Map<String, Object>> listMediationPoliciesForApi(String accessToken, String apiId) {
        return listPaginated(accessToken,
                props.getPublisherApiBase() + "/apis/" + apiId + "/mediation-policies");
    }

    /**
     * GET /scim2/Users  -  SCIM 2.0 user store.
     *
     * SCIM has a different response shape than the APIM REST APIs:
     *   { "totalResults": N, "startIndex": 1, "itemsPerPage": 50,
     *     "Resources": [ ... ] }
     *
     * Pagination keys are "startIndex" (1-based) and "count" - not limit/offset.
     * Some WSO2 builds gate /scim2/Users on Basic admin auth rather than bearer
     * tokens; if a bearer 401s, ops needs to grant the SCIM scope to the OAuth
     * client (or we add a Basic-auth fallback later).
     */
    public List<Map<String, Object>> listUsers(String accessToken) {
        return listScimPaginated(accessToken, props.getScimApiBase() + "/Users");
    }

    /** SCIM 2.0 paginator. Keys: startIndex (1-based), count. Wrapper: Resources. */
    private List<Map<String, Object>> listScimPaginated(String accessToken, String path) {
        Objects.requireNonNull(accessToken, "accessToken");
        int pageSize = props.getPageSize();
        int startIndex = 1;
        java.util.List<Map<String, Object>> all = new java.util.ArrayList<>();

        while (true) {
            final int currentStart = startIndex;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> page = wso2WebClient.get()
                        .uri(uri -> uri.path(path)
                                .queryParam("startIndex", currentStart)
                                .queryParam("count", pageSize)
                                .build())
                        .header("Authorization", "Bearer " + accessToken)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                        .block();

                if (page == null) break;
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> resources =
                        (java.util.List<Map<String, Object>>) page.get("Resources");
                if (resources == null || resources.isEmpty()) break;
                all.addAll(resources);

                if (resources.size() < pageSize) break;
                startIndex += resources.size();
                if (startIndex > 100_001) {
                    log.warn("listScimPaginated({}) exceeded 100k items, stopping pagination.", path);
                    break;
                }
            } catch (WebClientResponseException e) {
                log.warn("WSO2 SCIM call to {} failed: status={} body={}",
                        path, e.getStatusCode(), e.getResponseBodyAsString());
                return all;
            } catch (Exception e) {
                log.warn("WSO2 SCIM call to {} failed: {}", path, e.getMessage());
                return all;
            }
        }
        return all;
    }

    /**
     * Fetch the swagger / OpenAPI definition for an API.
     */
    public byte[] getApiSwagger(String accessToken, String apiId) {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(apiId, "apiId");

        try {
            return wso2WebClient.get()
                    .uri(props.getPublisherApiBase() + "/apis/" + apiId + "/swagger")
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON, MediaType.parseMediaType("application/yaml"))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                    .block();
        } catch (WebClientResponseException e) {
            log.warn("getApiSwagger({}) failed: status={} body={}", apiId, e.getStatusCode(), e.getResponseBodyAsString());
            return new byte[0];
        }
    }
}
