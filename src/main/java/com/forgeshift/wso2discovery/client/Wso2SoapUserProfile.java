package com.forgeshift.wso2discovery.client;

import com.forgeshift.wso2discovery.dto.Wso2RolePermissionDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SOAP-derived WSO2 user profile used internally by the discovery service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wso2SoapUserProfile {

    private String userName;
    private List<String> roles;
    private Map<String, String> claims;
    private Map<String, List<Wso2RolePermissionDetail>> rolePermissions;
    private String errorMessage;
}
