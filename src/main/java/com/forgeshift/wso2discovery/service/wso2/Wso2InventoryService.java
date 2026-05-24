package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.client.Wso2Client;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.service.Wso2OrganizationService;
import com.forgeshift.wso2discovery.service.Wso2TokenService;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.InventoryResponse;
import com.forgeshift.wso2discovery.dto.details.ResourceSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Cheap "what's there?" pass over a WSO2 tenant.
 *
 * Acquires one token covering publisher + admin + devportal scopes, then
 * fans out parallel list-only calls. Does not write to MongoDB and does not
 * allocate a revision. If a particular list endpoint fails (e.g. the
 * authenticated user lacks the admin scope), only that resource's entry is
 * empty and an entry appears in {@link InventoryResponse#getErrors()}.
 */
@Slf4j
@Service
public class Wso2InventoryService {

    private final Wso2Client wso2Client;
    private final Wso2Properties wso2Props;
    private final Wso2TokenService tokenService;
    private final Wso2OrganizationService organizationService;
    private final Executor discoveryExecutor;

    public Wso2InventoryService(Wso2Client wso2Client,
                                Wso2Properties wso2Props,
                                Wso2TokenService tokenService,
                                Wso2OrganizationService organizationService,
                                @Qualifier("discoveryExecutor") TaskExecutor discoveryExecutor) {
        this.wso2Client = wso2Client;
        this.wso2Props = wso2Props;
        this.tokenService = tokenService;
        this.organizationService = organizationService;
        this.discoveryExecutor = discoveryExecutor;
    }

    public InventoryResponse inventory(DiscoverResourceRequest req) {
        long start = System.currentTimeMillis();

        if (req == null || !StringUtils.hasText(req.getWso2Tenant())) {
            throw new IllegalArgumentException("wso2Tenant is required");
        }

        String token = tokenService.getToken(wso2Props.inventoryScope(),
                req.getCompanyName(), req.getWso2Tenant());
        if (token == null) {
            throw new IllegalStateException("Failed to acquire WSO2 access token for inventory");
        }

        String txn = StringUtils.hasText(req.getRequestTransactionId())
                ? req.getRequestTransactionId()
                : UUID.randomUUID().toString();

        // Issue every list call in parallel.
        Map<String, String> errors = new java.util.concurrent.ConcurrentHashMap<>();

        CompletableFuture<List<ResourceSummary>> apis = listAsync("apis", () ->
                wso2Client.listApis(token), Mappers::api, errors);

        CompletableFuture<List<ResourceSummary>> applications = listAsync("applications", () ->
                wso2Client.listApplications(token), Mappers::application, errors);

        CompletableFuture<List<ResourceSummary>> subscriptions = listAsync("subscriptions", () ->
                wso2Client.listSubscriptions(token), Mappers::subscription, errors);

        CompletableFuture<List<ResourceSummary>> apiProducts = listAsync("apiproducts", () ->
                wso2Client.listApiProducts(token), Mappers::apiProduct, errors);

        CompletableFuture<List<ResourceSummary>> keyManagers = listAsync("keymanagers", () ->
                wso2Client.listKeyManagers(token), Mappers::keyManager, errors);

        CompletableFuture<List<ResourceSummary>> scopes = listAsync("scopes", () ->
                wso2Client.listScopes(token), Mappers::scope, errors);

        CompletableFuture<List<ResourceSummary>> certificates = listAsync("certificates", () ->
                wso2Client.listCertificates(token), Mappers::certificate, errors);

        // Throttling policies: union of subscription + application + advanced tiers.
        CompletableFuture<List<ResourceSummary>> throttlingPolicies = CompletableFuture
                .supplyAsync(() -> {
                    List<ResourceSummary> merged = new ArrayList<>();
                    safeAddPolicies(token, "subscription", "subscription",
                            wso2Client::listSubscriptionThrottlingPolicies, merged, errors);
                    safeAddPolicies(token, "application", "application",
                            wso2Client::listApplicationThrottlingPolicies, merged, errors);
                    safeAddPolicies(token, "advanced", "advanced",
                            wso2Client::listAdvancedThrottlingPolicies, merged, errors);
                    return merged;
                }, discoveryExecutor);

        // Wait for all of them.
        CompletableFuture.allOf(apis, applications, subscriptions, apiProducts,
                keyManagers, scopes, certificates, throttlingPolicies).join();

        List<ResourceSummary> apisList = apis.join();
        List<ResourceSummary> appsList = applications.join();
        List<ResourceSummary> subsList = subscriptions.join();
        List<ResourceSummary> productsList = apiProducts.join();
        List<ResourceSummary> kmList = keyManagers.join();
        List<ResourceSummary> scopeList = scopes.join();
        List<ResourceSummary> certList = certificates.join();
        List<ResourceSummary> throttleList = throttlingPolicies.join();

        int total = apisList.size() + appsList.size() + subsList.size()
                + productsList.size() + kmList.size() + scopeList.size()
                + certList.size() + throttleList.size();

        organizationService.recordSeen(
                req.getCompanyName(), req.getWso2Tenant(),
                txn, null, req.getUserEmail(), "INVENTORY");

        long elapsed = System.currentTimeMillis() - start;
        log.info("[inventory] company={} tenant={} total={} elapsedMs={} errors={}",
                req.getCompanyName(), req.getWso2Tenant(), total, elapsed,
                errors.isEmpty() ? "none" : errors.keySet());

        return InventoryResponse.builder()
                .companyName(req.getCompanyName())
                .wso2Tenant(req.getWso2Tenant())
                .type("inventory")
                .requestTransactionId(txn)
                .timestamp(Instant.now().toString())
                .totalCount(total)
                .apiSummaries(apisList)
                .applicationSummaries(appsList)
                .subscriptionSummaries(subsList)
                .apiProductSummaries(productsList)
                .throttlingPolicySummaries(throttleList)
                .keyManagerSummaries(kmList)
                .scopeSummaries(scopeList)
                .certificateSummaries(certList)
                .errors(errors.isEmpty() ? null : errors)
                .elapsedMs(elapsed)
                .build();
    }

    // -----------------------------------------------------------------

    private CompletableFuture<List<ResourceSummary>> listAsync(
            String slug,
            java.util.function.Supplier<List<Map<String, Object>>> call,
            Function<Map<String, Object>, ResourceSummary> mapper,
            Map<String, String> errors) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> raw = call.get();
                List<ResourceSummary> out = new ArrayList<>(raw.size());
                for (Map<String, Object> r : raw) out.add(mapper.apply(r));
                log.debug("[inventory:{}] {} items", slug, out.size());
                return out;
            } catch (Exception e) {
                log.warn("[inventory:{}] failed: {}", slug, e.getMessage());
                errors.put(slug, e.getMessage());
                return Collections.<ResourceSummary>emptyList();
            }
        }, discoveryExecutor);
    }

    private void safeAddPolicies(String token, String slug, String policyType,
                                 java.util.function.Function<String, List<Map<String, Object>>> call,
                                 List<ResourceSummary> sink,
                                 Map<String, String> errors) {
        try {
            for (Map<String, Object> p : call.apply(token)) {
                sink.add(Mappers.throttlingPolicy(p, policyType));
            }
        } catch (Exception e) {
            log.warn("[inventory:throttlingpolicies:{}] failed: {}", slug, e.getMessage());
            errors.put("throttlingpolicies:" + slug, e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Per-resource projection helpers
    // -----------------------------------------------------------------

    static final class Mappers {
        private Mappers() {}

        static ResourceSummary api(Map<String, Object> m) {
            return ResourceSummary.builder()
                    .id(str(m.get("id")))
                    .name(str(m.get("name")))
                    .version(str(m.get("version")))
                    .type(str(m.get("type")))
                    .status(str(m.get("lifecycleStatus")))
                    .extra(map("context", str(m.get("context")),
                            "provider", str(m.get("provider"))))
                    .build();
        }

        static ResourceSummary application(Map<String, Object> m) {
            return ResourceSummary.builder()
                    .id(str(m.get("applicationId")))
                    .name(str(m.get("name")))
                    .status(str(m.get("status")))
                    .extra(map("owner", str(m.get("owner")),
                            "throttlingPolicy", str(m.get("throttlingPolicy"))))
                    .build();
        }

        static ResourceSummary subscription(Map<String, Object> m) {
            return ResourceSummary.builder()
                    .id(str(m.get("subscriptionId")))
                    .name(orJoin(m.get("apiInfo"), m.get("applicationInfo")))
                    .status(str(m.get("status")))
                    .extra(map("apiId", str(m.get("apiId")),
                            "applicationId", str(m.get("applicationId")),
                            "throttlingPolicy", str(m.get("throttlingPolicy"))))
                    .build();
        }

        static ResourceSummary apiProduct(Map<String, Object> m) {
            return ResourceSummary.builder()
                    .id(str(m.get("id")))
                    .name(str(m.get("name")))
                    .version(str(m.get("version")))
                    .status(str(m.get("state")))
                    .extra(map("context", str(m.get("context")),
                            "provider", str(m.get("provider"))))
                    .build();
        }

        static ResourceSummary keyManager(Map<String, Object> m) {
            return ResourceSummary.builder()
                    .id(str(m.get("id")))
                    .name(str(m.get("name")))
                    .type(str(m.get("type")))
                    .status(Boolean.TRUE.equals(m.get("enabled")) ? "ENABLED" : "DISABLED")
                    .extra(map("issuer", str(m.get("issuer"))))
                    .build();
        }

        static ResourceSummary scope(Map<String, Object> m) {
            return ResourceSummary.builder()
                    .id(str(m.get("id")))
                    .name(str(m.get("name")))
                    .extra(map("displayName", str(m.get("displayName")),
                            "description", str(m.get("description"))))
                    .build();
        }

        static ResourceSummary certificate(Map<String, Object> m) {
            return ResourceSummary.builder()
                    .id(str(m.get("alias")))
                    .name(str(m.get("alias")))
                    .extra(map("endpoint", str(m.get("endpoint"))))
                    .build();
        }

        static ResourceSummary throttlingPolicy(Map<String, Object> m, String policyType) {
            return ResourceSummary.builder()
                    .id(str(m.get("policyId")))
                    .name(str(m.get("policyName")))
                    .type(policyType)
                    .status(str(m.get("isDeployed")))
                    .extra(map("description", str(m.get("description"))))
                    .build();
        }

        // --------- tiny helpers -----------

        private static String str(Object o) {
            return o == null ? null : o.toString();
        }

        @SuppressWarnings("unchecked")
        private static String orJoin(Object apiInfo, Object appInfo) {
            String apiName = null, appName = null;
            if (apiInfo instanceof Map<?, ?> a) apiName = str(((Map<String, Object>) a).get("name"));
            if (appInfo instanceof Map<?, ?> a) appName = str(((Map<String, Object>) a).get("name"));
            if (apiName == null && appName == null) return null;
            return (appName == null ? "?" : appName) + " -> " + (apiName == null ? "?" : apiName);
        }

        private static Map<String, String> map(String... kv) {
            if (kv.length % 2 != 0) throw new IllegalArgumentException("kv pairs");
            Map<String, String> out = new LinkedHashMap<>();
            for (int i = 0; i < kv.length; i += 2) {
                if (kv[i + 1] != null && !kv[i + 1].isBlank()) out.put(kv[i], kv[i + 1]);
            }
            return out.isEmpty() ? null : out;
        }
    }
}
