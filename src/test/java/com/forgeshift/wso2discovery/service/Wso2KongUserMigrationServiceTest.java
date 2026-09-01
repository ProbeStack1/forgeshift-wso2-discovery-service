package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.domain.Wso2KongUserMigrationDocument;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationResponse;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationRoleRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationUserRequest;
import com.forgeshift.wso2discovery.repository.Wso2KongUserMigrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for demo-safe WSO2 to Kong user migration response handling.
 */
@ExtendWith(MockitoExtension.class)
class Wso2KongUserMigrationServiceTest {

    @Mock
    private Wso2KongUserMigrationRepository repository;

    private Wso2KongUserMigrationService service;

    @BeforeEach
    void setUp() {
        service = new Wso2KongUserMigrationService(repository);
    }

    @Test
    void migrateUsers_returnsMeaningfulFailureWhenIdpIsNotConfigured() {
        Wso2UserMigrationRequest request = request(role("Internal/subscriber", "kong-subscriber"));

        Wso2UserMigrationResponse response = service.migrateUsers(request);

        assertEquals("FAILED", response.getOverallStatus());
        assertEquals(1, response.getTotalRequested());
        assertEquals(0, response.getTotalSuccess());
        assertEquals(1, response.getTotalFailed());
        assertEquals("FAILED", response.getResults().get(0).getMigrationStatus());
        assertEquals("FAILED", response.getResults().get(0).getAssignmentStatus());
        assertEquals("Kong user migration is not enabled. IDP is not configured to migrate WSO2 users to Kong.",
                response.getResults().get(0).getErrorMessage());

        ArgumentCaptor<List<Wso2KongUserMigrationDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertAll(captor.capture());
        assertEquals("FAILED", captor.getValue().get(0).getMigrationStatus());
        assertTrue(captor.getValue().get(0).getErrorMessage().contains("IDP is not configured"));
    }

    @Test
    void migrateUsers_returnsInvalidMappingReasonWhenKongRoleIsMissing() {
        Wso2UserMigrationRequest request = request(role("Internal/subscriber", ""));

        Wso2UserMigrationResponse response = service.migrateUsers(request);

        assertEquals("FAILED", response.getOverallStatus());
        assertEquals("Creation failed due to invalid role mapping. Kong role is required for migration.",
                response.getResults().get(0).getErrorMessage());
    }

    private Wso2UserMigrationRequest request(Wso2UserMigrationRoleRequest role) {
        Wso2UserMigrationUserRequest user = new Wso2UserMigrationUserRequest();
        user.setUserName("api.developer");
        user.setUserEmail("sarah.williams@example.com");
        user.setRoles(List.of(role));

        Wso2UserMigrationRequest request = new Wso2UserMigrationRequest();
        request.setCompanyName("probestack");
        request.setWso2Tenant("carbon.super");
        request.setEnvironment("dev");
        request.setRequestTransactionId("tx-123");
        request.setUserEmail("admin@probestack.io");
        request.setKongControlPlane("default");
        request.setUsers(List.of(user));
        return request;
    }

    private Wso2UserMigrationRoleRequest role(String wso2RoleName, String kongRoleName) {
        Wso2UserMigrationRoleRequest role = new Wso2UserMigrationRoleRequest();
        role.setWso2RoleName(wso2RoleName);
        role.setKongRoleName(kongRoleName);
        return role;
    }
}
