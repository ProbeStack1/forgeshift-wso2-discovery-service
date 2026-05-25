package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.ApiProductDetail;
import com.forgeshift.wso2discovery.service.BaseDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Discovers API Products from {@code GET /api/am/publisher/v4/api-products}
 * and persists one snapshot per product into {@code discovery_wso2_apiproducts}.
 */
@Slf4j
@Service
public class Wso2ApiProductsDiscoveryService extends BaseDiscoveryService {

    @Override
    public ResourceType getResourceType() {
        return ResourceType.API_PRODUCTS;
    }

    @Override
    protected String scopeForResource() {
        return wso2Props.getPublisherScope();
    }

    @Override
    protected List<Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req) {
        List<Map<String, Object>> products = wso2Client.listApiProducts(accessToken);
        log.info("[apiproducts] Publisher returned {} products (company={} tenant={})",
                products.size(), req.getCompanyName(), req.getWso2Tenant());
        return products;
    }

    @Override
    protected DiscoverySnapshot buildSnapshot(Map<String, Object> item, DiscoverResourceRequest req, int revision) {
        String id = str(item.get("id"));
        String name = str(item.get("name"));
        String version = str(item.get("version"));

        Map<String, String> meta = new HashMap<>();
        putIfPresent(meta, "version", version);
        putIfPresent(meta, "context", str(item.get("context")));
        putIfPresent(meta, "state", str(item.get("state")));
        putIfPresent(meta, "provider", str(item.get("provider")));
        if (req.getEnvironment() != null) meta.put("environment", req.getEnvironment());

        return DiscoverySnapshot.builder()
                .sourceId(id)
                .sourceName(name)
                .sourceVersion(version)
                .payload(item)
                .metadata(meta)
                .build();
    }

    @Override
    protected void populateDetails(DiscoverResourceResponse response, List<DiscoverySnapshot> snapshots) {
        List<ApiProductDetail> details = snapshots.stream()
                .map(this::toDetail)
                .collect(Collectors.toList());
        response.setApiProductDetails(details);
    }

    private ApiProductDetail toDetail(DiscoverySnapshot snap) {
        Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
        Object apis = p.get("apis");
        Integer apiCount = (apis instanceof Collection<?> c) ? c.size() : null;
        return ApiProductDetail.builder()
                .id(snap.getSourceId())
                .name(snap.getSourceName())
                .version(snap.getSourceVersion())
                .context(str(p.get("context")))
                .provider(str(p.get("provider")))
                .state(str(p.get("state")))
                .description(str(p.get("description")))
                .apiCount(apiCount)
                .build();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static void putIfPresent(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }
}
