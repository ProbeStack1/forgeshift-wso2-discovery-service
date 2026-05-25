package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.ThrottlingPolicyDetail;
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
 * Discovers Throttling Policies and writes one snapshot per policy to
 * {@code discovery_wso2_throttlingpolicies}.
 *
 * WSO2 splits throttling into three tier families, each on its own admin
 * endpoint. This service fetches all three and merges them, tagging each
 * snapshot with {@code policyType} so the UI can filter.
 */
@Slf4j
@Service
public class Wso2ThrottlingPoliciesDiscoveryService extends BaseDiscoveryService {

    @Override
    public ResourceType getResourceType() {
        return ResourceType.THROTTLING_POLICIES;
    }

    @Override
    protected String scopeForResource() {
        return wso2Props.getAdminScope();
    }

    @Override
    protected List<Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req) {
        List<Map<String, Object>> all = new ArrayList<>();
        appendTagged(all, wso2Client.listSubscriptionThrottlingPolicies(accessToken), "subscription");
        appendTagged(all, wso2Client.listApplicationThrottlingPolicies(accessToken), "application");
        appendTagged(all, wso2Client.listAdvancedThrottlingPolicies(accessToken), "advanced");
        log.info("[throttlingpolicies] Admin returned {} policies (company={} tenant={})",
                all.size(), req.getCompanyName(), req.getWso2Tenant());
        return all;
    }

    private static void appendTagged(List<Map<String, Object>> out, List<Map<String, Object>> in, String policyType) {
        if (in == null) return;
        for (Map<String, Object> p : in) {
            // copy so we don't mutate the original (defensive; the lists are local but
            // this keeps the helper safe to call against shared maps in the future)
            Map<String, Object> tagged = new LinkedHashMap<>(p);
            tagged.put("__policyType", policyType);
            out.add(tagged);
        }
    }

    @Override
    protected DiscoverySnapshot buildSnapshot(Map<String, Object> item, DiscoverResourceRequest req, int revision) {
        String id = str(item.get("policyId"));
        String name = str(item.get("policyName"));
        String policyType = str(item.get("__policyType"));

        Map<String, String> meta = new HashMap<>();
        putIfPresent(meta, "policyType", policyType);
        putIfPresent(meta, "displayName", str(item.get("displayName")));
        putIfPresent(meta, "isDeployed", str(item.get("isDeployed")));
        if (req.getEnvironment() != null) meta.put("environment", req.getEnvironment());

        return DiscoverySnapshot.builder()
                .sourceId(id != null ? id : name)
                .sourceName(name)
                .payload(item)
                .metadata(meta)
                .build();
    }

    @Override
    protected void populateDetails(DiscoverResourceResponse response, List<DiscoverySnapshot> snapshots) {
        List<ThrottlingPolicyDetail> details = snapshots.stream()
                .map(this::toDetail)
                .collect(Collectors.toList());
        response.setThrottlingPolicyDetails(details);
    }

    private ThrottlingPolicyDetail toDetail(DiscoverySnapshot snap) {
        Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
        Map<String, Object> limit = nestedMap(p, "defaultLimit");

        return ThrottlingPolicyDetail.builder()
                .id(snap.getSourceId())
                .name(snap.getSourceName())
                .displayName(str(p.get("displayName")))
                .description(str(p.get("description")))
                .policyType(str(p.get("__policyType")))
                .requestCount(asLong(limit.get("requestCount")))
                .dataAmount(asLong(limit.get("dataAmount")))
                .dataUnit(str(limit.get("dataUnit")))
                .timeUnit(str(limit.get("timeUnit")))
                .unitTime(asInt(limit.get("unitTime")))
                .stopOnQuotaReach(asBool(p.get("stopOnQuotaReach")))
                .billingPlan(str(p.get("billingPlan")))
                .isDeployed(asBool(p.get("isDeployed")))
                .subscriberCount(asInt(p.get("subscriberCount")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> root, String key) {
        Object v = root.get(key);
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Collections.emptyMap();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return null; }
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
