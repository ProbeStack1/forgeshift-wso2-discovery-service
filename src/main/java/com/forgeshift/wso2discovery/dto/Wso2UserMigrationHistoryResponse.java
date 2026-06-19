package com.forgeshift.wso2discovery.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response envelope for WSO2 to Kong user migration history.
 */
@Data
@Builder
public class Wso2UserMigrationHistoryResponse {

    private String companyName;
    private String wso2Tenant;
    private String environment;
    private int totalRecords;
    private List<Wso2UserMigrationResult> records;
}
