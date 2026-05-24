package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Response for {@code GET /wso2/history/details}. Returns the full snapshot rows. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryDetailsResponse {

    @Schema(description = "Multi-tenancy partner id")
    private String companyName;

    @Schema(description = "WSO2 tenant")
    private String wso2Tenant;

    @Schema(description = "Resource type slug being detailed", example = "apis")
    private String resourceType;

    @Schema(description = "Filter discoveryId, when supplied")
    private String discoveryId;

    @Schema(description = "Filter revision, when supplied")
    private Integer revision;

    @Schema(description = "Mongo collection backing this resource type", example = "discovery_wso2_apis")
    private String collectionName;

    @Schema(description = "Raw snapshot documents (the full payloads)")
    private List<DiscoverySnapshot> items;

    @Schema(description = "Number of items returned")
    private int totalCount;
}
