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
    private String wso2Tenant;
    private String environment;
    private String requestTransactionId;
    private String discoveryStatus;
    private int totalUsers;
    private int totalRoles;
    private String collectionName;
    private List<String> documentIds;
    private List<Wso2UserProfileDetail> users;
    private String timestamp;
    private long elapsedMs;
}
