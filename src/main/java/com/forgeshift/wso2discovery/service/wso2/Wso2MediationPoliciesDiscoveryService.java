package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.MediationPolicyDetail;
import com.forgeshift.wso2discovery.service.BaseDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Discovers Mediation Policies and writes one snapshot per policy to
 * {@code discovery_wso2_mediationpolicies}.
 *
 * WSO2 4.x has no tenant-wide mediation-policies endpoint - policies are
 * per-API. This service:
 *   1. Calls {@link com.forgeshift.wso2discovery.client.Wso2Client#listApis listApis()} to enumerate APIs.
 *   2. For each API, calls /apis/{apiId}/mediation-policies.
 *   3. Tags each snapshot with {@code apiId / apiName / apiVersion} so the
 *      downstream translator can join back to the api snapshot.
 *
 * Cost is O(N) Publisher calls where N = number of APIs. Discovery is
 * sequential here for safety; parallelism is a later optimization.
 */
@Slf4j
@Service
public class Wso2MediationPoliciesDiscoveryService extends BaseDiscoveryService {

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.MEDIATION_POLICIES;
    }

    @Override
    protected String scopeForResource() {
        return wso2Props.getPublisherScope();
    }

    @Override
    protected List<Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req) {
        List<Map<String, Object>> apis = wso2Client.listApis(accessToken);
        log.info("[mediationpolicies] iterating {} APIs for per-API mediation policies (company={} tenant={})",
                apis.size(), req.getCompanyName(), req.getWso2Tenant());

        List<Map<String, Object>> all = new ArrayList<>();
        int policyCount = 0;
        for (Map<String, Object> api : apis) {
            String apiId = str(api.get("id"));
            String apiName = str(api.get("name"));
            String apiVersion = str(api.get("version"));
            if (apiId == null) continue;

            try {
                List<Map<String, Object>> policies = wso2Client.listMediationPoliciesForApi(accessToken, apiId);
                for (Map<String, Object> p : policies) {
                    // copy + enrich so we don't mutate the caller's map
                    Map<String, Object> tagged = new LinkedHashMap<>(p);
                    tagged.put("__apiId", apiId);
                    tagged.put("__apiName", apiName);
                    tagged.put("__apiVersion", apiVersion);
                    tagged.put("__scope", "api");
                    all.add(tagged);
                }
                policyCount += policies.size();
            } catch (Exception e) {
                log.warn("[mediationpolicies] failed for api {} ({}): {}", apiName, apiId, e.getMessage());
            }
        }
        log.info("[mediationpolicies] aggregated {} policies across {} APIs", policyCount, apis.size());
        return all;
    }

    @Override
    protected DiscoverySnapshot buildSnapshot(Map<String, Object> item, DiscoverResourceRequest req, int revision) {
        String id = str(item.get("id"));
        String name = str(item.get("name"));
        String type = str(item.get("type"));            // in / out / fault
        String apiId = str(item.get("__apiId"));
        // Composite source id: apiId + policy id keeps cross-API uniqueness.
        String compositeSourceId = apiId + "::" + (id != null ? id : name);

        Map<String, String> meta = new HashMap<>();
        putIfPresent(meta, "scope", str(item.get("__scope")));
        putIfPresent(meta, "type", type);
        putIfPresent(meta, "apiId", apiId);
        putIfPresent(meta, "apiName", str(item.get("__apiName")));
        putIfPresent(meta, "apiVersion", str(item.get("__apiVersion")));
        putIfPresent(meta, "shared", str(item.get("shared")));
        if (req.getEnvironment() != null) meta.put("environment", req.getEnvironment());

        return DiscoverySnapshot.builder()
                .sourceId(compositeSourceId)
                .sourceName(name)
                .payload(item)
                .metadata(meta)
                .build();
    }

    @Override
    protected void populateDetails(DiscoverResourceResponse response, List<DiscoverySnapshot> snapshots) {
        List<MediationPolicyDetail> details = snapshots.stream()
                .map(this::toDetail)
                .collect(Collectors.toList());
        response.setMediationPolicyDetails(details);
    }

    private MediationPolicyDetail toDetail(DiscoverySnapshot snap) {
        Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
        return MediationPolicyDetail.builder()
                .id(str(p.get("id")))
                .name(snap.getSourceName())
                .type(str(p.get("type")))
                .scope(str(p.get("__scope")))
                .apiId(str(p.get("__apiId")))
                .apiName(str(p.get("__apiName")))
                .apiVersion(str(p.get("__apiVersion")))
                .shared(asBool(p.get("shared")))
                .contentType(str(p.get("contentType")))
                .build();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Boolean asBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(o.toString());
    }

    private static void putIfPresent(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
}
