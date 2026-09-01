package com.forgeshift.wso2discovery.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * One WSO2 user selected for migration to Kong.
 */
@Data
public class Wso2UserMigrationUserRequest {

    @NotBlank
    private String userName;

    private String userEmail;
    private String firstName;
    private String lastName;

    @Valid
    private List<Wso2UserMigrationRoleRequest> roles;
}
