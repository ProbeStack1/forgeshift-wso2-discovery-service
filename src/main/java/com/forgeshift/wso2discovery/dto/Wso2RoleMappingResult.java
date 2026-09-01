package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-role result returned by role-mapping upsert operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wso2RoleMappingResult {

    private String mappingId;
    private String wso2RoleName;
    private String kongRoleName;
    private String operationType;
    private String status;
    private String errorMessage;
}
