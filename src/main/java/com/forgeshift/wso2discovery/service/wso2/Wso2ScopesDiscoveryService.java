package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.ScopeDetail;
import com.forgeshift.wso2discovery.service.BaseDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Discovers shared OAuth2 Scopes from {@code GET /api/am/admin/v4/scopes}
 * and writes one snapshot per scope to {@code discovery_wso2_scopes}.
 */
@Slf4j
@Service
public class Wso2ScopesDiscoveryService extends BaseDiscoveryService {

    @Override
    public ResourceType getResourceType() {
        return ResourceType.SCOPES;
    }

    @Override
    protected String scopeForResource() {
        return wso2Props.getAdminScope();
    }

    @Override
    protected List<Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req) {
        List<Map<String, Object>> scopes = wso2Client.listScopes(accessToken);
        log.info("[scopes] Admin returned {} scopes (company={} tenant={})",
                scopes.size(), req.getCompanyName(), req.getWso2Tenant());
        return scopes;
    }

    @Override
    protected DiscoverySnapshot buildSnapshot(Map<String, Object> item, DiscoverResourceRequest req, int revision) {
        String id = str(item.get("id"));
        String name = str(item.get("name"));

        Map<String, String> meta = new HashMap<>();
        putIfPresent(meta, "displayName", str(item.get("displayName")));
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
        List<ScopeDetail> details = snapshots.stream()
                .map(this::toDetail)
                .collect(Collectors.toList());
        response.setScopeDetails(details);
    }

    private ScopeDetail toDetail(DiscoverySnapshot snap) {
        Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
        return ScopeDetail.builder()
                .id(snap.getSourceId())
                .name(snap.getSourceName())
                .displayName(str(p.get("displayName")))
                .description(str(p.get("description")))
                .bindings(asStringList(p.get("bindings")))
                .usageCount(asInt(p.get("usageCount")))
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

    private static List<String> asStringList(Object o) {
        if (o == null) return null;
        if (o instanceof Collection<?> c) {
            List<String> out = new ArrayList<>(c.size());
            for (Object x : c) if (x != null) out.add(x.toString());
            return out.isEmpty() ? null : out;
        }
        return null;
    }

    private static void putIfPresent(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
}
