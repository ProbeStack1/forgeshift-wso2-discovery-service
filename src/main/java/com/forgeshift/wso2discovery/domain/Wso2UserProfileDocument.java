package com.forgeshift.wso2discovery.domain;

import com.forgeshift.wso2discovery.dto.Wso2RolePermissionDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Normalized WSO2 SCIM user profile stored for downstream user migration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wso2UserProfileDocument {

    private String id;
    private String companyName;
    private String sourceGateway;
    private String targetGateway;
    private String wso2Tenant;
    private String environment;
    private String requestTransactionId;
    private String sourceUserId;
    private String userName;
    private String firstName;
    private String lastName;
    private String displayName;
    private String primaryEmail;
    private List<String> emails;
    private Boolean active;
    private String userType;
    private List<String> roles;
    private Map<String, List<Wso2RolePermissionDetail>> rolePermissions;
    private String errorMessage;
    private Map<String, Object> rawPayload;
    private String requestedBy;
    private Instant createdDate;
    private Instant updatedDate;
}
