package com.forgeshift.wso2discovery.client;

import lombok.Builder;
import lombok.Data;

/**
 * Resolved Kong Konnect connection details for one migration request.
 *
 * <p>Mirrors the migration service's {@code KongKonnectCredentials} but keeps
 * only what user migration needs (no git/decK fields).
 */
@Data
@Builder
public class KonnectCredentials {

    /** "profile" when read from kong_konnect_profiles, "static" when from config fallback. */
    private String source;
    private String konnectBaseUrl;
    private String konnectAccessToken;
    private String controlPlaneId;
    private String controlPlaneName;
}
