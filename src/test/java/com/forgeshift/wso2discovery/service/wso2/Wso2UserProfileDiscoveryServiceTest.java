package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.client.Wso2Credentials;
import com.forgeshift.wso2discovery.client.Wso2UserStoreSoapClient;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.domain.Wso2UserProfileDocument;
import com.forgeshift.wso2discovery.dto.Wso2RolePermissionDetail;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryResponse;
import com.forgeshift.wso2discovery.repository.Wso2UserProfileRepository;
import com.forgeshift.wso2discovery.service.Wso2TenantProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * Unit tests for SOAP-based WSO2 user-profile discovery orchestration.
 */
@ExtendWith(MockitoExtension.class)
class Wso2UserProfileDiscoveryServiceTest {

    @Mock
    private Wso2UserStoreSoapClient soapClient;

    @Mock
    private Wso2TenantProfileService tenantProfileService;

    @Mock
    private Wso2UserProfileRepository repository;

    private Wso2Credentials credentials;
    private Wso2Properties properties;
    private Wso2UserProfileDiscoveryService service;

    @BeforeEach
    void setUp() {
        properties = new Wso2Properties();
        credentials = Wso2Credentials.builder()
                .baseUrl("https://wso2.local:9443")
                .username("admin")
                .password("admin")
                .build();
        service = new Wso2UserProfileDiscoveryService(soapClient, tenantProfileService, properties, repository);
    }

    @Test
    void discoverUserProfiles_fetchesSoapUsersRolesClaimsAndStoresDocuments() {
        properties.getSoap().setIncludeRolePermissions(true);
        Wso2UserProfileDiscoveryRequest request = validRequest();
        when(tenantProfileService.resolve("probestack", "carbon.super")).thenReturn(credentials);
        when(soapClient.listUsers(credentials)).thenReturn(List.of("alice"));
        when(soapClient.getRoleListOfUser(credentials, "alice")).thenReturn(List.of("Internal/admin", "creator"));
        when(soapClient.getUserClaimValues(credentials, "alice")).thenReturn(Map.of(
                "http://wso2.org/claims/emailaddress", "alice@example.com",
                "http://wso2.org/claims/givenname", "Alice",
                "http://wso2.org/claims/lastname", "Smith"));
        when(soapClient.getRolePermissions(credentials, "Internal/admin")).thenReturn(List.of(permission("/permission/admin/login", true)));
        when(soapClient.getRolePermissions(credentials, "creator")).thenReturn(List.of(permission("/permission/admin/manage/api", true)));
        when(repository.upsertAll(any())).thenReturn(List.of("doc-1"));

        Wso2UserProfileDiscoveryResponse response = service.discoverUserProfiles(request);

        assertEquals("COMPLETED", response.getDiscoveryStatus());
        assertEquals("wso2", response.getSourceGateway());
        assertEquals("kong", response.getTargetGateway());
        assertEquals("carbon.super", response.getOrgName());
        assertEquals(1, response.getTotalUsers());
        assertEquals(2, response.getTotalRoles());
        assertEquals("alice@example.com", response.getUsers().get(0).getUserEmail());
        assertEquals("Internal/admin", response.getUsers().get(0).getRoles().get(0).getRoleName());
        assertEquals("/permission/admin/login", response.getUsers().get(0).getRoles().get(0).getPermissions().get(0).getResourcePath());
        assertTrue(response.getUsers().get(0).getRoles().get(0).getPermissions().get(0).getSelected());

        ArgumentCaptor<List<Wso2UserProfileDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertAll(captor.capture());
        Wso2UserProfileDocument document = captor.getValue().get(0);
        assertTrue(document.getId().contains("probestack|carbon.super|user-profiles|alice|tx-123"));
        assertEquals("Alice", document.getFirstName());
        assertEquals("Smith", document.getLastName());
        assertEquals("wso2", document.getSourceGateway());
        assertEquals("kong", document.getTargetGateway());
        assertEquals("/permission/admin/login",
                document.getRolePermissions().get("Internal/admin").get(0).getResourcePath());
    }

    @Test
    void discoverUserProfiles_missingTenantFailsValidation() {
        Wso2UserProfileDiscoveryRequest request = validRequest();
        request.setWso2Tenant("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.discoverUserProfiles(request));

        assertEquals("wso2Tenant is required", exception.getMessage());
    }

    @Test
    void discoverUserProfiles_missingTransactionFailsValidation() {
        Wso2UserProfileDiscoveryRequest request = validRequest();
        request.setRequestTransactionId("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.discoverUserProfiles(request));

        assertEquals("requestTransactionId is required", exception.getMessage());
    }

    @Test
    void discoverUserProfiles_userRoleFailureContinuesWithPartialUser() {
        Wso2UserProfileDiscoveryRequest request = validRequest();
        when(tenantProfileService.resolve("probestack", "carbon.super")).thenReturn(credentials);
        when(soapClient.listUsers(credentials)).thenReturn(List.of("alice"));
        when(soapClient.getRoleListOfUser(credentials, "alice")).thenThrow(new RuntimeException("403"));
        when(soapClient.getUserClaimValues(credentials, "alice")).thenReturn(Map.of(
                "http://wso2.org/claims/emailaddress", "alice@example.com"));
        when(repository.upsertAll(any())).thenReturn(List.of("doc-1"));

        Wso2UserProfileDiscoveryResponse response = service.discoverUserProfiles(request);

        assertEquals(1, response.getTotalUsers());
        assertEquals(0, response.getTotalRoles());
        ArgumentCaptor<List<Wso2UserProfileDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertAll(captor.capture());
        assertTrue(captor.getValue().get(0).getErrorMessage().contains("Role lookup failed"));
    }

    @Test
    void discoverUserProfiles_mongoSaveFailureFailsRequest() {
        Wso2UserProfileDiscoveryRequest request = validRequest();
        when(tenantProfileService.resolve("probestack", "carbon.super")).thenReturn(credentials);
        when(soapClient.listUsers(credentials)).thenReturn(List.of("alice"));
        when(soapClient.getRoleListOfUser(credentials, "alice")).thenReturn(List.of("Internal/admin"));
        when(soapClient.getUserClaimValues(credentials, "alice")).thenReturn(Map.of());
        when(repository.upsertAll(any())).thenThrow(new RuntimeException("mongo down"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.discoverUserProfiles(request));

        assertTrue(exception.getMessage().contains("Failed to store WSO2 user profiles"));
    }

    @Test
    void discoverUserProfiles_rolePermissionFailureContinuesWithEmptyPermissions() {
        properties.getSoap().setIncludeRolePermissions(true);
        Wso2UserProfileDiscoveryRequest request = validRequest();
        when(tenantProfileService.resolve("probestack", "carbon.super")).thenReturn(credentials);
        when(soapClient.listUsers(credentials)).thenReturn(List.of("alice"));
        when(soapClient.getRoleListOfUser(credentials, "alice")).thenReturn(List.of("Internal/admin"));
        when(soapClient.getUserClaimValues(credentials, "alice")).thenReturn(Map.of());
        when(soapClient.getRolePermissions(credentials, "Internal/admin")).thenThrow(new RuntimeException("permission denied"));
        when(repository.upsertAll(any())).thenReturn(List.of("doc-1"));

        Wso2UserProfileDiscoveryResponse response = service.discoverUserProfiles(request);

        assertEquals("Internal/admin", response.getUsers().get(0).getRoles().get(0).getRoleName());
        assertTrue(response.getUsers().get(0).getRoles().get(0).getPermissions().isEmpty());
    }

    @Test
    void discoverUserProfiles_sharedRoleFetchesPermissionsOnce() {
        properties.getSoap().setIncludeRolePermissions(true);
        Wso2UserProfileDiscoveryRequest request = validRequest();
        when(tenantProfileService.resolve("probestack", "carbon.super")).thenReturn(credentials);
        when(soapClient.listUsers(credentials)).thenReturn(List.of("alice", "bob"));
        when(soapClient.getRoleListOfUser(credentials, "alice")).thenReturn(List.of("Internal/admin"));
        when(soapClient.getRoleListOfUser(credentials, "bob")).thenReturn(List.of("Internal/admin"));
        when(soapClient.getUserClaimValues(credentials, "alice")).thenReturn(Map.of());
        when(soapClient.getUserClaimValues(credentials, "bob")).thenReturn(Map.of());
        when(soapClient.getRolePermissions(credentials, "Internal/admin")).thenReturn(List.of(permission("/permission/admin/login", true)));
        when(repository.upsertAll(any())).thenReturn(List.of("doc-1", "doc-2"));

        Wso2UserProfileDiscoveryResponse response = service.discoverUserProfiles(request);

        assertEquals(2, response.getTotalUsers());
        assertEquals(1, response.getTotalRoles());
        verify(soapClient, times(1)).getRolePermissions(credentials, "Internal/admin");
    }

    @Test
    void discoverUserProfiles_rolePermissionsDisabledSkipsPermissionSoapCalls() {
        properties.getSoap().setIncludeRolePermissions(false);
        Wso2UserProfileDiscoveryRequest request = validRequest();
        when(tenantProfileService.resolve("probestack", "carbon.super")).thenReturn(credentials);
        when(soapClient.listUsers(credentials)).thenReturn(List.of("alice"));
        when(soapClient.getRoleListOfUser(credentials, "alice")).thenReturn(List.of("Internal/admin"));
        when(soapClient.getUserClaimValues(credentials, "alice")).thenReturn(Map.of());
        when(repository.upsertAll(any())).thenReturn(List.of("doc-1"));

        Wso2UserProfileDiscoveryResponse response = service.discoverUserProfiles(request);

        assertEquals("Internal/admin", response.getUsers().get(0).getRoles().get(0).getRoleName());
        assertTrue(response.getUsers().get(0).getRoles().get(0).getPermissions().isEmpty());
        verify(soapClient, never()).getRolePermissions(any(), anyString());
    }

    private Wso2UserProfileDiscoveryRequest validRequest() {
        Wso2UserProfileDiscoveryRequest request = new Wso2UserProfileDiscoveryRequest();
        request.setCompanyName("probestack");
        request.setWso2Tenant("carbon.super");
        request.setEnvironment("dev");
        request.setRequestTransactionId("tx-123");
        request.setUserEmail("admin@probestack.io");
        return request;
    }

    private Wso2RolePermissionDetail permission(String resourcePath, boolean selected) {
        return Wso2RolePermissionDetail.builder()
                .resourcePath(resourcePath)
                .selected(selected)
                .build();
    }

}
