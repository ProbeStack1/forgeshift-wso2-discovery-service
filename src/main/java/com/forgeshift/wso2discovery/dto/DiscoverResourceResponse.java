package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.forgeshift.wso2discovery.dto.details.ApiDetail;
import com.forgeshift.wso2discovery.dto.details.ApiProductDetail;
import com.forgeshift.wso2discovery.dto.details.ApplicationDetail;
import com.forgeshift.wso2discovery.dto.details.CertificateDetail;
import com.forgeshift.wso2discovery.dto.details.KeyManagerDetail;
import com.forgeshift.wso2discovery.dto.details.MediationPolicyDetail;
import com.forgeshift.wso2discovery.dto.details.ScopeDetail;
import com.forgeshift.wso2discovery.dto.details.SubscriptionDetail;
import com.forgeshift.wso2discovery.dto.details.ThrottlingPolicyDetail;
import com.forgeshift.wso2discovery.dto.details.UserDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Standardised response for every {@code POST /wso2/<resource>} endpoint.
 *
 * Mirrors the shape of the Apigee discovery service's
 * {@code DiscoveryResourceResponse}: an envelope of identifying fields plus
 * one typed detail list populated based on the resource being discovered.
 * Only the relevant detail field is populated; all other detail fields are
 * omitted from the JSON via {@link JsonInclude.Include#NON_NULL}.
 *
 * Example response for {@code POST /wso2/apis}:
 * <pre>
 * {
 *   "companyName": "probestack",
 *   "wso2Tenant": "carbon.super",
 *   "environment": null,
 *   "type": "apis",
 *   "totalCount": 1,
 *   "requestTransactionId": "32abcc62-...",
 *   "revision": 1,
 *   "timestamp": "2026-05-21T12:07:58Z",
 *   "apiDetails": [
 *     { "id": "47d0...", "name": "PetStore", "version": "1.0.0", "context": "/petstore/1.0.0",
 *       "lifecycleStatus": "PUBLISHED", "provider": "admin", "type": "HTTP",
 *       "transports": ["http","https"], "tags": ["pet"], "description": "..." }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "companyName", "wso2Tenant", "environment", "type", "totalCount",
        "requestTransactionId", "revision", "timestamp",
        "apiDetails", "applicationDetails", "subscriptionDetails",
        "throttlingPolicyDetails", "keyManagerDetails", "apiProductDetails",
        "scopeDetails", "certificateDetails", "mediationPolicyDetails", "userDetails",
        "collectionName", "snapshotIds", "elapsedMs"
})
public class DiscoverResourceResponse {

    // -------- envelope --------

    @JsonProperty("companyName")
    @Schema(description = "Multi-tenancy partner id", example = "probestack")
    private String companyName;

    @JsonProperty("wso2Tenant")
    @Schema(description = "WSO2 tenant being discovered", example = "carbon.super")
    private String wso2Tenant;

    @JsonProperty("environment")
    @Schema(description = "WSO2 gateway environment, when applicable", example = "Production and Sandbox", nullable = true)
    private String environment;

    /**
     * Resource type slug (lowercase plural). Serialised as JSON key {@code type}
     * to align with the Apigee discovery response.
     */
    @JsonProperty("type")
    @Schema(description = "Resource type slug", example = "apis",
            allowableValues = {"apis", "applications", "subscriptions", "throttlingpolicies",
                    "keymanagers", "apiproducts", "scopes", "certificates", "mediationpolicies", "users"})
    private String resourceType;

    @JsonProperty("totalCount")
    @Schema(description = "Number of items discovered in this run", example = "17")
    private int totalCount;

    /**
     * Caller-supplied or service-generated correlation id. Serialised as
     * {@code requestTransactionId} to match the Apigee response.
     */
    @JsonProperty("requestTransactionId")
    @Schema(description = "Correlation id for this discovery", example = "32abcc62-af99-41ae-bf37-bbb5e26952fb")
    private String discoveryId;

    @JsonProperty("revision")
    @Schema(description = "Monotonic revision number allocated to this discovery", example = "3")
    private Integer revision;

    @JsonProperty("timestamp")
    @Schema(description = "Server timestamp at the moment the discovery completed", example = "2026-05-21T12:07:58Z")
    private String timestamp;

    // -------- typed detail lists (one populated per resource type) --------

    @JsonProperty("apiDetails")
    @Schema(description = "Populated when type == apis")
    private List<ApiDetail> apiDetails;

    @JsonProperty("applicationDetails")
    @Schema(description = "Populated when type == applications")
    private List<ApplicationDetail> applicationDetails;

    @JsonProperty("subscriptionDetails")
    @Schema(description = "Populated when type == subscriptions")
    private List<SubscriptionDetail> subscriptionDetails;

    @JsonProperty("throttlingPolicyDetails")
    @Schema(description = "Populated when type == throttlingpolicies. Includes all three tier families (subscription, application, advanced).")
    private List<ThrottlingPolicyDetail> throttlingPolicyDetails;

    @JsonProperty("keyManagerDetails")
    @Schema(description = "Populated when type == keymanagers")
    private List<KeyManagerDetail> keyManagerDetails;

    @JsonProperty("apiProductDetails")
    @Schema(description = "Populated when type == apiproducts")
    private List<ApiProductDetail> apiProductDetails;

    @JsonProperty("scopeDetails")
    @Schema(description = "Populated when type == scopes")
    private List<ScopeDetail> scopeDetails;

    @JsonProperty("certificateDetails")
    @Schema(description = "Populated when type == certificates")
    private List<CertificateDetail> certificateDetails;

    @JsonProperty("mediationPolicyDetails")
    @Schema(description = "Populated when type == mediationpolicies. Merges global (tenant-wide) and per-API policies, tagged with scope.")
    private List<MediationPolicyDetail> mediationPolicyDetails;

    @JsonProperty("userDetails")
    @Schema(description = "Populated when type == users. Sourced from /scim2/Users.")
    private List<UserDetail> userDetails;

    // -------- auxiliary (debugging / drill-down) --------

    @JsonProperty("collectionName")
    @Schema(description = "MongoDB collection holding the snapshot documents", example = "discovery_wso2_apis")
    private String collectionName;

    @JsonProperty("snapshotIds")
    @Schema(description = "Composite document ids written for this discovery, for drill-down")
    private List<String> snapshotIds;

    @JsonProperty("elapsedMs")
    @Schema(description = "Server-side wall-clock for the discovery", example = "3483")
    private long elapsedMs;
}
