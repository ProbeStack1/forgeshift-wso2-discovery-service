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

    /** Source gateway identifier for cross-service tracking, for example "wso2". */
    private String sourceGateway;

    /** Target gateway identifier for cross-service tracking, for example "kong-konnect". */
    private String targetGateway;

    /** WSO2 tenant to discover (e.g. "carbon.super"). */
    @NotBlank
    private String wso2Tenant;

    /** Logical environment label for traceability (e.g. "prod", "stage"). Optional. */
    private String environment;

    /**
     * Caller-supplied discovery transaction id. Must be supplied by the UI —
     * the backend will not generate one. Persists on every snapshot written
     * by this run and is what the history / details endpoints use to find it.
     */
    @NotBlank
    private String requestTransactionId;

    /** Initiating user email, recorded on the revision counter. */
    private String userEmail;

    /** Free-form note attached to the resulting snapshots. */
    private String notes;
}
