package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response envelope for normalized WSO2 user-profile discovery.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wso2UserProfileDiscoveryResponse {

    private String companyName;
    private String sourceGateway;
    private String targetGateway;
    private String orgName;
    private String environment;
    private String requestTransactionId;
    private String discoveryStatus;
    private int totalUsers;
    private int totalRoles;
    private List<Wso2UserProfileDetail> users;
}
