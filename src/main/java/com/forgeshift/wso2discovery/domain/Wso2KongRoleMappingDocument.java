package com.forgeshift.wso2discovery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mongo document for one WSO2 role to Kong role/group mapping rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wso2KongRoleMappingDocument {

    private String id;
    private String mappingId;
    private String companyName;
    private String sourceGateway;
    private String targetGateway;
    private String wso2Tenant;
    private String environment;
    private String kongControlPlane;
    private String wso2RoleName;
    private String wso2RoleNameNormalized;
    private String kongRoleName;
    private String scopeType;
    private String status;
    private String createdBy;
    private Instant createdDate;
    private String updatedBy;
    private Instant updatedDate;
}
