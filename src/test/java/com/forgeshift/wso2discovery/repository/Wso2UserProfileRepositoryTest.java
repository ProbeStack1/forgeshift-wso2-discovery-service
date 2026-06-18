package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.Wso2UserProfileDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for normalized WSO2 user-profile repository behavior.
 */
@ExtendWith(MockitoExtension.class)
class Wso2UserProfileRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private Wso2UserProfileRepository repository;

    @BeforeEach
    void setUp() {
        DiscoveryProperties properties = new DiscoveryProperties();
        properties.setUserProfilesCollection("wso2_user_profiles");
        repository = new Wso2UserProfileRepository(mongoTemplate, properties);
    }

    @Test
    void upsertAllUsesConfiguredCollectionAndReturnsIds() {
        Wso2UserProfileDocument document = Wso2UserProfileDocument.builder()
                .id("doc-1")
                .companyName("probestack")
                .wso2Tenant("carbon.super")
                .requestTransactionId("tx-123")
                .sourceUserId("user-1")
                .userName("alice")
                .createdDate(Instant.now())
                .updatedDate(Instant.now())
                .build();

        List<String> ids = repository.upsertAll(List.of(document));

        assertEquals(List.of("doc-1"), ids);
        verify(mongoTemplate).upsert(any(), any(), eq("wso2_user_profiles"));
    }
}
