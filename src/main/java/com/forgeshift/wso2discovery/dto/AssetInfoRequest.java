package com.forgeshift.wso2discovery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Request body for {@code POST /wso2/assetinfo}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetInfoRequest {

    @NotBlank
    @Schema(description = "Multi-tenancy partner id", example = "probestack", requiredMode = Schema.RequiredMode.REQUIRED)
    private String companyName;

    @NotBlank
    @Schema(description = "WSO2 tenant the assets live in", example = "carbon.super", requiredMode = Schema.RequiredMode.REQUIRED)
    private String wso2Tenant;

    @NotEmpty
    @Valid
    @Schema(description = "Business-metadata items to attach to discovered assets",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AssetInfoItem> assetInfoItems;

    /** One business-metadata row attached to an API. Mirrors the Apigee AssetInfoItem. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetInfoItem {

        @NotBlank
        @Schema(description = "Business unit identifier", example = "BU0000001", requiredMode = Schema.RequiredMode.REQUIRED)
        private String businessUnit;

        @NotBlank
        @Schema(description = "Application identifier (the business catalog id, not the WSO2 app id)",
                example = "AID0000001", requiredMode = Schema.RequiredMode.REQUIRED)
        private String appId;

        @NotBlank
        @Schema(description = "Project name", example = "PN000001", requiredMode = Schema.RequiredMode.REQUIRED)
        private String projectName;

        @Schema(description = "Deployable unit identifier", example = "DU0000001")
        private String deployableUnit;

        @NotBlank
        @Schema(description = "WSO2 API name this metadata attaches to",
                example = "PetStore", requiredMode = Schema.RequiredMode.REQUIRED)
        private String apiName;

        @Schema(description = "Optional WSO2 API version")
        private String apiVersion;
    }
}
