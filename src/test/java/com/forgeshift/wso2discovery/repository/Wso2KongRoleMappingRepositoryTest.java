package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.Wso2KongRoleMappingDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WSO2-Kong role mapping Mongo lookups.
 */
@ExtendWith(MockitoExtension.class)
class Wso2KongRoleMappingRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private Wso2KongRoleMappingRepository repository;

    @BeforeEach
    void setUp() {
        DiscoveryProperties properties = new DiscoveryProperties();
        properties.setUserRoleMappingsCollection("wso2_kong_role_mapping");
        repository = new Wso2KongRoleMappingRepository(mongoTemplate, properties);
    }

    @Test
    void findByNormalizedRoles_usesFallbackWhenStrictLookupReturnsEmpty() {
        Wso2KongRoleMappingDocument fallbackDocument = Wso2KongRoleMappingDocument.builder()
                .wso2RoleName("admin")
                .wso2RoleNameNormalized("admin")
                .build();
        when(mongoTemplate.find(any(Query.class), eq(Wso2KongRoleMappingDocument.class), eq("wso2_kong_role_mapping")))
                .thenReturn(List.of())
                .thenReturn(List.of(fallbackDocument));

        List<Wso2KongRoleMappingDocument> results = repository.findByNormalizedRoles(
                "probestack", "wso2", "kong-konnect", "carbon.super", "default", List.of("admin"));

        assertSame(fallbackDocument, results.get(0));
        verify(mongoTemplate, times(2)).find(any(Query.class),
                eq(Wso2KongRoleMappingDocument.class),
                eq("wso2_kong_role_mapping"));
    }
}
