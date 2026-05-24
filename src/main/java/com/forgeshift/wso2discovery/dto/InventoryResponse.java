package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.forgeshift.wso2discovery.dto.details.ResourceSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response for {@code POST /wso2/inventory}.
 *
 * The inventory is a list-only pre-flight pass over the source WSO2 tenant.
 * It calls only the LIST endpoints (no per-item enrichment, no persistence)
 * and returns names / ids across every resource type in one response.
 *
 * The envelope mirrors {@link DiscoverResourceResponse} so the UI can render
 * inventory and per-resource discovery results with the same components.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "companyName", "wso2Tenant", "type", "totalCount",
        "requestTransactionId", "timestamp",
        "apiSummaries", "applicationSummaries", "subscriptionSummaries",
        "apiProductSummaries", "throttlingPolicySummaries",
        "keyManagerSummaries", "scopeSummaries", "certificateSummaries",
        "errors", "elapsedMs"
})
public class InventoryResponse {

    // -------- envelope --------

    @JsonProperty("companyName")
    @Schema(description = "Multi-tenancy partner id", example = "probestack")
    private String companyName;

    @JsonProperty("wso2Tenant")
    @Schema(description = "WSO2 tenant inventoried", example = "carbon.super")
    private String wso2Tenant;

    @JsonProperty("type")
    @Schema(description = "Always \"inventory\" for this endpoint", example = "inventory")
    private String type;

    @JsonProperty("totalCount")
    @Schema(description = "Sum of items across all detail lists", example = "27")
    private int totalCount;

    @JsonProperty("requestTransactionId")
    @Schema(description = "Correlation id (caller-supplied or generated)")
    private String requestTransactionId;

    @JsonProperty("timestamp")
    @Schema(description = "Server timestamp at the moment the inventory completed",
            example = "2026-05-21T13:30:00Z")
    private String timestamp;

    // -------- per-resource summary lists --------

    @JsonProperty("apiSummaries")
    @Schema(description = "All APIs in the tenant (Publisher API)")
    private List<ResourceSummary> apiSummaries;

    @JsonProperty("applicationSummaries")
    @Schema(description = "All DevPortal applications")
    private List<ResourceSummary> applicationSummaries;

    @JsonProperty("subscriptionSummaries")
    @Schema(description = "All app↔API subscriptions")
    private List<ResourceSummary> subscriptionSummaries;

    @JsonProperty("apiProductSummaries")
    @Schema(description = "All API products")
    private List<ResourceSummary> apiProductSummaries;

    @JsonProperty("throttlingPolicySummaries")
    @Schema(description = "Subscription + application + advanced throttling policies, merged")
    private List<ResourceSummary> throttlingPolicySummaries;

    @JsonProperty("keyManagerSummaries")
    @Schema(description = "Registered Key Managers")
    private List<ResourceSummary> keyManagerSummaries;

    @JsonProperty("scopeSummaries")
    @Schema(description = "Shared OAuth2 scopes")
    private List<ResourceSummary> scopeSummaries;

    @JsonProperty("certificateSummaries")
    @Schema(description = "Endpoint TLS certificates")
    private List<ResourceSummary> certificateSummaries;

    // -------- diagnostics --------

    @JsonProperty("errors")
    @Schema(description = "Per-resource error messages keyed by the resource slug. " +
            "Entries appear only when a particular list call failed; other resources still return.")
    private Map<String, String> errors;

    @JsonProperty("elapsedMs")
    @Schema(description = "Wall-clock for the whole inventory pass", example = "1450")
    private long elapsedMs;
}
