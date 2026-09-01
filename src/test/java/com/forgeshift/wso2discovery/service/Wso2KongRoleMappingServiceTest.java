package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.domain.Wso2KongRoleMappingDocument;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingEntryRequest;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingResolveRequest;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingResolveResponse;
import com.forgeshift.wso2discovery.dto.Wso2RoleMappingUpsertRequest;
import com.forgeshift.wso2discovery.repository.Wso2KongRoleMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for WSO2 role to Kong role mapping resolution.
 */
@ExtendWith(MockitoExtension.class)
class Wso2KongRoleMappingServiceTest {

    @Mock
    private Wso2KongRoleMappingRepository repository;

    private Wso2KongRoleMappingService service;

    @BeforeEach
    void setUp() {
        service = new Wso2KongRoleMappingService(repository);
    }

    @Test
    void resolveRoleMappings_returnsMappedRolesInRequestOrder() {
        Wso2RoleMappingResolveRequest request = resolveRequest();
        when(repository.findByNormalizedRoles("probestack", "wso2", "kong-konnect", "carbon.super", "default",
                List.of("internal/subscriber", "internal/publisher", "admin")))
                .thenReturn(List.of(
                        mapping("MAP-1", "Internal/subscriber", "consumer-group-subscriber", "ACTIVE"),
                        mapping("MAP-2", "Internal/publisher", "api-product-admin", "ACTIVE"),
                        mapping("MAP-3", "admin", "organization-admin", "ACTIVE")));

        Wso2RoleMappingResolveResponse response = service.resolveRoleMappings(request);

        assertEquals(3, response.getMappedRoles());
        assertEquals(0, response.getUnmappedRoles());
        assertEquals("Internal/subscriber", response.getRoles().get(0).getWso2RoleName());
        assertEquals("consumer-group-subscriber", response.getRoles().get(0).getKongRoleName());
        assertEquals("MAPPED", response.getRoles().get(0).getMappingStatus());
        assertEquals("Internal/publisher", response.getRoles().get(1).getWso2RoleName());
        assertEquals("admin", response.getRoles().get(2).getWso2RoleName());
    }

    @Test
    void upsertRoleMappings_populatesCreatedAndUpdatedAuditFieldsForNewMapping() {
        Wso2RoleMappingUpsertRequest request = upsertRequest("admin@probestack.io");

        service.upsertRoleMappings(request);

        ArgumentCaptor<Wso2KongRoleMappingDocument> captor =
                ArgumentCaptor.forClass(Wso2KongRoleMappingDocument.class);
        verify(repository).upsert(captor.capture());
        Wso2KongRoleMappingDocument document = captor.getValue();
        assertEquals("admin@probestack.io", document.getCreatedBy());
        assertEquals("admin@probestack.io", document.getUpdatedBy());
        assertNotNull(document.getCreatedDate());
        assertNotNull(document.getUpdatedDate());
    }

    @Test
    void upsertRoleMappings_preservesCreatedAuditFieldsForExistingMapping() {
        Instant createdDate = Instant.parse("2026-06-19T01:00:00Z");
        Wso2RoleMappingUpsertRequest request = upsertRequest("ops@probestack.io");
        when(repository.findByBusinessKey("probestack", "wso2", "kong-konnect", "carbon.super", "default", "admin"))
                .thenReturn(Wso2KongRoleMappingDocument.builder()
                        .mappingId("MAP-EXISTING")
                        .createdBy("original@probestack.io")
                        .createdDate(createdDate)
                        .build());

        service.upsertRoleMappings(request);

        ArgumentCaptor<Wso2KongRoleMappingDocument> captor =
                ArgumentCaptor.forClass(Wso2KongRoleMappingDocument.class);
        verify(repository).upsert(captor.capture());
        Wso2KongRoleMappingDocument document = captor.getValue();
        assertEquals("original@probestack.io", document.getCreatedBy());
        assertEquals(createdDate, document.getCreatedDate());
        assertEquals("ops@probestack.io", document.getUpdatedBy());
        assertNotNull(document.getUpdatedDate());
    }

    private Wso2RoleMappingResolveRequest resolveRequest() {
        Wso2RoleMappingResolveRequest request = new Wso2RoleMappingResolveRequest();
        request.setCompanyName("probestack");
        request.setSourceGateway("wso2");
        request.setTargetGateway("kong-konnect");
        request.setWso2Tenant("carbon.super");
        request.setEnvironment("");
        request.setRequestTransactionId("HWWS_probestack_carbon.super_20260619062450186");
        request.setUserEmail("ops@local");
        request.setKongControlPlane("default");
        request.setWso2Roles(List.of("Internal/subscriber", "Internal/publisher", "admin"));
        return request;
    }

    private Wso2RoleMappingUpsertRequest upsertRequest(String userEmail) {
        Wso2RoleMappingEntryRequest entry = new Wso2RoleMappingEntryRequest();
        entry.setWso2RoleName("admin");
        entry.setKongRoleName("organization-admin");
        entry.setScopeType("GLOBAL");
        entry.setStatus("ACTIVE");

        Wso2RoleMappingUpsertRequest request = new Wso2RoleMappingUpsertRequest();
        request.setCompanyName("probestack");
        request.setSourceGateway("wso2");
        request.setTargetGateway("kong-konnect");
        request.setWso2Tenant("carbon.super");
        request.setEnvironment("");
        request.setRequestTransactionId("HWWS_probestack_carbon.super_20260619062450186");
        request.setUserEmail(userEmail);
        request.setKongControlPlane("default");
        request.setRoleMappings(List.of(entry));
        return request;
    }

    private Wso2KongRoleMappingDocument mapping(String mappingId,
                                                String wso2RoleName,
                                                String kongRoleName,
                                                String status) {
        return Wso2KongRoleMappingDocument.builder()
                .mappingId(mappingId)
                .companyName("probestack")
                .sourceGateway("wso2")
                .targetGateway("kong-konnect")
                .wso2Tenant("carbon.super")
                .kongControlPlane("default")
                .wso2RoleName(wso2RoleName)
                .wso2RoleNameNormalized(wso2RoleName.toLowerCase())
                .kongRoleName(kongRoleName)
                .scopeType("GLOBAL")
                .status(status)
                .build();
    }
}
