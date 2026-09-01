package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.client.KonnectWriteOutcome;
import com.forgeshift.wso2discovery.client.KonnectCredentials;
import com.forgeshift.wso2discovery.client.KonnectIdentityClient;
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
 * WSO2 users are added to the Konnect teams their roles map to. Nothing here
 * creates a Konnect user: a person exists only once invited or signed in.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Wso2KongUserMigrationServiceTest {

    @Mock
    private Wso2KongUserMigrationRepository repository;

    @Mock
    private KonnectIdentityClient konnectIdentityClient;

    @Mock
    private KonnectProfileReader konnectProfileReader;

    private Wso2KongUserMigrationService service;

    private static final KonnectCredentials CREDENTIALS = KonnectCredentials.builder()
            .source("profile")
            .konnectBaseUrl("https://us.api.konghq.com")
            .konnectAccessToken("kpat_test")
            .controlPlaneId("cp-1234")
            .build();

    private static final KonnectIdentityClient.KonnectTeam PUBLISHER_TEAM =
            KonnectIdentityClient.KonnectTeam.builder()
                    .id("team-publisher")
                    .name("api-product-admin")
                    .description("Manage API products")
                    .systemTeam(true)
                    .build();

    private static final KonnectIdentityClient.KonnectTeam VIEWER_TEAM =
            KonnectIdentityClient.KonnectTeam.builder()
                    .id("team-viewer")
                    .name("organization-admin-readonly")
                    .systemTeam(true)
                    .build();

    @BeforeEach
    void setUp() {
        service = new Wso2KongUserMigrationService(repository, konnectIdentityClient, konnectProfileReader);
        when(konnectProfileReader.resolveTargets(any(), any(), any())).thenReturn(List.of(CREDENTIALS));
        when(konnectIdentityClient.listTeams(any())).thenReturn(List.of(PUBLISHER_TEAM, VIEWER_TEAM));
        when(konnectIdentityClient.findUserIdByEmail(any(), any())).thenReturn("konnect-user-1");
    }

    @Test
    void addsTheUserToTheTeamTheirRoleMapsTo() {
        when(konnectIdentityClient.addUserToTeam(any(), any(), any()))
                .thenReturn(KonnectWriteOutcome.CREATED);

        Wso2UserMigrationResponse response =
                service.migrateUsers(request(role("Internal/publisher", "api-product-admin")));

        assertEquals("SUCCESS", response.getOverallStatus());
        assertEquals(1, response.getTotalSuccess());

        Wso2UserMigrationResult result = response.getResults().get(0);
        assertEquals("SUCCESS", result.getAssignmentStatus());
        assertEquals("team-publisher", result.getKonnectTeamId());
        assertEquals("api-product-admin", result.getKonnectTeamName());
        assertNull(result.getErrorMessage());

        verify(konnectIdentityClient).addUserToTeam(eq(CREDENTIALS), eq("team-publisher"), eq("konnect-user-1"));
    }

    @Test
    void reportsAlreadyExistsWhenTheUserIsAlreadyInTheTeam() {
        when(konnectIdentityClient.addUserToTeam(any(), any(), any()))
                .thenReturn(KonnectWriteOutcome.ALREADY_EXISTS);

        Wso2UserMigrationResponse response =
                service.migrateUsers(request(role("Internal/publisher", "api-product-admin")));

        assertEquals(1, response.getTotalAlreadyExists());
        assertEquals(0, response.getTotalSuccess());
    }

    @Test
    void tellsTheOperatorWhenThePersonIsNotInKonnectYet() {
        // The expected case under SSO before anyone has signed in. It is not a
        // failure of the mapping, and the message has to say what to do.
        when(konnectIdentityClient.findUserIdByEmail(any(), any())).thenReturn(null);

        Wso2UserMigrationResponse response =
                service.migrateUsers(request(role("Internal/publisher", "api-product-admin")));

        assertEquals("FAILED", response.getOverallStatus());
        String error = response.getResults().get(0).getErrorMessage();
        assertTrue(error.contains("sarah.williams@example.com"), error);
        assertTrue(error.contains("Invite them"), error);
        verify(konnectIdentityClient, never()).addUserToTeam(any(), any(), any());
    }

    @Test
    void refusesAUserWithNoEmailBecauseKonnectKeysOnIt() {
        Wso2UserMigrationUserRequest user = new Wso2UserMigrationUserRequest();
        user.setUserName("admin");
        user.setRoles(List.of(role("admin", "organization-admin")));
        Wso2UserMigrationRequest request = baseRequest();
        request.setUsers(List.of(user));

        Wso2UserMigrationResponse response = service.migrateUsers(request);

        assertEquals("FAILED", response.getOverallStatus());
        assertTrue(response.getResults().get(0).getErrorMessage().contains("Konnect identifies people by email"));
        verify(konnectIdentityClient, never()).findUserIdByEmail(any(), any());
    }

    @Test
    void namesTheAvailableTeamsWhenTheMappingPointsAtNone() {
        Wso2UserMigrationResponse response =
                service.migrateUsers(request(role("Internal/publisher", "kong-subscriber")));

        String error = response.getResults().get(0).getErrorMessage();
        assertTrue(error.contains("No Konnect team named kong-subscriber"), error);
        assertTrue(error.contains("api-product-admin"), error);
        assertTrue(error.contains("organization-admin-readonly"), error);
    }

    @Test
    void matchesTeamNamesRegardlessOfCase() {
        when(konnectIdentityClient.addUserToTeam(any(), any(), any()))
                .thenReturn(KonnectWriteOutcome.CREATED);

        Wso2UserMigrationResponse response =
                service.migrateUsers(request(role("Internal/publisher", "  API-Product-Admin  ")));

        assertEquals(1, response.getTotalSuccess());
        assertEquals("team-publisher", response.getResults().get(0).getKonnectTeamId());
    }

    @Test
    void marksOnlyTheFailingRoleAsFailed() {
        when(konnectIdentityClient.addUserToTeam(any(), eq("team-publisher"), any()))
                .thenReturn(KonnectWriteOutcome.CREATED);
        when(konnectIdentityClient.addUserToTeam(any(), eq("team-viewer"), any()))
                .thenThrow(WebClientResponseException.create(
                        HttpStatus.FORBIDDEN.value(), "Forbidden", null, "{\"message\":\"no\"}".getBytes(), null));

        Wso2UserMigrationResponse response = service.migrateUsers(request(
                role("Internal/publisher", "api-product-admin"),
                role("Internal/everyone", "organization-admin-readonly")));

        assertEquals("PARTIAL_SUCCESS", response.getOverallStatus());
        assertEquals(1, response.getTotalSuccess());
        assertEquals(1, response.getTotalFailed());
    }

    @Test
    void failsEveryRowWhenTheTeamListCannotBeRead() {
        when(konnectIdentityClient.listTeams(any()))
                .thenThrow(WebClientResponseException.create(
                        HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, "{}".getBytes(), null));

        Wso2UserMigrationResponse response = service.migrateUsers(request(
                role("Internal/publisher", "api-product-admin"),
                role("Internal/everyone", "organization-admin-readonly")));

        assertEquals("FAILED", response.getOverallStatus());
        assertEquals(2, response.getTotalFailed());
        assertTrue(response.getResults().get(0).getErrorMessage().contains("Could not read Konnect teams"));
    }

    @Test
    void addsTheUserOncePerMappedRole() {
        when(konnectIdentityClient.addUserToTeam(any(), any(), any()))
                .thenReturn(KonnectWriteOutcome.CREATED);

        service.migrateUsers(request(
                role("Internal/publisher", "api-product-admin"),
                role("Internal/everyone", "organization-admin-readonly")));

        // One lookup for the person, one join per role.
        verify(konnectIdentityClient, org.mockito.Mockito.times(1)).findUserIdByEmail(any(), any());
        verify(konnectIdentityClient, org.mockito.Mockito.times(2)).addUserToTeam(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsTheTeamOnEveryStoredRow() {
        when(konnectIdentityClient.addUserToTeam(any(), any(), any()))
                .thenReturn(KonnectWriteOutcome.CREATED);

        service.migrateUsers(request(role("Internal/publisher", "api-product-admin")));

        ArgumentCaptor<List<Wso2KongUserMigrationDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertAll(captor.capture());
        assertEquals("team-publisher", captor.getValue().get(0).getKonnectTeamId());
        assertEquals("api-product-admin", captor.getValue().get(0).getKonnectTeamName());
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
        return request;
    }

    private Wso2UserMigrationRoleRequest role(String wso2RoleName, String kongRoleName) {
        Wso2UserMigrationRoleRequest role = new Wso2UserMigrationRoleRequest();
        role.setWso2RoleName(wso2RoleName);
        role.setKongRoleName(kongRoleName);
        return role;
    }
}
