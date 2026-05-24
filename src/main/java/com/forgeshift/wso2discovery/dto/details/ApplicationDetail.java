package com.forgeshift.wso2discovery.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UI-friendly summary of one DevPortal Application.
 *
 * Populated into {@code DiscoverResourceResponse.applicationDetails} when
 * {@code type == applications}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationDetail {

    @JsonProperty("id")
    @Schema(description = "WSO2 application UUID", example = "5d04...")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Application name", example = "DefaultApplication")
    private String name;

    @JsonProperty("owner")
    @Schema(description = "Owner username", example = "admin")
    private String owner;

    @JsonProperty("status")
    @Schema(description = "Application lifecycle status", example = "APPROVED",
            allowableValues = {"CREATED", "APPROVED", "REJECTED", "DELETE_PENDING", "UPDATE_PENDING"})
    private String status;

    @JsonProperty("throttlingPolicy")
    @Schema(description = "Application-level throttling policy name", example = "10PerMin")
    private String throttlingPolicy;

    @JsonProperty("description")
    @Schema(description = "Free-form description")
    private String description;

    @JsonProperty("tokenType")
    @Schema(description = "Token type used by the application", example = "JWT",
            allowableValues = {"JWT", "OAUTH"})
    private String tokenType;

    @JsonProperty("groupId")
    @Schema(description = "Application group id, if any")
    private String groupId;

    @JsonProperty("subscriber")
    @Schema(description = "Subscriber identifier")
    private String subscriber;

    @JsonProperty("subscriptionCount")
    @Schema(description = "Number of API subscriptions held by this application")
    private Integer subscriptionCount;
}
