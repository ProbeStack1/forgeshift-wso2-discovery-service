package com.forgeshift.wso2discovery.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Request envelope for migrating WSO2 users and mapped roles to Kong.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Wso2UserMigrationRequest extends DiscoverResourceRequest {

    private String kongControlPlane;

    @Valid
    @NotEmpty
    private List<Wso2UserMigrationUserRequest> users;
}
