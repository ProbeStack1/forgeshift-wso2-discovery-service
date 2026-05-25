package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for {@code GET /wso2/history}. Same shape as the Apigee
 * {@code DiscoveryHistoryResponse} — companyName + tenant + history list +
 * processing time — with {@code wso2Tenant} standing in for Apigee's
 * (organization, environment) pair.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryResponse {

    @Schema(description = "Multi-tenancy partner id", example = "probestack")
    private String companyName;

    @Schema(description = "WSO2 tenant the history covers", example = "carbon.super")
    private String wso2Tenant;

    @Schema(description = "Last N discovery snapshots, newest first")
    private List<HistorySnapshot> history;

    @Schema(description = "Total API processing time in milliseconds", example = "286")
    private Long processingTimeMs;
}
