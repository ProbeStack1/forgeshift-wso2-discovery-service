package com.forgeshift.wso2discovery.client;

import lombok.Builder;
import lombok.Data;

/**
 * Resolved WSO2 connection credentials for one acquireToken call.
 *
 * Either built from a row in the {@code profiles} collection (multi-tenancy
 * path) or composed from the static {@code forgeshift.wso2.*} config
 * (single-tenant path). Always populated before any call leaves Wso2Client.
 */
@Data
@Builder
public class Wso2Credentials {

    /** "profile" or "static" — for diagnostic logging only. */
    private String source;

    private String baseUrl;
    private String username;
    private String password;
    private String clientId;
    private String clientSecret;
    private boolean trustSelfSigned;
}
