package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** Response for {@code GET /wso2/history/revisions}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryRevisionsResponse {

    @Schema(description = "Multi-tenancy partner id")
    private String companyName;

    @Schema(description = "WSO2 tenant")
    private String wso2Tenant;

    @Schema(description = "Latest allocated revision for this (companyName, wso2Tenant)")
    private Integer currentRevision;

    @Schema(description = "Last operator who triggered a discovery (lastUserEmail on the counter)")
    private String lastUserEmail;

    @Schema(description = "Timestamp of the most recent counter bump")
    private Instant lastBumpAt;

    @Schema(description = "All revision numbers that ever existed, ascending")
    private List<Integer> revisions;
}
