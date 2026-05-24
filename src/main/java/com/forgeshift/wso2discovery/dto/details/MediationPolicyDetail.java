package com.forgeshift.wso2discovery.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UI-friendly summary of one Mediation Policy.
 *
 * Mediation policies in WSO2 4.x live in two scopes:
 *   - {@code global}: shared across APIs, available via
 *     /api/am/publisher/v4/mediation-policies (deprecated in some builds)
 *   - {@code api}: bound to one API at /apis/{apiId}/mediation-policies
 *
 * The discovery service iterates every API for per-API policies; future work
 * will also try the global endpoint when it exists.
 *
 * Populated into {@code DiscoverResourceResponse.mediationPolicyDetails} when
 * {@code type == mediationpolicies}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediationPolicyDetail {

    @JsonProperty("id")
    @Schema(description = "WSO2 mediation policy id")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Policy name", example = "log-request")
    private String name;

    @JsonProperty("type")
    @Schema(description = "Mediation flow this policy attaches to",
            allowableValues = {"in", "out", "fault"})
    private String type;

    @JsonProperty("scope")
    @Schema(description = "global = tenant-wide, api = bound to one API",
            allowableValues = {"global", "api"})
    private String scope;

    @JsonProperty("apiId")
    @Schema(description = "Owning API id when scope == api", nullable = true)
    private String apiId;

    @JsonProperty("apiName")
    @Schema(description = "Owning API name when scope == api", nullable = true)
    private String apiName;

    @JsonProperty("apiVersion")
    @Schema(description = "Owning API version when scope == api", nullable = true)
    private String apiVersion;

    @JsonProperty("shared")
    @Schema(description = "Whether the policy is shared across APIs")
    private Boolean shared;

    @JsonProperty("contentType")
    @Schema(description = "Media type of the policy body, typically application/xml")
    private String contentType;
}
