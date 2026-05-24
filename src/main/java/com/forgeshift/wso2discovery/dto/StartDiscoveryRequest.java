package com.forgeshift.wso2discovery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Payload for POST /discoveries (bulk fan-out).
 *
 * Spawns one per-resource discovery for every entry in {@code resourceTypes}.
 * Per-resource POST endpoints accept {@link DiscoverResourceRequest} instead.
 */
@Data
public class StartDiscoveryRequest {

    private String companyName;

    @NotBlank
    private String wso2Tenant;

    private String environment;

    private String userEmail;

    /**
     * Resource type slugs to discover (e.g. ["apis","applications"]). If
     * null or empty, all known resource types are attempted.
     */
    private List<String> resourceTypes;

    private String notes;
}
