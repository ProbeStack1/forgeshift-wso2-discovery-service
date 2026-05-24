package com.forgeshift.wso2discovery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * One bulk-discovery run that fans out to multiple resource-type discoveries.
 *
 * Per-resource POST endpoints do NOT use this collection - they write
 * directly to {@link DiscoverySnapshot} documents and return synchronously.
 * This document only exists for the bulk {@code POST /discoveries} flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("discovery_jobs")
public class DiscoveryJob {

    @Id
    private String id;

    @Indexed
    private String companyName;

    @Indexed
    private String wso2Tenant;

    private String wso2BaseUrl;

    @Indexed
    private DiscoveryState state;

    /** The discoveryId stamped on every snapshot this job produces. */
    private String discoveryId;

    /** Revision allocated for this job. */
    private Integer revision;

    /** Per-resource progress: slug -> { state, count, lastError }. */
    @Builder.Default
    private Map<String, ResourceProgress> resourceProgress = new HashMap<>();

    @Builder.Default
    private Counts counts = new Counts();

    private String createdBy;

    private String lastError;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant completedAt;

    @Version
    private Long version;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Counts {
        @Builder.Default private int totalDiscovered = 0;
        @Builder.Default private int totalFailed = 0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResourceProgress {
        private String state;        // PENDING, RUNNING, COMPLETED, FAILED
        private int count;
        private String lastError;
        private Instant startedAt;
        private Instant completedAt;
    }
}
