package com.forgeshift.wso2discovery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mongo document for one WSO2 user-role migration result in Kong.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wso2KongUserMigrationDocument {

    private String id;
    private String migrationId;
    private String companyName;
    private String sourceGateway;
    private String targetGateway;
    private String wso2Tenant;
    private String environment;
    private String requestTransactionId;
    private String kongControlPlane;
    private String userName;
    private String userEmail;
    private String firstName;
    private String lastName;
    private String wso2RoleName;
    private String kongRoleName;
    private String migrationStatus;
    private String assignmentStatus;
    private String errorMessage;
    private String requestedBy;
    private Instant createdDate;
    private Instant updatedDate;
}
