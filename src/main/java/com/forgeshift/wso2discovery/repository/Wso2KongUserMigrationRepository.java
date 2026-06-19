package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.Wso2KongUserMigrationDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB access layer for WSO2 to Kong user migration status records.
 */
@Repository
@RequiredArgsConstructor
public class Wso2KongUserMigrationRepository {

    private final MongoTemplate mongoTemplate;
    private final DiscoveryProperties discoveryProperties;

    /**
     * Returns the configured migration status collection name.
     */
    public String collectionName() {
        return discoveryProperties.getUserMigrationCollection();
    }

    /**
     * Upserts all migration result documents.
     */
    public void upsertAll(List<Wso2KongUserMigrationDocument> documents) {
        if (documents == null) {
            return;
        }
        for (Wso2KongUserMigrationDocument document : documents) {
            upsert(document);
        }
    }

    /**
     * Finds migration history using optional filters.
     */
    public List<Wso2KongUserMigrationDocument> findHistory(String companyName,
                                                           String wso2Tenant,
                                                           String environment,
                                                           String requestTransactionId,
                                                           int limit) {
        Criteria criteria = Criteria.where("companyName").is(companyName).and("wso2Tenant").is(wso2Tenant);
        if (StringUtils.hasText(environment)) {
            criteria = criteria.and("environment").is(environment);
        }
        if (StringUtils.hasText(requestTransactionId)) {
            criteria = criteria.and("requestTransactionId").is(requestTransactionId);
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdDate"))
                .limit(limit > 0 ? limit : 100);
        return mongoTemplate.find(query, Wso2KongUserMigrationDocument.class, collectionName());
    }

    /**
     * Upserts one migration status document by deterministic id.
     */
    private void upsert(Wso2KongUserMigrationDocument document) {
        Instant now = document.getUpdatedDate() != null ? document.getUpdatedDate() : Instant.now();
        Update update = new Update()
                .setOnInsert("createdDate", document.getCreatedDate() != null ? document.getCreatedDate() : now)
                .set("migrationId", document.getMigrationId())
                .set("companyName", document.getCompanyName())
                .set("sourceGateway", document.getSourceGateway())
                .set("targetGateway", document.getTargetGateway())
                .set("wso2Tenant", document.getWso2Tenant())
                .set("environment", document.getEnvironment())
                .set("requestTransactionId", document.getRequestTransactionId())
                .set("kongControlPlane", document.getKongControlPlane())
                .set("userName", document.getUserName())
                .set("userEmail", document.getUserEmail())
                .set("firstName", document.getFirstName())
                .set("lastName", document.getLastName())
                .set("wso2RoleName", document.getWso2RoleName())
                .set("kongRoleName", document.getKongRoleName())
                .set("migrationStatus", document.getMigrationStatus())
                .set("assignmentStatus", document.getAssignmentStatus())
                .set("errorMessage", document.getErrorMessage())
                .set("requestedBy", document.getRequestedBy())
                .set("updatedDate", now);
        mongoTemplate.upsert(Query.query(Criteria.where("_id").is(document.getId())),
                update,
                collectionName());
    }
}
