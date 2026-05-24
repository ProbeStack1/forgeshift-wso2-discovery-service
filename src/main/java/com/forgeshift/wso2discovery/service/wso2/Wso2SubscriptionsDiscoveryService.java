package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.SubscriptionDetail;
import com.forgeshift.wso2discovery.service.AppApiRelationService;
import com.forgeshift.wso2discovery.service.BaseDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Discovers app ↔ API subscriptions from
 * {@code GET /api/am/devportal/v3/subscriptions} and writes one snapshot per
 * subscription to {@code discovery_wso2_subscriptions}.
 *
 * Subscriptions are the spine of the migrator: every Consumer + credential
 * created on the Kong side starts from a row in this collection.
 */
@Slf4j
@Service
public class Wso2SubscriptionsDiscoveryService extends BaseDiscoveryService {

    private AppApiRelationService appApiRelationService;

    @Autowired
    public void setAppApiRelationService(AppApiRelationService s) {
        this.appApiRelationService = s;
    }

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.SUBSCRIPTIONS;
    }

    @Override
    protected String scopeForResource() {
        return wso2Props.getDevportalScope();
    }

    @Override
    protected List<Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req) {
        List<Map<String, Object>> subs = wso2Client.listSubscriptions(accessToken);
        log.info("[subscriptions] DevPortal returned {} subscriptions (company={} tenant={})",
                subs.size(), req.getCompanyName(), req.getWso2Tenant());
        return subs;
    }

    @Override
    protected DiscoverySnapshot buildSnapshot(Map<String, Object> item, DiscoverResourceRequest req, int revision) {
        String subId = str(item.get("subscriptionId"));
        String appName = mapField(item, "applicationInfo", "name");
        String apiName = mapField(item, "apiInfo", "name");
        String synthetic = (appName == null ? "?" : appName) + " -> " + (apiName == null ? "?" : apiName);

        Map<String, String> meta = new HashMap<>();
        putIfPresent(meta, "applicationId", str(item.get("applicationId")));
        putIfPresent(meta, "apiId", str(item.get("apiId")));
        putIfPresent(meta, "throttlingPolicy", str(item.get("throttlingPolicy")));
        putIfPresent(meta, "status", str(item.get("status")));
        if (req.getEnvironment() != null) meta.put("environment", req.getEnvironment());

        return DiscoverySnapshot.builder()
                .sourceId(subId)
                .sourceName(synthetic)
                .payload(item)
                .metadata(meta)
                .build();
    }

    @Override
    protected void populateDetails(DiscoverResourceResponse response, List<DiscoverySnapshot> snapshots) {
        List<SubscriptionDetail> details = snapshots.stream()
                .map(this::toDetail)
                .collect(Collectors.toList());
        response.setSubscriptionDetails(details);

        // Refresh the denormalized app <-> API join from this batch.
        if (appApiRelationService != null) {
            appApiRelationService.recordFromSubscriptionSnapshots(snapshots);
        }
    }

    private SubscriptionDetail toDetail(DiscoverySnapshot snap) {
        Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
        return SubscriptionDetail.builder()
                .id(snap.getSourceId())
                .applicationId(str(p.get("applicationId")))
                .applicationName(mapField(p, "applicationInfo", "name"))
                .apiId(str(p.get("apiId")))
                .apiName(mapField(p, "apiInfo", "name"))
                .apiVersion(mapField(p, "apiInfo", "version"))
                .apiContext(mapField(p, "apiInfo", "context"))
                .throttlingPolicy(str(p.get("throttlingPolicy")))
                .status(str(p.get("status")))
                .requestedThrottlingPolicy(str(p.get("requestedThrottlingPolicy")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static String mapField(Map<String, Object> root, String outerKey, String innerKey) {
        Object outer = root.get(outerKey);
        if (outer instanceof Map<?, ?> m) {
            Object v = ((Map<String, Object>) m).get(innerKey);
            return v == null ? null : v.toString();
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static void putIfPresent(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
}
