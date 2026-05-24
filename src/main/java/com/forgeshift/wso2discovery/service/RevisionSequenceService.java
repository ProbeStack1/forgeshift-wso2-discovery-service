package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.RevisionCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
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
 * <p>Idempotent on {@code discoveryId}: calling {@link #nextRevision} multiple
 * times with the same (companyName, wso2Tenant, discoveryId) returns the same
 * revision. This is how a single "discovery run" can cover N resource types
 * (apis, applications, subscriptions, ...) as separate HTTP calls but have
 * them all stamped with the same revision — mirroring the Apigee pattern.
 *
 * <p>Persistence layout:
 * <ul>
 *   <li>{@code discovery_revisions} — one doc per (company, tenant) with the
 *       running counter, used for the atomic increment.</li>
 *   <li>{@code discovery_revision_map} — one doc per (company, tenant,
 *       discoveryId) caching the allocated revision for idempotent lookups.
 *       The {@code _id} encodes the triple, so a Mongo unique-key conflict
 *       acts as a distributed lock against concurrent allocations.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevisionSequenceService {

    private final MongoTemplate mongoTemplate;
    private final DiscoveryProperties props;

    /**
     * Returns the revision number for this (companyName, wso2Tenant,
     * discoveryId). Allocates a new one on first call; returns the cached
     * one on every subsequent call with the same triple.
     */
    public int nextRevision(String companyName, String wso2Tenant, String discoveryId, String userEmail) {
        String sequenceId = sequenceId(companyName, wso2Tenant, discoveryId);
        String mapColl = props.getRevisionMapCollection();

        // Step 1 — idempotent lookup. If this discoveryId already has a
        // revision, return it. This is the hot path for every call after the
        // first in a multi-resource discovery run.
        Document existing = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(sequenceId)),
                Document.class,
                mapColl);
        if (existing != null) {
            Integer rev = existing.getInteger("revision");
            if (rev != null) {
                log.debug("Reusing revision {} for company={} tenant={} discoveryId={}",
                        rev, companyName, wso2Tenant, discoveryId);
                return rev;
            }
        }

        // Step 2 — atomically bump the per-tenant counter to claim a new
        // revision number. Concurrent calls always get distinct values here.
        Query counterQ = Query.query(Criteria.where("companyName").is(companyName)
                .and("wso2Tenant").is(wso2Tenant));
        Update counterU = new Update()
                .inc("current", 1)
                .set("lastDiscoveryId", discoveryId)
                .set("lastUserEmail", userEmail)
                .set("updatedAt", Instant.now())
                .setOnInsert("companyName", companyName)
                .setOnInsert("wso2Tenant", wso2Tenant);
        RevisionCounter updated = mongoTemplate.findAndModify(
                counterQ,
                counterU,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                RevisionCounter.class,
                props.getRevisionsCollection());
        int newRev = updated != null ? updated.getCurrent() : 1;

        // Step 3 — record the mapping. If two threads/replicas both got here
        // for the same discoveryId, the second insert fails with a duplicate
        // _id; we re-read the winner's revision and return that. We accept
        // the cost of one wasted revision number in that race — gaps in the
        // sequence are fine for history.
        try {
            Document map = new Document()
                    .append("_id", sequenceId)
                    .append("companyName", companyName)
                    .append("wso2Tenant", wso2Tenant)
                    .append("discoveryId", discoveryId)
                    .append("revision", newRev)
                    .append("userEmail", userEmail)
                    .append("createdAt", Instant.now());
            mongoTemplate.insert(map, mapColl);
            log.debug("Allocated revision {} for company={} tenant={} discoveryId={}",
                    newRev, companyName, wso2Tenant, discoveryId);
            return newRev;
        } catch (DuplicateKeyException race) {
            Document winner = mongoTemplate.findOne(
                    Query.query(Criteria.where("_id").is(sequenceId)),
                    Document.class,
                    mapColl);
            Integer winnerRev = winner != null ? winner.getInteger("revision") : null;
            log.warn("Concurrent allocation for discoveryId={} — wasted revision {}, using {}",
                    discoveryId, newRev, winnerRev);
            return winnerRev != null ? winnerRev : newRev;
        }
    }

    private static String sequenceId(String companyName, String wso2Tenant, String discoveryId) {
        return companyName + "|" + wso2Tenant + "|" + discoveryId;
    }
}
