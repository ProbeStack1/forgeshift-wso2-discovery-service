package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** Response for {@code POST /wso2/assetinfo}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssetInfoResponse {

    @Schema(description = "Multi-tenancy partner id")
    private String companyName;

    @Schema(description = "WSO2 tenant")
    private String wso2Tenant;

    @Schema(description = "Mongo collection the rows were upserted into",
            example = "probestack_wso2_asset_info")
    private String collectionName;

    @Schema(description = "Composite ids of the upserted rows")
    private List<String> upsertedIds;

    @Schema(description = "Number of rows accepted")
    private int savedCount;

    @Schema(description = "Server timestamp at acceptance")
    private Instant savedAt;
}
