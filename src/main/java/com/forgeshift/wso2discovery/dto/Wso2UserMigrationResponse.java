package com.forgeshift.wso2discovery.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response envelope for WSO2 to Kong user migration execution.
 */
@Data
@Builder
public class Wso2UserMigrationResponse {

    private String requestTransactionId;
    private String companyName;
    private String sourceGateway;
    private String targetGateway;
    private String orgName;
    private String environment;
    private int totalRequested;
    private int totalSuccess;
    private int totalAlreadyExists;
    private int totalFailed;
    private String overallStatus;
    private List<Wso2UserMigrationResult> results;
}
