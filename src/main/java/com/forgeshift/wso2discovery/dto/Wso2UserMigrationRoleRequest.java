package com.forgeshift.wso2discovery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * One role assignment requested for a user migration to Kong.
 */
@Data
public class Wso2UserMigrationRoleRequest {

    @NotBlank
    private String wso2RoleName;

    @NotBlank
    private String kongRoleName;
}
