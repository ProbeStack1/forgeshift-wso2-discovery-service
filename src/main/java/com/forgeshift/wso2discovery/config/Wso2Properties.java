package com.forgeshift.wso2discovery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the source WSO2 API Manager.
 *
 * Bound from the {@code forgeshift.wso2.*} properties.
 */
@Data
@ConfigurationProperties(prefix = "forgeshift.wso2")
public class Wso2Properties {

    /** Base URL of the WSO2 APIM management plane (e.g. https://wso2.example.com:9443). */
    private String baseUrl;

    /** Tenant admin username for password-grant token acquisition. */
    private String username;

    private String password;

    /** OAuth2 client_id of a registered service application with publisher scopes. */
    private String clientId;

    private String clientSecret;

    /** Disable TLS hostname / cert verification. Acceptable for dev only. */
    private boolean trustSelfSigned;

    private String publisherApiBase = "/api/am/publisher/v4";
    private String adminApiBase = "/api/am/admin/v4";
    private String devportalApiBase = "/api/am/devportal/v3";
    private String scimApiBase = "/scim2";
    private String tokenPath = "/oauth2/token";

    /** OAuth2 scope requested when calling the Publisher REST API. */
    private String publisherScope = "apim:api_view";

    /** OAuth2 scope requested when calling the Admin REST API. */
    private String adminScope = "apim:admin";

    /** OAuth2 scope requested when calling the DevPortal REST API. */
    private String devportalScope = "apim:subscribe";

    /** Page size used when listing resources from WSO2. */
    private int pageSize = 50;

    private int requestTimeoutSeconds = 30;

    /** In-memory token cache TTL. Default 55min — matches WSO2's default 1hr token lifetime with a 5min safety margin. */
    private int tokenCacheTtlSeconds = 3300;

    /** Cap on cached entries. Hit it = oldest entry evicted. */
    private int tokenCacheMaxSize = 1000;

    /**
     * Space-separated combination of publisher, admin and devportal scopes,
     * used by the inventory endpoint to acquire one token that can hit every
     * list endpoint. WSO2's OAuth grants the intersection of requested scopes
     * and what the authenticating user is permitted to hold.
     */
    public String inventoryScope() {
        StringBuilder sb = new StringBuilder();
        if (publisherScope != null && !publisherScope.isBlank()) sb.append(publisherScope);
        if (adminScope != null && !adminScope.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(adminScope);
        }
        if (devportalScope != null && !devportalScope.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(devportalScope);
        }
        return sb.toString();
    }
}
