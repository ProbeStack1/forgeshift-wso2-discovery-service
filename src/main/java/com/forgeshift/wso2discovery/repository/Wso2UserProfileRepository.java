package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.Wso2UserProfileDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * Every stored profile for one discovery run, ordered by user name.
     *
     * <p>Documents are keyed per user and upserted, so re-running the same
     * transaction updates rows rather than adding them — which is what makes
     * {@code requestTransactionId} a usable name for a run.
     */
    public List<Wso2UserProfileDocument> findByTransaction(String companyName,
                                                           String wso2Tenant,
                                                           String requestTransactionId) {
        Query query = Query.query(Criteria.where("companyName").is(companyName)
                .and("wso2Tenant").is(wso2Tenant)
                .and("requestTransactionId").is(requestTransactionId));
        query.with(Sort.by(Sort.Direction.ASC, "userName"));
        return mongoTemplate.find(query, Wso2UserProfileDocument.class, collectionName());
    }

    /**
     * The discovery runs held for one tenant, newest first.
     *
     * <p>Grouped in memory rather than with an aggregation pipeline. This
     * collection holds a tenant's users — hundreds of documents, not millions —
     * and the alternative is a pipeline whose grouping over a nested permission
     * map cannot be read at a glance. If a tenant ever outgrows that, this is
     * the method to move server-side.
     */
    public List<Wso2UserProfileRun> listRuns(String companyName, String wso2Tenant, int limit) {
        Query query = Query.query(Criteria.where("companyName").is(companyName)
                .and("wso2Tenant").is(wso2Tenant));
        List<Wso2UserProfileDocument> documents =
                mongoTemplate.find(query, Wso2UserProfileDocument.class, collectionName());

        Map<String, Wso2UserProfileRun> byRun = new LinkedHashMap<>();
        for (Wso2UserProfileDocument document : documents) {
            String transactionId = document.getRequestTransactionId();
            if (!StringUtils.hasText(transactionId)) {
                continue;
            }
            byRun.computeIfAbsent(transactionId, Wso2UserProfileRun::new).add(document);
        }

        List<Wso2UserProfileRun> runs = new ArrayList<>(byRun.values());
        // Newest first. A run with no timestamp sorts last rather than throwing:
        // documents written before createdDate was set have none.
        runs.sort(Comparator.comparing(
                Wso2UserProfileRun::getDiscoveredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return limit > 0 && runs.size() > limit ? List.copyOf(runs.subList(0, limit)) : runs;
    }

    /**
     * One discovery run, accumulated from the documents it wrote.
     */
    public static final class Wso2UserProfileRun {
        private final String requestTransactionId;
        private final Set<String> roles = new LinkedHashSet<>();
        private int totalUsers;
        private Instant discoveredAt;

        private Wso2UserProfileRun(String requestTransactionId) {
            this.requestTransactionId = requestTransactionId;
        }

        private void add(Wso2UserProfileDocument document) {
            totalUsers++;
            if (document.getRoles() != null) {
                roles.addAll(document.getRoles());
            }
            Instant written = document.getUpdatedDate() != null
                    ? document.getUpdatedDate() : document.getCreatedDate();
            if (written != null && (discoveredAt == null || written.isAfter(discoveredAt))) {
                discoveredAt = written;
            }
        }

        public String getRequestTransactionId() {
            return requestTransactionId;
        }

        public int getTotalUsers() {
            return totalUsers;
        }

        public int getTotalRoles() {
            return roles.size();
        }

        public Instant getDiscoveredAt() {
            return discoveredAt;
        }
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
