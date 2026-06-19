package com.forgeshift.wso2discovery.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Request envelope for resolving discovered WSO2 roles to Kong mappings.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Wso2RoleMappingResolveRequest extends DiscoverResourceRequest {

    private String kongControlPlane;

    @NotEmpty
    private List<String> wso2Roles;
}
