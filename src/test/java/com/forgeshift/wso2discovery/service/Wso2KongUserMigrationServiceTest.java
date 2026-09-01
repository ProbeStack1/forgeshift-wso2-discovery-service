package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.client.KongAdminClient;
import com.forgeshift.wso2discovery.client.KonnectCredentials;
import com.forgeshift.wso2discovery.domain.Wso2KongUserMigrationDocument;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationResponse;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationResult;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationRoleRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserMigrationUserRequest;
import com.forgeshift.wso2discovery.repository.Wso2KongUserMigrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WSO2 to Kong user migration against the Konnect management API.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Wso2KongUserMigrationServiceTest {

    @Mock
    private Wso2KongUserMigrationRepository repository;

    @Mock
    private KongAdminClient kongAdminClient;

    @Mock
    private KonnectProfileReader konnectProfileReader;

    private Wso2KongUserMigrationService service;

    private static final KonnectCredentials CREDENTIALS = KonnectCredentials.builder()
            .source("profile")
            .konnectBaseUrl("https://us.api.konghq.com")
            .konnectAccessToken("kpat_test")
            .controlPlaneId("cp-1234")
            .build();

    @BeforeEach
    void setUp() {
        service = new Wso2KongUserMigrationService(repository, kongAdminClient, konnectProfileReader);
        when(konnectProfileReader.resolve(any(), any(), any())).thenReturn(CREDENTIALS);
    }

    @Test
    void migrateUsers_createsConsumerAndAssignsGroup() {
        stubConsumer(KongAdminClient.WriteOutcome.CREATED, "consumer-uuid");
        when(kongAdminClient.assignGroup(any(), any(), any()))
                .thenReturn(KongAdminClient.WriteOutcome.CREATED);

        Wso2UserMigrationResponse response =
                service.migrateUsers(request(role("Internal/subscriber", "kong-subscriber")));

        assertEquals("SUCCESS", response.getOverallStatus());
        assertEquals(1, response.getTotalRequested());
        assertEquals(1, response.getTotalSuccess());
        assertEquals(0, response.getTotalFailed());

        Wso2UserMigrationResult result = response.getResults().get(0);
        assertEquals("SUCCESS", result.getMigrationStatus());
        assertEquals("SUCCESS", result.getAssignmentStatus());
        assertNull(result.getErrorMessage());

        // The ACL must be attached to the uuid Kong returned, not the username.
        verify(kongAdminClient).assignGroup(eq(CREDENTIALS), eq("consumer-uuid"), eq("kong-subscriber"));
    }

    @Test
    void migrateUsers_reportsAlreadyExistsWhenNothingChanged() {
        stubConsumer(KongAdminClient.WriteOutcome.ALREADY_EXISTS, "consumer-uuid");
        when(kongAdminClient.assignGroup(any(), any(), any()))
                .thenReturn(KongAdminClient.WriteOutcome.ALREADY_EXISTS);

        Wso2UserMigrationResponse response =
                service.migrateUsers(request(role("Internal/subscriber", "kong-subscriber")));

        assertEquals("SUCCESS", response.getOverallStatus());
        assertEquals(1, response.getTotalAlreadyExists());
        assertEquals(0, response.getTotalSuccess());
    }

    @Test
    void migrateUsers_countsExistingConsumerWithNewGroupAsSuccess() {
        stubConsumer(KongAdminClient.WriteOutcome.ALREADY_EXISTS, "consumer-uuid");
        when(kongAdminClient.assignGroup(any(), any(), any()))
                .thenReturn(KongAdminClient.WriteOutcome.CREATED);

        Wso2UserMigrationResponse response =
                service.migrateUsers(request(role("Internal/subscriber", "kong-subscriber")));

        // Real work happened, so this is not a no-op row.
        assertEquals(1, response.getTotalSuccess());
        assertEquals(0, response.getTotalAlreadyExists());
    }

    @Test
    void migrateUsers_createsConsumerOncePerUserNotPerRole() {
        stubConsumer(KongAdminClient.WriteOutcome.CREATED, "consumer-uuid");
        when(kongAdminClient.assignGroup(any(), any(), any()))
                .thenReturn(KongAdminClient.WriteOutcome.CREATED);

        Wso2UserMigrationResponse response = service.migrateUsers(request(
                role("Internal/subscriber", "kong-subscriber"),
                role("Internal/publisher", "kong-publisher")));

        assertEquals(2, response.getTotalRequested());
        verify(kongAdminClient, org.mockito.Mockito.times(1))
                .ensureConsumer(any(), eq("api.developer"), any());
        verify(kongAdminClient, org.mockito.Mockito.times(2)).assignGroup(any(), any(), any());
    }

    @Test
    void migrateUsers_failsEveryRowWhenConsumerCreationFails() {
        when(kongAdminClient.ensureConsumer(any(), any(), any()))
                .thenThrow(WebClientResponseException.create(
                        HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null,
                        "{\"message\":\"Unauthorized\"}".getBytes(), null));

        Wso2UserMigrationResponse response = service.migrateUsers(request(
                role("Internal/subscriber", "kong-subscriber"),
                role("Internal/publisher", "kong-publisher")));

        assertEquals("FAILED", response.getOverallStatus());
        assertEquals(2, response.getTotalFailed());
        // No ACL is attempted once the consumer could not be created.
        verify(kongAdminClient, never()).assignGroup(any(), any(), any());

        String error = response.getResults().get(0).getErrorMessage();
        assertTrue(error.contains("Kong consumer creation failed"), error);
        assertTrue(error.contains("401"), error);
    }

    @Test
    void migrateUsers_marksOnlyTheFailingRoleAsFailed() {
        stubConsumer(KongAdminClient.WriteOutcome.CREATED, "consumer-uuid");
        when(kongAdminClient.assignGroup(any(), any(), eq("kong-subscriber")))
                .thenReturn(KongAdminClient.WriteOutcome.CREATED);
        when(kongAdminClient.assignGroup(any(), any(), eq("kong-publisher")))
                .thenThrow(WebClientResponseException.create(
                        HttpStatus.NOT_FOUND.value(), "Not Found", null,
                        "{\"message\":\"group not found\"}".getBytes(), null));

        Wso2UserMigrationResponse response = service.migrateUsers(request(
                role("Internal/subscriber", "kong-subscriber"),
                role("Internal/publisher", "kong-publisher")));

        assertEquals("PARTIAL_SUCCESS", response.getOverallStatus());
        assertEquals(1, response.getTotalSuccess());
        assertEquals(1, response.getTotalFailed());
    }

    @Test
    void migrateUsers_stillCreatesConsumerWhenUserHasNoRoles() {
        stubConsumer(KongAdminClient.WriteOutcome.CREATED, "consumer-uuid");

        Wso2UserMigrationUserRequest user = new Wso2UserMigrationUserRequest();
        user.setUserName("api.developer");
        user.setRoles(List.of());
        Wso2UserMigrationRequest request = baseRequest();
        request.setUsers(List.of(user));

        Wso2UserMigrationResponse response = service.migrateUsers(request);

        // The user reached Kong; there was simply no group to attach.
        assertEquals("SUCCESS", response.getOverallStatus());
        assertEquals("SUCCESS", response.getResults().get(0).getMigrationStatus());
        assertEquals("SKIPPED", response.getResults().get(0).getAssignmentStatus());
        verify(kongAdminClient, never()).assignGroup(any(), any(), any());
    }

    @Test
    void migrateUsers_returnsInvalidMappingReasonWhenKongRoleIsMissing() {
        stubConsumer(KongAdminClient.WriteOutcome.CREATED, "consumer-uuid");

        Wso2UserMigrationResponse response =
                service.migrateUsers(request(role("Internal/subscriber", "")));

        assertEquals("FAILED", response.getOverallStatus());
        assertEquals("Creation failed due to invalid role mapping. Kong role is required for migration.",
                response.getResults().get(0).getErrorMessage());
        verify(kongAdminClient, never()).assignGroup(any(), any(), any());
    }

    @Test
    void migrateUsers_fallsBackToUsernameWhenKongReturnsNoId() {
        stubConsumer(KongAdminClient.WriteOutcome.ALREADY_EXISTS, null);
        when(kongAdminClient.assignGroup(any(), any(), any()))
                .thenReturn(KongAdminClient.WriteOutcome.CREATED);

        service.migrateUsers(request(role("Internal/subscriber", "kong-subscriber")));

        verify(kongAdminClient).assignGroup(eq(CREDENTIALS), eq("api.developer"), eq("kong-subscriber"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void migrateUsers_persistsOneStatusDocumentPerUserRole() {
        stubConsumer(KongAdminClient.WriteOutcome.CREATED, "consumer-uuid");
        when(kongAdminClient.assignGroup(any(), any(), any()))
                .thenReturn(KongAdminClient.WriteOutcome.CREATED);

        service.migrateUsers(request(
                role("Internal/subscriber", "kong-subscriber"),
                role("Internal/publisher", "kong-publisher")));

        ArgumentCaptor<List<Wso2KongUserMigrationDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertAll(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals("SUCCESS", captor.getValue().get(0).getMigrationStatus());
        assertEquals("kong-subscriber", captor.getValue().get(0).getKongRoleName());
    }

    private void stubConsumer(KongAdminClient.WriteOutcome outcome, String id) {
        when(kongAdminClient.ensureConsumer(any(), any(), any()))
                .thenReturn(KongAdminClient.ConsumerRef.builder()
                        .id(id)
                        .username("api.developer")
                        .outcome(outcome)
                        .build());
    }

    private Wso2UserMigrationRequest request(Wso2UserMigrationRoleRequest... roles) {
        Wso2UserMigrationUserRequest user = new Wso2UserMigrationUserRequest();
        user.setUserName("api.developer");
        user.setUserEmail("sarah.williams@example.com");
        user.setRoles(List.of(roles));

        Wso2UserMigrationRequest request = baseRequest();
        request.setUsers(List.of(user));
        return request;
    }

    private Wso2UserMigrationRequest baseRequest() {
        Wso2UserMigrationRequest request = new Wso2UserMigrationRequest();
        request.setCompanyName("probestack");
        request.setWso2Tenant("carbon.super");
        request.setEnvironment("dev");
        request.setRequestTransactionId("tx-123");
        request.setUserEmail("admin@probestack.io");
        request.setKongControlPlane("default");
        return request;
    }

    private Wso2UserMigrationRoleRequest role(String wso2RoleName, String kongRoleName) {
        Wso2UserMigrationRoleRequest role = new Wso2UserMigrationRoleRequest();
        role.setWso2RoleName(wso2RoleName);
        role.setKongRoleName(kongRoleName);
        return role;
    }
}
