package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Response for {@code GET /wso2/stats/{dimension}}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatsResponse {

    @Schema(description = "Multi-tenancy partner id (filter applied when supplied)")
    private String companyName;

    @Schema(description = "WSO2 tenant (filter applied when supplied)")
    private String wso2Tenant;

    @Schema(description = "Dimension queried",
            allowableValues = {"byResourceType", "byTenant", "byDiscoveryId", "byTime"})
    private String dimension;

    @Schema(description = "Server timestamp at query time")
    private Instant timestamp;

    @Schema(description = "Total snapshots counted across all returned buckets")
    private long totalCount;

    @Schema(description = "When dimension uses a single key per bucket - {bucket: count}")
    private Map<String, Long> counts;

    @Schema(description = "When dimension needs richer rows - one entry per bucket")
    private List<Bucket> buckets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Bucket {
        @Schema(description = "Bucket label (e.g. discoveryId, day, slug)")
        private String key;

        @Schema(description = "Optional secondary label (e.g. revision number)")
        private String subkey;

        @Schema(description = "Items in this bucket")
        private long count;

        @Schema(description = "When the bucket has a timestamp (byTime, byDiscoveryId)")
        private Instant snapshotAt;

        @Schema(description = "Optional extras for the UI")
        private Map<String, Object> meta;
    }
}
