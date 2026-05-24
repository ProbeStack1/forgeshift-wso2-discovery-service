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
 * UI-friendly summary of one WSO2 user from the SCIM 2.0 user store.
 *
 * Populated into {@code DiscoverResourceResponse.userDetails} when
 * {@code type == users}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDetail {

    @JsonProperty("id")
    @Schema(description = "SCIM user UUID")
    private String id;

    @JsonProperty("userName")
    @Schema(description = "Login username", example = "admin")
    private String userName;

    @JsonProperty("displayName")
    @Schema(description = "Display name composed from name.givenName + name.familyName")
    private String displayName;

    @JsonProperty("emails")
    @Schema(description = "User email addresses (primary first, then others)")
    private List<String> emails;

    @JsonProperty("active")
    @Schema(description = "Whether the account is enabled")
    private Boolean active;

    @JsonProperty("roles")
    @Schema(description = "Role names the user holds (from SCIM groups / WSO2 roles)")
    private List<String> roles;

    @JsonProperty("userType")
    @Schema(description = "User type, e.g. DEFAULT or FEDERATED")
    private String userType;
}
