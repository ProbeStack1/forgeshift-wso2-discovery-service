package com.forgeshift.wso2discovery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request entry for one WSO2 role to Kong role/group mapping rule.
 */
@Data
public class Wso2RoleMappingEntryRequest {

    @NotBlank
    private String wso2RoleName;

    @NotBlank
    private String kongRoleName;

    private String scopeType;

    @NotBlank
    private String status;
}
