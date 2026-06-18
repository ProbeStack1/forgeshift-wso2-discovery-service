package com.forgeshift.wso2discovery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables and collection names for the discovery pipeline.
 *
 * Bound from the {@code forgeshift.discovery.*} properties.
 */
@Data
@ConfigurationProperties(prefix = "forgeshift.discovery")
public class DiscoveryProperties {

    /** Prefix prepended to per-resource collection names. */
    private String collectionPrefix = "discovery_wso2_";

    /** Collection that holds the per-(tenant) revision counter. */
    private String revisionsCollection = "discovery_revisions";

    /**
     * Collection that maps (company|tenant|discoveryId) → revision, so the
     * caller can issue multiple resource discoveries under one discovery run
     * and have them all share the same revision (mirrors the Apigee pattern).
     */
    private String revisionMapCollection = "discovery_revision_map";

    /** Audit log collection. */
    private String auditCollection = "wso2_migration_audit_info";

    /** Discovered tenants/orgs collection. */
    private String organizationsCollection = "wso2_organizations";

    /** Per-tenant credential profiles collection (mirrors Apigee 'profiles'). */
    private String profilesCollection = "wso2_profiles";

    /** Normalized WSO2 user profiles discovered for downstream user migration. */
    private String userProfilesCollection = "wso2_user_profiles";

    /** Business-metadata collection for POST /wso2/assetinfo. */
    private String assetInfoCollection = "probestack_wso2_asset_info";

    /** Denormalized application-to-API join collection. */
    private String appApiRelationsCollection = "app_api_relations";

    /** Whether to write an audit row for every REST request. */
    private boolean auditEnabled = true;

    /** Default companyName when caller does not supply one (multi-tenancy stub). */
    private String defaultCompanyName = "probestack";

    /** Worker pool size used by @Async bulk-discovery fan-out. */
    private int parallelThreadPoolSize = 4;

    /** Per-download timeout in seconds. */
    private int downloadTimeoutSeconds = 60;
}
