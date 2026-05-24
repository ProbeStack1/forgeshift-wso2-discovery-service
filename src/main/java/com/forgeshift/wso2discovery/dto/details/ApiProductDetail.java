package com.forgeshift.wso2discovery.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UI-friendly summary of one API Product (bundle of operations from one or
 * more APIs). Populated into {@code DiscoverResourceResponse.apiProductDetails}
 * when {@code type == apiproducts}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiProductDetail {

    @JsonProperty("id")
    @Schema(description = "WSO2 API Product UUID")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Product name", example = "PetStoreSuite")
    private String name;

    @JsonProperty("version")
    @Schema(description = "Product version", example = "1.0.0")
    private String version;

    @JsonProperty("context")
    @Schema(description = "Context path", example = "/petstore-suite/1.0")
    private String context;

    @JsonProperty("provider")
    @Schema(description = "Owner / provider", example = "admin")
    private String provider;

    @JsonProperty("state")
    @Schema(description = "Lifecycle state", example = "PUBLISHED",
            allowableValues = {"CREATED", "PUBLISHED", "DEPRECATED", "RETIRED"})
    private String state;

    @JsonProperty("description")
    @Schema(description = "Free-form description")
    private String description;

    @JsonProperty("apiCount")
    @Schema(description = "Number of constituent APIs (operations come from these)")
    private Integer apiCount;
}
