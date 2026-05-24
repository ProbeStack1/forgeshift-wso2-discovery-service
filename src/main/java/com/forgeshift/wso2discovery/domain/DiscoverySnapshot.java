package com.forgeshift.wso2discovery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * One snapshot of one WSO2 resource at one revision.
 *
 * Mirrors the Apigee snapshot shape: per-(companyName, tenant, resourceType,
 * sourceId, revision) document, written to per-resource collections such as
 * {@code discovery_wso2_apis}.
 *
 * The composite document {@code id} makes upserts idempotent across re-runs
 * of the same discoveryId.
 *
 * No {@code @Document} annotation: collections are addressed by name at
 * runtime via MongoTemplate so we do not need one Java class per collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoverySnapshot {

    /** Composite id: companyName|tenant|resourceType|sourceId|revision */
    private String id;

    /** Caller-supplied multi-tenancy identifier. Defaults to "probestack". */
    private String companyName;

    /** WSO2 tenant the resource belongs to (e.g. "carbon.super"). */
    private String wso2Tenant;

    /** The discovery transaction id that produced this snapshot. */
    private String discoveryId;

    /** Monotonic revision number per (companyName, wso2Tenant). */
    private Integer revision;

    /** Resource type slug (apis, applications, ...). */
    private String resourceType;

    /** WSO2-side identifier (api uuid, app uuid, etc.). */
    private String sourceId;

    private String sourceName;

    private String sourceVersion;

    /** Full WSO2 payload as returned by the source API, unmodified. */
    private Map<String, Object> payload;

    /** Lifecycle status, content type, tags, etc. */
    private Map<String, String> metadata;

    private Instant snapshotAt;
}
