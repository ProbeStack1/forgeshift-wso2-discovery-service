package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.Wso2UserProfileDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB access layer for normalized WSO2 user-profile discovery documents.
 */
@Repository
@RequiredArgsConstructor
public class Wso2UserProfileRepository {

    private final MongoTemplate mongoTemplate;
    private final DiscoveryProperties discoveryProperties;

    /**
     * Returns the configured Mongo collection used for normalized user profiles.
     */
    public String collectionName() {
        return discoveryProperties.getUserProfilesCollection();
    }

    /**
     * Upserts every discovered user profile and returns the persisted document ids.
     */
    public List<String> upsertAll(List<Wso2UserProfileDocument> documents) {
        List<String> ids = new ArrayList<>();
        if (documents == null || documents.isEmpty()) {
            return ids;
        }
        for (Wso2UserProfileDocument document : documents) {
            upsert(document);
            ids.add(document.getId());
        }
        return ids;
    }

    /**
     * Upserts one normalized user profile using its deterministic document id.
     */
    private void upsert(Wso2UserProfileDocument document) {
        Instant now = document.getUpdatedDate() != null ? document.getUpdatedDate() : Instant.now();
        Update update = new Update()
                .setOnInsert("createdDate", document.getCreatedDate() != null ? document.getCreatedDate() : now)
                .set("companyName", document.getCompanyName())
                .set("sourceGateway", document.getSourceGateway())
                .set("targetGateway", document.getTargetGateway())
                .set("wso2Tenant", document.getWso2Tenant())
                .set("environment", document.getEnvironment())
                .set("requestTransactionId", document.getRequestTransactionId())
                .set("sourceUserId", document.getSourceUserId())
                .set("userName", document.getUserName())
                .set("firstName", document.getFirstName())
                .set("lastName", document.getLastName())
                .set("displayName", document.getDisplayName())
                .set("primaryEmail", document.getPrimaryEmail())
                .set("emails", document.getEmails())
                .set("active", document.getActive())
                .set("userType", document.getUserType())
                .set("roles", document.getRoles())
                .set("rolePermissions", document.getRolePermissions())
                .set("errorMessage", document.getErrorMessage())
                .set("rawPayload", document.getRawPayload())
                .set("requestedBy", document.getRequestedBy())
                .set("updatedDate", now);

        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(document.getId())),
                update,
                collectionName());
    }
}
