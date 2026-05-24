package com.forgeshift.wso2discovery.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * WSO2 resource types this service can discover.
 *
 * Each value maps to:
 *   - a collection name suffix (used after {@code forgeshift.discovery.collection-prefix})
 *   - a per-resource REST endpoint
 *   - a concrete BaseDiscoveryService subclass
 *
 * Naming follows the Apigee reference pattern: lowercase, pluralized, no underscores
 * within a single concept so the resulting collection name is readable.
 */
@Getter
@RequiredArgsConstructor
public enum ResourceType {

    APIS("apis"),
    APPLICATIONS("applications"),
    SUBSCRIPTIONS("subscriptions"),
    THROTTLING_POLICIES("throttlingpolicies"),
    MEDIATION_POLICIES("mediationpolicies"),
    KEY_MANAGERS("keymanagers"),
    SCOPES("scopes"),
    API_PRODUCTS("apiproducts"),
    CERTIFICATES("certificates"),
    USERS("users");

    /** Lower-case slug used in URLs and collection names. */
    private final String slug;

    public String collectionName(String prefix) {
        return prefix + slug;
    }

    public static ResourceType fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(rt -> rt.slug.equalsIgnoreCase(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown resource type slug: " + slug));
    }

    public static final List<ResourceType> ALL = Arrays.asList(values());
}
