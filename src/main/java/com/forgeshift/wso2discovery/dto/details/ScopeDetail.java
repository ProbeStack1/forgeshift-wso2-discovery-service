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
 * UI-friendly summary of one shared OAuth2 Scope.
 *
 * Drives Kong authorization translation: scopes bind to roles in WSO2 and to
 * consumer groups / ACL lists in Kong. Populated into
 * {@code DiscoverResourceResponse.scopeDetails} when {@code type == scopes}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScopeDetail {

    @JsonProperty("id")
    @Schema(description = "WSO2 scope UUID")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Scope name (the value that appears in OAuth2 tokens)",
            example = "petstore:read")
    private String name;

    @JsonProperty("displayName")
    @Schema(description = "Human-readable name")
    private String displayName;

    @JsonProperty("description")
    @Schema(description = "Free-form description")
    private String description;

    @JsonProperty("bindings")
    @Schema(description = "Roles this scope is bound to")
    private List<String> bindings;

    @JsonProperty("usageCount")
    @Schema(description = "Number of API resources that demand this scope")
    private Integer usageCount;
}
