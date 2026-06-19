package com.forgeshift.wso2discovery.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response envelope for bulk WSO2 to Kong role-mapping upserts.
 */
@Data
@Builder
public class Wso2RoleMappingUpsertResponse {

    private String requestTransactionId;
    private String companyName;
    private String sourceGateway;
    private String targetGateway;
    private String orgName;
    private String environment;
    private int totalRequested;
    private int totalCreated;
    private int totalUpdated;
    private int totalFailed;
    private String overallStatus;
    private List<Wso2RoleMappingResult> results;
}
