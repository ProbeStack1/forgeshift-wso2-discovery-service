package com.forgeshift.wso2discovery.service.wso2;

import com.forgeshift.wso2discovery.client.Wso2Client;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.domain.Wso2UserProfileDocument;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryRequest;
import com.forgeshift.wso2discovery.dto.Wso2UserProfileDiscoveryResponse;
import com.forgeshift.wso2discovery.repository.Wso2UserProfileRepository;
import com.forgeshift.wso2discovery.service.Wso2TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for normalized WSO2 SCIM user-profile discovery orchestration.
 */
@ExtendWith(MockitoExtension.class)
class Wso2UserProfileDiscoveryServiceTest {

    @Mock
    private Wso2Client wso2Client;

    @Mock
    private Wso2TokenService tokenService;

    @Mock
    private Wso2UserProfileRepository repository;

    private Wso2UserProfileDiscoveryService service;

    @BeforeEach
    void setUp() {
        Wso2Properties properties = new Wso2Properties();
        properties.setAdminScope("apim:admin");
        service = new Wso2UserProfileDiscoveryService(wso2Client, tokenService, properties, repository);
    }

    @Test
    void discoverUserProfiles_normalizesAndStoresUsers() {
        Wso2UserProfileDiscoveryRequest request = validRequest();
        when(tokenService.getToken("apim:admin", "probestack", "carbon.super")).thenReturn("token");
        when(wso2Client.listUsersStrict("token")).thenReturn(List.of(scimUser()));
        when(repository.upsertAll(any())).thenReturn(List.of("doc-1"));
        when(repository.collectionName()).thenReturn("wso2_user_profiles");

        Wso2UserProfileDiscoveryResponse response = service.discoverUserProfiles(request);

        assertEquals("COMPLETED", response.getDiscoveryStatus());
        assertEquals(1, response.getTotalUsers());
        assertEquals(2, response.getTotalRoles());
        assertEquals("wso2_user_profiles", response.getCollectionName());
        assertEquals("alice@example.com", response.getUsers().get(0).getPrimaryEmail());
        assertEquals(List.of("Internal/admin", "creator"), response.getUsers().get(0).getRoles());

        ArgumentCaptor<List<Wso2UserProfileDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertAll(captor.capture());
        Wso2UserProfileDocument document = captor.getValue().get(0);
        assertTrue(document.getId().contains("probestack|carbon.super|user-profiles|user-1|tx-123"));
        assertEquals("Alice", document.getFirstName());
        assertEquals("Smith", document.getLastName());
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
    void discoverUserProfiles_retriesOnceAfterUnauthorized() {
        Wso2UserProfileDiscoveryRequest request = validRequest();
        when(tokenService.getToken("apim:admin", "probestack", "carbon.super"))
                .thenReturn("expired")
                .thenReturn("fresh");
        when(wso2Client.listUsersStrict("expired")).thenThrow(unauthorized());
        when(wso2Client.listUsersStrict("fresh")).thenReturn(List.of(scimUser()));
        when(repository.upsertAll(any())).thenReturn(List.of("doc-1"));
        when(repository.collectionName()).thenReturn("wso2_user_profiles");

        Wso2UserProfileDiscoveryResponse response = service.discoverUserProfiles(request);

        assertEquals(1, response.getTotalUsers());
        verify(tokenService).invalidate("probestack", "carbon.super", "apim:admin");
        verify(wso2Client).listUsersStrict("fresh");
    }

    @Test
    void discoverUserProfiles_mongoSaveFailureFailsRequest() {
        Wso2UserProfileDiscoveryRequest request = validRequest();
        when(tokenService.getToken("apim:admin", "probestack", "carbon.super")).thenReturn("token");
        when(wso2Client.listUsersStrict("token")).thenReturn(List.of(scimUser()));
        when(repository.upsertAll(any())).thenThrow(new RuntimeException("mongo down"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.discoverUserProfiles(request));

        assertTrue(exception.getMessage().contains("Failed to store WSO2 user profiles"));
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

    private Map<String, Object> scimUser() {
        return Map.of(
                "id", "user-1",
                "userName", "alice",
                "name", Map.of("givenName", "Alice", "familyName", "Smith"),
                "emails", List.of(
                        Map.of("value", "other@example.com", "primary", false),
                        Map.of("value", "alice@example.com", "primary", true)),
                "active", true,
                "userType", "DEFAULT",
                "groups", List.of(
                        Map.of("display", "Internal/admin"),
                        Map.of("display", "creator"))
        );
    }

    private WebClientResponseException unauthorized() {
        return WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
    }
}
