package com.forgeshift.wso2discovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per user-role result returned by the Kong migration API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wso2UserMigrationResult {

    private String userName;
    private String userEmail;
    private String wso2RoleName;
    private String kongRoleName;
    private String migrationStatus;
    private String assignmentStatus;
    private String errorMessage;
}
