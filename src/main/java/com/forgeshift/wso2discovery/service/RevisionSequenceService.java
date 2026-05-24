package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.RevisionCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Allocates monotonic revision numbers per (companyName, wso2Tenant).
 *
 * The atomic findAndModify on {@code discovery_revisions} guarantees that
 * concurrent discoveries against the same tenant do not collide.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevisionSequenceService {

    private final MongoTemplate mongoTemplate;
    private final DiscoveryProperties props;

    /**
     * Atomically increment the counter for (companyName, wso2Tenant) and
     * return the new value. Creates the counter document on first call.
     */
    public int nextRevision(String companyName, String wso2Tenant, String discoveryId, String userEmail) {
        Query q = Query.query(Criteria.where("companyName").is(companyName).and("wso2Tenant").is(wso2Tenant));
        Update u = new Update()
                .inc("current", 1)
                .set("lastDiscoveryId", discoveryId)
                .set("lastUserEmail", userEmail)
                .set("updatedAt", Instant.now())
                .setOnInsert("companyName", companyName)
                .setOnInsert("wso2Tenant", wso2Tenant);

        RevisionCounter updated = mongoTemplate.findAndModify(
                q,
                u,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                RevisionCounter.class,
                props.getRevisionsCollection());

        int rev = updated != null ? updated.getCurrent() : 1;
        log.debug("Allocated revision {} for company={} tenant={} discoveryId={}",
                rev, companyName, wso2Tenant, discoveryId);
        return rev;
    }
}
