package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.ApiDetail;
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
 * Discovers WSO2 APIs from the Publisher REST API and persists one snapshot
 * per API into {@code discovery_wso2_apis}.
 *
 * Steps per call:
 *   1. List APIs (cheap summary fields only) via Wso2Client.listApis
 *   2. For each summary, fetch the full definition via Wso2Client.getApi so
 *      the snapshot payload contains the production endpoint, throttling
 *      policy, securityScheme, CORS, etc.
 *   3. Build a {@link DiscoverySnapshot} keyed by the WSO2 api id.
 *
 * Errors fetching one API do not fail the whole run; failures are logged
 * and that API is omitted from the snapshot set.
 */
@Slf4j
@Service
public class Wso2ApisDiscoveryService extends BaseDiscoveryService {

    @Override
    public ResourceType getResourceType() {
        return ResourceType.APIS;
    }

    @Override
    protected List<Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req) {
        // Step 1: list
        List<Map<String, Object>> summaries = wso2Client.listApis(accessToken);
        log.info("[apis] Publisher returned {} API summaries (company={} tenant={})",
                summaries.size(), req.getCompanyName(), req.getWso2Tenant());

        // Step 2: enrich each with the full definition
        List<Map<String, Object>> enriched = new ArrayList<>(summaries.size());
        for (Map<String, Object> summary : summaries) {
            Object idObj = summary.get("id");
            if (idObj == null) {
                log.warn("[apis] Skipping summary with no id: {}", summary);
                continue;
            }
            String apiId = idObj.toString();
            Map<String, Object> full = wso2Client.getApi(accessToken, apiId);
            if (full == null) {
                log.warn("[apis] getApi returned null for id={}, falling back to summary only", apiId);
                enriched.add(summary);
            } else {
                // Merge: full overrides summary fields, but keep both for traceability.
                Map<String, Object> merged = new HashMap<>(summary);
                merged.putAll(full);
                enriched.add(merged);
            }
        }
        return enriched;
    }

    @Override
    protected DiscoverySnapshot buildSnapshot(Map<String, Object> item, DiscoverResourceRequest req, int revision) {
        String apiId = str(item.get("id"));
        String name = str(item.get("name"));
        String version = str(item.get("version"));

        Map<String, String> meta = new HashMap<>();
        putIfPresent(meta, "context", str(item.get("context")));
        putIfPresent(meta, "lifecycleStatus", str(item.get("lifecycleStatus")));
        putIfPresent(meta, "provider", str(item.get("provider")));
        putIfPresent(meta, "type", str(item.get("type")));
        if (req.getEnvironment() != null) meta.put("environment", req.getEnvironment());

        return DiscoverySnapshot.builder()
                .sourceId(apiId)
                .sourceName(name)
                .sourceVersion(version)
                .payload(item)
                .metadata(meta)
                .build();
    }

    /**
     * Project each persisted snapshot into a UI-friendly {@link ApiDetail}
     * and attach the list to the response. Keeps the UI to a single round
     * trip per discovery.
     */
    @Override
    protected void populateDetails(DiscoverResourceResponse response, List<DiscoverySnapshot> snapshots) {
        List<ApiDetail> details = snapshots.stream()
                .map(this::toApiDetail)
                .collect(Collectors.toList());
        response.setApiDetails(details);
    }

    private ApiDetail toApiDetail(DiscoverySnapshot snap) {
        Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
        return ApiDetail.builder()
                .id(snap.getSourceId())
                .name(snap.getSourceName())
                .version(snap.getSourceVersion())
                .context(str(p.get("context")))
                .lifecycleStatus(str(p.get("lifecycleStatus")))
                .provider(str(p.get("provider")))
                .type(str(p.get("type")))
                .transports(asStringList(p.get("transport")))
                .tags(asStringList(p.get("tags")))
                .description(str(p.get("description")))
                .build();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static void putIfPresent(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }

    /** WSO2 returns transports and tags as JSON arrays; coerce to List<String>. */
    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object o) {
        if (o == null) return null;
        if (o instanceof Collection<?> c) {
            List<String> out = new ArrayList<>(c.size());
            for (Object x : c) if (x != null) out.add(x.toString());
            return out.isEmpty() ? null : out;
        }
        // Some WSO2 endpoints return comma-separated strings, e.g. transport=http,https
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("[")) return null; // pathological; let it through as null
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out.isEmpty() ? null : out;
    }
}
