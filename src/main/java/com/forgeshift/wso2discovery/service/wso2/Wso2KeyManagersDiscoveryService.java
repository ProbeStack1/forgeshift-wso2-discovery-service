package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.KeyManagerDetail;
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
 * Discovers registered Key Managers from
 * {@code GET /api/am/admin/v4/key-managers} and writes one snapshot per KM to
 * {@code discovery_wso2_keymanagers}.
 *
 * Key Manager type drives the Kong auth-plugin choice downstream:
 *   default Resident → jwt plugin
 *   Keycloak/Okta/Auth0/WSO2-IS → openid-connect plugin
 */
@Slf4j
@Service
public class Wso2KeyManagersDiscoveryService extends BaseDiscoveryService {

    @Override
    public ResourceType getResourceType() {
        return ResourceType.KEY_MANAGERS;
    }

    @Override
    protected String scopeForResource() {
        return wso2Props.getAdminScope();
    }

    @Override
    protected List<Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req) {
        List<Map<String, Object>> kms = wso2Client.listKeyManagers(accessToken);
        log.info("[keymanagers] Admin returned {} key managers (company={} tenant={})",
                kms.size(), req.getCompanyName(), req.getWso2Tenant());
        return kms;
    }

    @Override
    protected DiscoverySnapshot buildSnapshot(Map<String, Object> item, DiscoverResourceRequest req, int revision) {
        String id = str(item.get("id"));
        String name = str(item.get("name"));

        Map<String, String> meta = new HashMap<>();
        putIfPresent(meta, "type", str(item.get("type")));
        putIfPresent(meta, "issuer", str(item.get("issuer")));
        putIfPresent(meta, "enabled", str(item.get("enabled")));
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
        List<KeyManagerDetail> details = snapshots.stream()
                .map(this::toDetail)
                .collect(Collectors.toList());
        response.setKeyManagerDetails(details);
    }

    private KeyManagerDetail toDetail(DiscoverySnapshot snap) {
        Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
        return KeyManagerDetail.builder()
                .id(snap.getSourceId())
                .name(snap.getSourceName())
                .displayName(str(p.get("displayName")))
                .type(str(p.get("type")))
                .enabled(asBool(p.get("enabled")))
                .description(str(p.get("description")))
                .issuer(str(p.get("issuer")))
                .tokenEndpoint(str(p.get("tokenEndpoint")))
                .introspectionEndpoint(str(p.get("introspectionEndpoint")))
                .revokeEndpoint(str(p.get("revokeEndpoint")))
                .authorizeEndpoint(str(p.get("authorizeEndpoint")))
                .userInfoEndpoint(str(p.get("userInfoEndpoint")))
                .scopeManagementEndpoint(str(p.get("scopeManagementEndpoint")))
                .jwksEndpoint(str(p.get("jwksEndpoint")))
                .availableGrantTypes(asStringList(p.get("availableGrantTypes")))
                .tokenType(str(p.get("tokenType")))
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

    @SuppressWarnings("unchecked")
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
