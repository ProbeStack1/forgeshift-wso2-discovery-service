package com.forgeshift.wso2discovery.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * UI-friendly summary of one Throttling Policy.
 *
 * WSO2 has three tier families: {@code subscription}, {@code application}, and
 * {@code advanced}. The discovery service merges all three into one snapshot
 * stream tagged with {@link #getPolicyType()} so the UI can filter by tier
 * family in the same view.
 *
 * Populated into {@code DiscoverResourceResponse.throttlingPolicyDetails}
 * when {@code type == throttlingpolicies}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThrottlingPolicyDetail {

    @JsonProperty("id")
    @Schema(description = "WSO2 policy id")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Policy name", example = "10PerMin")
    private String name;

    @JsonProperty("displayName")
    @Schema(description = "Display name shown in the UI")
    private String displayName;

    @JsonProperty("description")
    @Schema(description = "Free-form description")
    private String description;

    @JsonProperty("policyType")
    @Schema(description = "Tier family the policy belongs to",
            allowableValues = {"subscription", "application", "advanced"})
    private String policyType;

    @JsonProperty("requestCount")
    @Schema(description = "Allowed request count per unit time (when type==requestCount)")
    private Long requestCount;

    @JsonProperty("dataAmount")
    @Schema(description = "Allowed data amount per unit time (when type==bandwidth)")
    private Long dataAmount;

    @JsonProperty("dataUnit")
    @Schema(description = "Data unit: KB, MB, GB",
            allowableValues = {"KB", "MB", "GB"})
    private String dataUnit;

    @JsonProperty("timeUnit")
    @Schema(description = "Time window for the limit", example = "min",
            allowableValues = {"sec", "min", "hour", "day", "month", "year"})
    private String timeUnit;

    @JsonProperty("unitTime")
    @Schema(description = "Multiplier on timeUnit", example = "1")
    private Integer unitTime;

    @JsonProperty("stopOnQuotaReach")
    @Schema(description = "If true, hard-stops requests once the quota is hit")
    private Boolean stopOnQuotaReach;

    @JsonProperty("billingPlan")
    @Schema(description = "Free or commercial", allowableValues = {"FREE", "COMMERCIAL"})
    private String billingPlan;

    @JsonProperty("isDeployed")
    @Schema(description = "Whether the policy is currently deployed to the gateways")
    private Boolean isDeployed;

    @JsonProperty("subscriberCount")
    @Schema(description = "Subscriber count, when present")
    private Integer subscriberCount;

    @JsonProperty("customAttributes")
    @Schema(description = "Custom attributes attached to the policy")
    private Map<String, String> customAttributes;
}
