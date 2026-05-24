package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Response for {@code GET /wso2/history}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryResponse {

    @Schema(description = "Multi-tenancy partner id")
    private String companyName;

    @Schema(description = "WSO2 tenant the history covers")
    private String wso2Tenant;

    @Schema(description = "Optional resourceType filter applied")
    private String resourceType;

    @Schema(description = "Discoveries in reverse-chronological order (most recent first)")
    private List<HistorySnapshot> snapshots;

    @Schema(description = "Number of entries in snapshots")
    private int totalSnapshots;
}
