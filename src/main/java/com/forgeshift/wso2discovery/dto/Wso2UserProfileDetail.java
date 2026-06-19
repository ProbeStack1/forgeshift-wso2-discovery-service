package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API response projection of one normalized WSO2 SCIM user profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wso2UserProfileDetail {

    private String sourceUserId;
    private String userName;
    private String userEmail;
    private String firstName;
    private String lastName;
    private String displayName;
    private String primaryEmail;
    private List<String> emails;
    private Boolean active;
    private String userType;
    private List<Wso2DiscoveredUserRole> roles;
}
