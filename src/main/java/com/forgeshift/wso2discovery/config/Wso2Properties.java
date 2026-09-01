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
    private String scimUsersPath = "/Users";
    private String tokenPath = "/oauth2/token";
    private Soap soap = new Soap();
    private Kong kong = new Kong();

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

    /**
     * SOAP admin-service settings used for WSO2 user and role discovery.
     */
    @Data
    public static class Soap {
        private String userStoreServicePath = "/services/RemoteUserStoreManagerService";
        private String userAdminServicePath = "/services/UserAdmin";
        private String namespace = "http://service.ws.um.carbon.wso2.org";
        private String userAdminNamespace = "http://org.apache.axis2/xsd";
        private String userFilter = "*";
        private int maxUsers = 100;
        private String profileName = "default";
        private String emailClaim = "http://wso2.org/claims/emailaddress";
        private String firstNameClaim = "http://wso2.org/claims/givenname";
        private String lastNameClaim = "http://wso2.org/claims/lastname";
        private boolean includeRolePermissions = false;
        private int userDiscoveryParallelism = 10;
        private int rolePermissionParallelism = 5;
        private int maxRolePermissionsPerRole = 10;
    }

    /**
     * Kong Konnect settings used by the WSO2-to-Kong user migration endpoint.
     *
     * <p>Credentials normally come from the {@code kong_konnect_profiles}
     * collection written by the profile-config service (same source the
     * migration service reads). The {@code *Fallback} values below are only
     * used when no profile matches the requested company/profile.
     *
     * <p>Note this targets <b>Konnect</b>, not a self-managed Kong Gateway:
     * auth is {@code Authorization: Bearer <PAT>} and entity paths live under
     * {@code /v2/control-planes/{cpId}/core-entities}.
     */
    @Data
    public static class Kong {
        /** Konnect profile collection written by the profile-config service. */
        private String profilesCollection = "kong_konnect_profiles";
        /** Used only when no Konnect profile matches. */
        private String baseUrlFallback = "https://us.api.konghq.com";
        private String accessTokenFallback;
        private String controlPlaneIdFallback;
        private String consumersPath = "/consumers";
        /** {@code {consumer}} is replaced with the consumer id or username. */
        private String aclPathTemplate = "/consumers/{consumer}/acls";
        private int requestTimeoutSeconds = 30;
        /** Stamped on every consumer this service creates, matching migration-service convention. */
        private String migratedByTag = "migrated-by:forgeshift-wso2-migrator";
        /** Prefix for the tag recording which WSO2 user a consumer came from. */
        private String sourceUserTagPrefix = "wso2-source-user";
        /** Prefix for the tag recording the WSO2 tenant a consumer came from. */
        private String tenantTagPrefix = "wso2-tenant";
        /**
         * Marks a consumer as a person rather than an application. A control
         * plane holds one flat consumer list, so without this nothing tells the
         * two apart once they share it.
         */
        private String principalTypeTag = "principal-type:user";
        /**
         * Leading segment of a migrated user's consumer username, keeping users
         * out of the namespace the migration service uses for WSO2
         * applications. Set blank to drop the segment.
         *
         * <p>Applications are deliberately <b>not</b> renamed to match: they are
         * already in Kong, and renaming them would make decK treat them as new
         * entities and prune the originals. The decK cutover, which rebuilds
         * those entities anyway, is the moment to converge the two schemes.
         */
        private String consumerUsernamePrefix = "user";
    }
}
