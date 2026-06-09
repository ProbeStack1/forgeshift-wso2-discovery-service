package com.forgeshift.wso2discovery.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * UI-friendly summary of one discovered WSO2 API. Populated into
 * {@code DiscoverResourceResponse.apiDetails} so a caller can render the
 * inventory in one round-trip without dereferencing snapshot ids.
 *
 * Mirrors the role of Apigee's {@code ProxyDetail}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDetail {

    @JsonProperty("id")
    @Schema(description = "WSO2 API UUID", example = "47d0454c-977b-4ca6-ac1a-5d55b16d01bd")
    private String id;

    @JsonProperty("name")
    @Schema(description = "API name", example = "PetStoreAPI")
    private String name;

    @JsonProperty("version")
    @Schema(description = "API version", example = "1.0.0")
    private String version;

    @JsonProperty("context")
    @Schema(description = "API URL context path", example = "/petstore/1.0.0")
    private String context;

    @JsonProperty("lifecycleStatus")
    @Schema(description = "WSO2 lifecycle state", example = "PUBLISHED",
            allowableValues = {"CREATED", "PUBLISHED", "PROTOTYPED", "DEPRECATED", "RETIRED", "BLOCKED"})
    private String lifecycleStatus;

    @JsonProperty("provider")
    @Schema(description = "Owner / provider", example = "admin")
    private String provider;

    @JsonProperty("type")
    @Schema(description = "API protocol type", example = "HTTP",
            allowableValues = {"HTTP", "SOAP", "SOAPTOREST", "GRAPHQL", "WS", "WEBSUB", "SSE", "ASYNC"})
    private String type;

    @JsonProperty("transports")
    @Schema(description = "Allowed transports", example = "[\"http\",\"https\"]")
    private List<String> transports;

    @JsonProperty("tags")
    @Schema(description = "Tags attached to the API")
    private List<String> tags;

    @JsonProperty("description")
    @Schema(description = "Free-form description")
    private String description;

    @JsonProperty("lastChangedAt")
    @Schema(description = "When the API was last updated in WSO2 (source lastUpdatedTime, "
            + "falling back to the list summary's updatedTime). Pass-through of the WSO2 timestamp string.",
            example = "2026-05-20T10:15:30Z")
    private String lastChangedAt;

    @JsonProperty("lastChangedBy")
    @Schema(description = "WSO2 user who last updated the API (source updatedBy). May be null, or a system "
            + "user such as wso2.system.user, when WSO2 did not record an interactive editor.",
            example = "admin")
    private String lastChangedBy;
}
