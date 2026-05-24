package com.forgeshift.wso2discovery.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UI-friendly summary of one Application ↔ API subscription.
 *
 * Populated into {@code DiscoverResourceResponse.subscriptionDetails} when
 * {@code type == subscriptions}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionDetail {

    @JsonProperty("id")
    @Schema(description = "WSO2 subscription UUID")
    private String id;

    @JsonProperty("applicationId")
    @Schema(description = "WSO2 application UUID")
    private String applicationId;

    @JsonProperty("applicationName")
    @Schema(description = "Application name (denormalized from applicationInfo)")
    private String applicationName;

    @JsonProperty("apiId")
    @Schema(description = "WSO2 API UUID")
    private String apiId;

    @JsonProperty("apiName")
    @Schema(description = "API name (denormalized from apiInfo)")
    private String apiName;

    @JsonProperty("apiVersion")
    @Schema(description = "API version (denormalized from apiInfo)")
    private String apiVersion;

    @JsonProperty("apiContext")
    @Schema(description = "API context path (denormalized from apiInfo)")
    private String apiContext;

    @JsonProperty("throttlingPolicy")
    @Schema(description = "Subscription-tier throttling policy", example = "Unlimited")
    private String throttlingPolicy;

    @JsonProperty("status")
    @Schema(description = "Subscription status", example = "UNBLOCKED",
            allowableValues = {"UNBLOCKED", "BLOCKED", "PROD_ONLY_BLOCKED", "ON_HOLD", "REJECTED"})
    private String status;

    @JsonProperty("requestedThrottlingPolicy")
    @Schema(description = "Throttling policy requested at subscription time, before approval")
    private String requestedThrottlingPolicy;
}
