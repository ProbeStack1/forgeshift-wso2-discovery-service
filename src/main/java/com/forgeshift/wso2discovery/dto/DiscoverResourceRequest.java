package com.forgeshift.wso2discovery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Common request body for every {@code POST /wso2/<resource>} endpoint.
 *
 * Mirrors the Apigee DiscoveryResourceRequest shape so callers see one model
 * across both tools.
 */
@Data
public class DiscoverResourceRequest {

    /** Multi-tenancy identifier of the caller. Defaults to "probestack" if omitted. */
    private String companyName;

    /** WSO2 tenant to discover (e.g. "carbon.super"). */
    @NotBlank
    private String wso2Tenant;

    /** Logical environment label for traceability (e.g. "prod", "stage"). Optional. */
    private String environment;

    /** Caller-supplied transaction id; if missing, the service generates one. */
    private String requestTransactionId;

    /** Initiating user email, recorded on the revision counter. */
    private String userEmail;

    /** Free-form note attached to the resulting snapshots. */
    private String notes;
}
