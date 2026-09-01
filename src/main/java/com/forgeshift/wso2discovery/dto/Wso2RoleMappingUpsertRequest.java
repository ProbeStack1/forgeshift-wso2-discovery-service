package com.forgeshift.wso2discovery.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Request envelope for creating or updating WSO2 to Kong role mappings.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Wso2RoleMappingUpsertRequest extends DiscoverResourceRequest {

    private String kongControlPlane;

    @Valid
    @NotEmpty
    private List<Wso2RoleMappingEntryRequest> roleMappings;
}
