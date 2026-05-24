package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * One row in the {@code GET /wso2/history} response: a past discovery
 * identified by ({@code discoveryId}, {@code revision}), with per-resource
 * counts so the UI can render a summary card without drilling down.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistorySnapshot {

    @Schema(description = "Correlation id allocated at discovery time", example = "32abcc62-...")
    private String discoveryId;

    @Schema(description = "Monotonic revision for this (companyName, wso2Tenant)", example = "3")
    private Integer revision;

    @Schema(description = "Earliest snapshot timestamp seen for this discoveryId")
    private Instant snapshotAt;

    @Schema(description = "Per-resource-type counts: {apis: 17, applications: 3, ...}")
    private Map<String, Integer> resourceCounts;

    @Schema(description = "Sum of every count in resourceCounts")
    private int totalCount;
}
