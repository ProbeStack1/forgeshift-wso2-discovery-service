package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response for {@code POST /internal/wso2/token/test}.
 *
 * Reports whether a token was acquired without leaking the token value
 * itself. The {@code tokenAuthType} field tells the caller whether the
 * returned token would be sent as Bearer (JWT) or via Basic (if a future
 * Basic-auth fallback is added).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenTestResponse {

    @Schema(description = "Service-side UUID for this diagnostic call")
    private String serviceTransactionId;

    @Schema(description = "Echo of the caller's correlation id, when provided")
    private String requestTransactionId;

    @Schema(description = "Whether a token was successfully acquired")
    private boolean success;

    @Schema(description = "How the token would be sent in subsequent calls",
            allowableValues = {"BEARER", "BASIC", "NONE"})
    private String tokenAuthType;

    @Schema(description = "OAuth2 scope that was requested")
    private String scopeRequested;

    @Schema(description = "WSO2 tenant tested against")
    private String wso2Tenant;

    @Schema(description = "Multi-tenancy partner id used")
    private String companyName;

    @Schema(description = "Server timestamp at acquisition")
    private Instant tokenAcquiredAt;

    @Schema(description = "Wall-clock latency for the WSO2 /oauth2/token call")
    private long elapsedMs;

    @Schema(description = "First 6 characters of the token for visual confirmation only - never the full value",
            example = "eyJ4NX...")
    private String tokenPrefix;

    @Schema(description = "When success == false, the human-readable cause")
    private String errorMessage;
}
