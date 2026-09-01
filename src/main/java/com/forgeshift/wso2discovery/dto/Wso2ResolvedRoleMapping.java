package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response item for one WSO2 role mapping resolution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wso2ResolvedRoleMapping {

    private String mappingId;
    private String wso2RoleName;
    private String kongRoleName;
    private String scopeType;
    private String mappingStatus;
}
