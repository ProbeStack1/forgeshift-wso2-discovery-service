package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.Wso2KongRoleMappingDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB access layer for WSO2 to Kong role mapping rules.
 */
@Repository
@RequiredArgsConstructor
public class Wso2KongRoleMappingRepository {

    private final MongoTemplate mongoTemplate;
    private final DiscoveryProperties discoveryProperties;

    /**
     * Returns the configured role-mapping collection name.
     */
    public String collectionName() {
        return discoveryProperties.getUserRoleMappingsCollection();
    }

    /**
     * Finds one active or inactive mapping by the unique business key.
     */
    public Wso2KongRoleMappingDocument findByBusinessKey(String companyName,
                                                         String sourceGateway,
                                                         String targetGateway,
                                                         String wso2Tenant,
                                                         String kongControlPlane,
                                                         String normalizedRole) {
        return mongoTemplate.findOne(businessKeyQuery(companyName, sourceGateway, targetGateway,
                        wso2Tenant, kongControlPlane, normalizedRole),
                Wso2KongRoleMappingDocument.class,
                collectionName());
    }

    /**
     * Finds mappings for a batch of normalized WSO2 role names.
     */
    public List<Wso2KongRoleMappingDocument> findByNormalizedRoles(String companyName,
                                                                   String sourceGateway,
                                                                   String targetGateway,
                                                                   String wso2Tenant,
                                                                   String kongControlPlane,
                                                                   List<String> normalizedRoles) {
        Query query = Query.query(Criteria.where("companyName").is(trim(companyName))
                .and("sourceGateway").is(trim(sourceGateway))
                .and("targetGateway").is(trim(targetGateway))
                .and("wso2Tenant").is(trim(wso2Tenant))
                .and("kongControlPlane").is(trim(kongControlPlane))
                .and("wso2RoleNameNormalized").in(normalizedRoles));
        List<Wso2KongRoleMappingDocument> mappings =
                mongoTemplate.find(query, Wso2KongRoleMappingDocument.class, collectionName());
        if (!mappings.isEmpty()) {
            return mappings;
        }
        return findByNormalizedRolesFallback(companyName, wso2Tenant, normalizedRoles);
    }

    /**
     * Upserts a role mapping document while preserving created audit fields.
     */
    public void upsert(Wso2KongRoleMappingDocument document) {
        Instant now = document.getUpdatedDate() != null ? document.getUpdatedDate() : Instant.now();
        Update update = new Update()
                .setOnInsert("mappingId", document.getMappingId())
                .set("companyName", document.getCompanyName())
                .set("sourceGateway", document.getSourceGateway())
                .set("targetGateway", document.getTargetGateway())
                .set("wso2Tenant", document.getWso2Tenant())
                .set("environment", document.getEnvironment())
                .set("kongControlPlane", document.getKongControlPlane())
                .set("wso2RoleName", document.getWso2RoleName())
                .set("wso2RoleNameNormalized", document.getWso2RoleNameNormalized())
                .set("kongRoleName", document.getKongRoleName())
                .set("scopeType", document.getScopeType())
                .set("status", document.getStatus())
                .set("createdBy", document.getCreatedBy())
                .set("createdDate", document.getCreatedDate() != null ? document.getCreatedDate() : now)
                .set("updatedBy", document.getUpdatedBy())
                .set("updatedDate", now);
        mongoTemplate.upsert(
                businessKeyQuery(document.getCompanyName(), document.getSourceGateway(), document.getTargetGateway(), document.getWso2Tenant(),
                        document.getKongControlPlane(), document.getWso2RoleNameNormalized()),
                update,
                collectionName());
    }

    /**
     * Builds the unique business-key query for role mapping upserts/lookups.
     */
    private Query businessKeyQuery(String companyName,
                                   String sourceGateway,
                                   String targetGateway,
                                   String wso2Tenant,
                                   String kongControlPlane,
                                   String normalizedRole) {
        return Query.query(Criteria.where("companyName").is(trim(companyName))
                .and("sourceGateway").is(trim(sourceGateway))
                .and("targetGateway").is(trim(targetGateway))
                .and("wso2Tenant").is(trim(wso2Tenant))
                .and("kongControlPlane").is(trim(kongControlPlane))
                .and("wso2RoleNameNormalized").is(normalizedRole));
    }

    /**
     * Finds mappings using the minimal stable key. This keeps resolve backward
     * compatible with records created before gateway/control-plane fields were
     * consistently populated.
     */
    private List<Wso2KongRoleMappingDocument> findByNormalizedRolesFallback(String companyName,
                                                                            String wso2Tenant,
                                                                            List<String> normalizedRoles) {
        Query fallbackQuery = Query.query(Criteria.where("companyName").is(trim(companyName))
                .and("wso2Tenant").is(trim(wso2Tenant))
                .and("wso2RoleNameNormalized").in(normalizedRoles));
        return mongoTemplate.find(fallbackQuery, Wso2KongRoleMappingDocument.class, collectionName());
    }

    /**
     * Normalizes business-key text values before querying MongoDB.
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
