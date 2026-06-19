package com.forgeshift.wso2discovery.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response envelope for resolving WSO2 roles against Kong mappings.
 */
@Data
@Builder
public class Wso2RoleMappingResolveResponse {

    private String requestTransactionId;
    private String companyName;
    private String sourceGateway;
    private String targetGateway;
    private String orgName;
    private String environment;
    private int totalRequestedRoles;
    private int mappedRoles;
    private int unmappedRoles;
    private int inactiveMappings;
    private List<Wso2ResolvedRoleMapping> roles;
}
