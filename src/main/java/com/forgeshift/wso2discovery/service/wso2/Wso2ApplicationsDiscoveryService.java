package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.ApplicationDetail;
import com.forgeshift.wso2discovery.service.BaseDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Discovers DevPortal Applications from {@code GET /api/am/devportal/v3/applications}
 * and persists one snapshot per application into {@code discovery_wso2_applications}.
 *
 * Unlike APIs, Applications are listed only — there is no per-item enrichment
 * call. The list response already contains every field we need.
 */
@Slf4j
@Service
public class Wso2ApplicationsDiscoveryService extends BaseDiscoveryService {

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.APPLICATIONS;
    }

    @Override
    protected String scopeForResource() {
        return wso2Props.getDevportalScope();
    }

    @Override
    protected List<Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req) {
        List<Map<String, Object>> applications = wso2Client.listApplications(accessToken);
        log.info("[applications] DevPortal returned {} applications (company={} tenant={})",
                applications.size(), req.getCompanyName(), req.getWso2Tenant());
        return applications;
    }

    @Override
    protected DiscoverySnapshot buildSnapshot(Map<String, Object> item, DiscoverResourceRequest req, int revision) {
        String appId = str(item.get("applicationId"));
        String name = str(item.get("name"));

        Map<String, String> meta = new HashMap<>();
        putIfPresent(meta, "owner", str(item.get("owner")));
        putIfPresent(meta, "status", str(item.get("status")));
        putIfPresent(meta, "throttlingPolicy", str(item.get("throttlingPolicy")));
        putIfPresent(meta, "tokenType", str(item.get("tokenType")));
        putIfPresent(meta, "groupId", str(item.get("groupId")));
        if (req.getEnvironment() != null) meta.put("environment", req.getEnvironment());

        return DiscoverySnapshot.builder()
                .sourceId(appId)
                .sourceName(name)
                .payload(item)
                .metadata(meta)
                .build();
    }

    @Override
    protected void populateDetails(DiscoverResourceResponse response, List<DiscoverySnapshot> snapshots) {
        List<ApplicationDetail> details = snapshots.stream()
                .map(this::toDetail)
                .collect(Collectors.toList());
        response.setApplicationDetails(details);
    }

    private ApplicationDetail toDetail(DiscoverySnapshot snap) {
        Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
        return ApplicationDetail.builder()
                .id(snap.getSourceId())
                .name(snap.getSourceName())
                .owner(str(p.get("owner")))
                .status(str(p.get("status")))
                .throttlingPolicy(str(p.get("throttlingPolicy")))
                .description(str(p.get("description")))
                .tokenType(str(p.get("tokenType")))
                .groupId(str(p.get("groupId")))
                .subscriber(str(p.get("subscriber")))
                .subscriptionCount(asInt(p.get("subscriptionCount")))
                .build();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return null; }
    }

    private static void putIfPresent(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
}
