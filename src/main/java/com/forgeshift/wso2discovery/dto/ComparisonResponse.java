package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Response for {@code GET /wso2/compare}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComparisonResponse {

    @Schema(description = "Multi-tenancy partner id")
    private String companyName;

    @Schema(description = "WSO2 tenant")
    private String wso2Tenant;

    @Schema(description = "Source discoveryId (the 'before' snapshot set)")
    private String sourceDiscoveryId;

    @Schema(description = "Target discoveryId (the 'after' snapshot set)")
    private String targetDiscoveryId;

    @Schema(description = "Resource-type filter that was applied, or null if comparing all types")
    private String resourceType;

    @Schema(description = "Per-resource-type diff. Keys are resource slugs (apis, applications, ...).")
    private Map<String, ResourceDiff> diff;

    @Schema(description = "Roll-up counts across every resource type compared")
    private Summary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourceDiff {
        @Schema(description = "Items in target but not in source")
        private List<DiffItem> added;
        @Schema(description = "Items in source but not in target")
        private List<DiffItem> removed;
        @Schema(description = "Items present in both with differing payload")
        private List<DiffItem> changed;
        @Schema(description = "Count of items present in both and byte-identical")
        private int unchangedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DiffItem {
        @Schema(description = "WSO2 source id (api uuid, app uuid, ...)")
        private String sourceId;
        @Schema(description = "Human-readable name")
        private String sourceName;
        @Schema(description = "Version, when applicable")
        private String sourceVersion;
        @Schema(description = "List of field paths that differ, when this is a 'changed' entry")
        private List<String> changedFields;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Summary {
        private int added;
        private int removed;
        private int changed;
        private int unchanged;
    }
}
