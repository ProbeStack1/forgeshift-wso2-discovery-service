package com.forgeshift.wso2discovery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for {@code POST /internal/wso2/token/test}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenTestRequest {

    @Schema(description = "Multi-tenancy partner id. Defaults to the configured default if omitted.",
            example = "probestack")
    private String companyName;

    @NotBlank
    @Schema(description = "WSO2 tenant identifier",
            example = "carbon.super", requiredMode = Schema.RequiredMode.REQUIRED)
    private String wso2Tenant;

    @Schema(description = "OAuth2 scope to request. Special value 'inventory' uses the combined publisher+admin+devportal scopes.",
            example = "apim:api_view",
            allowableValues = {"apim:api_view", "apim:admin", "apim:subscribe", "inventory"})
    private String scope;

    @Schema(description = "Optional caller-supplied correlation id. A service-side UUID is generated when absent.")
    private String requestTransactionId;

    @Schema(description = "If true, drop any cached token for this (companyName, tenant, scope) before acquiring. " +
            "Use to force a fresh WSO2 round-trip when validating that new credentials work.",
            defaultValue = "false")
    private boolean invalidateCacheFirst;
}
