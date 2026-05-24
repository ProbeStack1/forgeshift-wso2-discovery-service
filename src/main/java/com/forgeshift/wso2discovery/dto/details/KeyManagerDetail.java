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
 * UI-friendly summary of one Key Manager registered in WSO2.
 *
 * Drives downstream auth-plugin selection in the Kong translator:
 *   - default Resident KM with RS256 tokens  -> Kong jwt plugin
 *   - federated OIDC KM (Keycloak, Okta...)  -> Kong openid-connect plugin
 *
 * Populated into {@code DiscoverResourceResponse.keyManagerDetails} when
 * {@code type == keymanagers}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyManagerDetail {

    @JsonProperty("id")
    @Schema(description = "WSO2 key manager UUID")
    private String id;

    @JsonProperty("name")
    @Schema(description = "Key manager name", example = "Resident Key Manager")
    private String name;

    @JsonProperty("displayName")
    @Schema(description = "Display name")
    private String displayName;

    @JsonProperty("type")
    @Schema(description = "Key manager type", example = "default",
            allowableValues = {"default", "WSO2-IS", "Auth0", "Keycloak", "Okta", "AmazonCognito", "Custom"})
    private String type;

    @JsonProperty("enabled")
    @Schema(description = "Whether the key manager is enabled")
    private Boolean enabled;

    @JsonProperty("description")
    @Schema(description = "Free-form description")
    private String description;

    @JsonProperty("issuer")
    @Schema(description = "Issuer claim that tokens carry")
    private String issuer;

    @JsonProperty("tokenEndpoint")
    @Schema(description = "OAuth2 token endpoint URL")
    private String tokenEndpoint;

    @JsonProperty("introspectionEndpoint")
    @Schema(description = "Introspection endpoint URL")
    private String introspectionEndpoint;

    @JsonProperty("revokeEndpoint")
    @Schema(description = "Token revocation endpoint URL")
    private String revokeEndpoint;

    @JsonProperty("authorizeEndpoint")
    @Schema(description = "Authorize endpoint URL")
    private String authorizeEndpoint;

    @JsonProperty("userInfoEndpoint")
    @Schema(description = "UserInfo endpoint URL")
    private String userInfoEndpoint;

    @JsonProperty("scopeManagementEndpoint")
    @Schema(description = "Scope management endpoint URL")
    private String scopeManagementEndpoint;

    @JsonProperty("jwksEndpoint")
    @Schema(description = "JWKS URI used to validate JWTs", example = "https://wso2/oauth2/jwks")
    private String jwksEndpoint;

    @JsonProperty("availableGrantTypes")
    @Schema(description = "OAuth2 grant types this KM supports")
    private List<String> availableGrantTypes;

    @JsonProperty("tokenType")
    @Schema(description = "Token type", example = "JWT",
            allowableValues = {"JWT", "OAUTH", "DIRECT", "EXCHANGED", "BOTH"})
    private String tokenType;
}
