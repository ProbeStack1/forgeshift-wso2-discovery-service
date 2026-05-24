package com.forgeshift.wso2discovery.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thin item descriptor used by the inventory endpoint.
 *
 * The inventory's job is to answer "what's there?" cheaply, so every resource
 * type projects into the same shape rather than carrying full per-type
 * details. For drill-down to full payloads, callers run the per-resource
 * POST endpoints which write snapshots and return typed details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceSummary {

    @JsonProperty("id")
    @Schema(description = "WSO2 resource identifier (UUID or natural id depending on type)")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Human-readable name")
    private String name;

    @JsonProperty("version")
    @Schema(description = "Version, when applicable (APIs, API Products)")
    private String version;

    @JsonProperty("type")
    @Schema(description = "Sub-type, when applicable (e.g. HTTP for APIs, JWT for Key Managers)")
    private String type;

    @JsonProperty("status")
    @Schema(description = "Lifecycle / enabled flag, when applicable")
    private String status;

    @JsonProperty("extra")
    @Schema(description = "Type-specific extras kept compact for UI rendering")
    private java.util.Map<String, String> extra;
}
