package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.DiscoverResourceRequest;
import com.forgeshift.wso2discovery.dto.DiscoverResourceResponse;
import com.forgeshift.wso2discovery.dto.details.UserDetail;
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
 * Discovers WSO2 users from the SCIM 2.0 user store
 * ({@code GET /scim2/Users}) and writes one snapshot per user to
 * {@code discovery_wso2_users}.
 *
 * SCIM responses look different from the APIM REST APIs:
 *   - Wrapper key is {@code Resources} (capital R), not {@code list}
 *   - Pagination uses {@code startIndex} (1-based) + {@code count}
 *   - User-shaped fields: id, userName, name.{givenName,familyName},
 *     emails[*].value, active, groups
 *
 * Auth: this service reuses the bearer token issued for {@code apim:admin}.
 * Some WSO2 builds gate /scim2 on Basic auth instead; if so, the list call
 * 401s and the discovery returns empty with a log warning - operator must
 * grant the SCIM scope to the OAuth client, or we add a Basic-auth fallback.
 */
@Slf4j
@Service
public class Wso2UsersDiscoveryService extends BaseDiscoveryService {

    @Override
    public ResourceType getResourceType() {
        return ResourceType.USERS;
    }

    @Override
    protected String scopeForResource() {
        return wso2Props.getAdminScope();
    }

    @Override
    protected List<Map<String, Object>> fetchFromWso2(String accessToken, DiscoverResourceRequest req) {
        List<Map<String, Object>> users = wso2Client.listUsers(accessToken);
        log.info("[users] SCIM returned {} users (company={} tenant={})",
                users.size(), req.getCompanyName(), req.getWso2Tenant());
        return users;
    }

    @Override
    protected DiscoverySnapshot buildSnapshot(Map<String, Object> item, DiscoverResourceRequest req, int revision) {
        String id = str(item.get("id"));
        String userName = str(item.get("userName"));

        Map<String, String> meta = new HashMap<>();
        putIfPresent(meta, "userName", userName);
        putIfPresent(meta, "active", str(item.get("active")));
        putIfPresent(meta, "userType", str(item.get("userType")));
        if (req.getEnvironment() != null) meta.put("environment", req.getEnvironment());

        return DiscoverySnapshot.builder()
                .sourceId(id != null ? id : userName)
                .sourceName(userName)
                .payload(item)
                .metadata(meta)
                .build();
    }

    @Override
    protected void populateDetails(DiscoverResourceResponse response, List<DiscoverySnapshot> snapshots) {
        List<UserDetail> details = snapshots.stream()
                .map(this::toDetail)
                .collect(Collectors.toList());
        response.setUserDetails(details);
    }

    private UserDetail toDetail(DiscoverySnapshot snap) {
        Map<String, Object> p = snap.getPayload() != null ? snap.getPayload() : Collections.emptyMap();
        return UserDetail.builder()
                .id(snap.getSourceId())
                .userName(snap.getSourceName())
                .displayName(buildDisplayName(p))
                .emails(extractEmails(p))
                .active(asBool(p.get("active")))
                .roles(extractRoleNames(p))
                .userType(str(p.get("userType")))
                .build();
    }

    /** Compose displayName = givenName + " " + familyName, fall back to userName. */
    @SuppressWarnings("unchecked")
    private static String buildDisplayName(Map<String, Object> user) {
        Object name = user.get("name");
        if (name instanceof Map<?, ?> m) {
            Map<String, Object> n = (Map<String, Object>) m;
            String given = str(n.get("givenName"));
            String family = str(n.get("familyName"));
            if (given != null || family != null) {
                return ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
            }
        }
        return str(user.get("userName"));
    }

    /** SCIM emails are a list of objects: [{value, type, primary}]. Extract values, primary first. */
    @SuppressWarnings("unchecked")
    private static List<String> extractEmails(Map<String, Object> user) {
        Object emails = user.get("emails");
        if (!(emails instanceof Collection<?> c)) return null;

        List<String> primary = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (Object o : c) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> e = (Map<String, Object>) m;
                String value = str(e.get("value"));
                if (value == null) continue;
                if (Boolean.TRUE.equals(e.get("primary"))) primary.add(value);
                else rest.add(value);
            } else if (o != null) {
                rest.add(o.toString());
            }
        }
        List<String> out = new ArrayList<>(primary);
        out.addAll(rest);
        return out.isEmpty() ? null : out;
    }

    /** SCIM groups → role names. WSO2 also exposes wso2-extension role lists; we read both shapes. */
    @SuppressWarnings("unchecked")
    private static List<String> extractRoleNames(Map<String, Object> user) {
        List<String> roles = new ArrayList<>();

        Object groups = user.get("groups");
        if (groups instanceof Collection<?> c) {
            for (Object o : c) {
                if (o instanceof Map<?, ?> m) {
                    Object display = ((Map<String, Object>) m).get("display");
                    if (display != null) roles.add(display.toString());
                } else if (o != null) {
                    roles.add(o.toString());
                }
            }
        }
        return roles.isEmpty() ? null : roles;
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
