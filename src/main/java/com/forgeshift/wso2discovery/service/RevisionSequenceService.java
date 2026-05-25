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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allocates monotonic revision numbers per (companyName, wso2Tenant).
 *
 * <p>Idempotent on {@code discoveryId}: calling {@link #nextRevision} multiple
 * times with the same (companyName, wso2Tenant, discoveryId) returns the same
 * revision. This is how a single "discovery run" can cover N resource types
 * (apis, applications, subscriptions, ...) as separate HTTP calls but have
 * them all stamped with the same revision — mirroring the Apigee pattern.
 *
 * <p>Concurrency model — copied verbatim from
 * {@code MongoRevisionSequenceRepository} in the Apigee service:
 * <ol>
 *   <li>A per-scope (company|tenant) in-process lock serialises the
 *       idempotency-check + counter-increment + mapping-insert sequence,
 *       eliminating the race where two threads with the same
 *       {@code discoveryId} both pass step 1 before either bumps the
 *       counter (which would leak revision numbers and produce the
 *       {@code +2}/{@code +5} jumps reported in the field).</li>
 *   <li>The {@code DuplicateKeyException} catch on the mapping insert is
 *       retained as a cross-JVM safety net — the in-process lock only
 *       protects calls hitting the same replica.</li>
 * </ol>
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
     * Per-scope locks to prevent in-process races between the idempotency
     * check and the counter increment. Keyed by {@code company|tenant}
     * (the same scope the counter document is keyed by), so two concurrent
     * allocations for different scopes don't serialise unnecessarily.
     */
    private final ConcurrentHashMap<String, Object> allocationLocks = new ConcurrentHashMap<>();

    /**
     * Returns the revision number for this (companyName, wso2Tenant,
     * discoveryId). Allocates a new one on first call; returns the cached
     * one on every subsequent call with the same triple.
     */
    public int nextRevision(String companyName, String wso2Tenant, String discoveryId, String userEmail) {
        // ------------------------------------------------------
        // STEP 0 — normalise inputs and build document keys
        // ------------------------------------------------------
        String company = normalise(companyName, "_default_");
        String tenant  = normalise(wso2Tenant, "_default_");
        String txId    = normalise(discoveryId, "_default_");
        String email   = userEmail != null ? userEmail : "";

        String sequenceId  = String.join("|", company, tenant, txId);
        String scopeKey    = String.join("|", company, tenant);
        String mapColl     = props.getRevisionMapCollection();

        Object lock = allocationLocks.computeIfAbsent(scopeKey, k -> new Object());
        synchronized (lock) {

            // ------------------------------------------------------
            // STEP 1 — idempotent lookup. If this discoveryId already has a
            // revision, return it. This is the hot path for every call after
            // the first in a multi-resource discovery run.
            // ------------------------------------------------------
            Document existing = mongoTemplate.findOne(
                    Query.query(Criteria.where("_id").is(sequenceId)),
                    Document.class,
                    mapColl);
            if (existing != null) {
                Integer rev = existing.getInteger("revision");
                if (rev != null) {
                    log.debug("Reusing revision {} for company={} tenant={} discoveryId={}",
                            rev, company, tenant, txId);
                    return rev;
                }
            }

            // ------------------------------------------------------
            // STEP 2 — atomically bump the per-tenant counter to claim a new
            // revision number. Concurrent calls always get distinct values
            // here, but the surrounding lock ensures only one allocation per
            // scope is in flight on this replica.
            // ------------------------------------------------------
            Query counterQ = Query.query(Criteria.where("companyName").is(company)
                    .and("wso2Tenant").is(tenant));
            Update counterU = new Update()
                    .inc("current", 1)
                    .set("lastDiscoveryId", txId)
                    .set("lastUserEmail", email)
                    .set("updatedAt", Instant.now())
                    .setOnInsert("companyName", company)
                    .setOnInsert("wso2Tenant", tenant);
            RevisionCounter updated = mongoTemplate.findAndModify(
                    counterQ,
                    counterU,
                    FindAndModifyOptions.options().returnNew(true).upsert(true),
                    RevisionCounter.class,
                    props.getRevisionsCollection());
            int newRev = updated != null ? updated.getCurrent() : 1;

            // ------------------------------------------------------
            // STEP 3 — record the mapping. The in-process lock means a same-
            // JVM caller can't race here; the DuplicateKeyException catch
            // handles cross-JVM races on the same discoveryId. We accept the
            // cost of one wasted revision number in the cross-JVM race —
            // gaps in the sequence are fine for history.
            // ------------------------------------------------------
            try {
                Document map = new Document()
                        .append("_id", sequenceId)
                        .append("companyName", company)
                        .append("wso2Tenant", tenant)
                        .append("discoveryId", txId)
                        .append("revision", newRev)
                        .append("userEmail", email)
                        .append("createdAt", Instant.now());
                mongoTemplate.insert(map, mapColl);
                log.info("Allocated revision {} for company={} tenant={} discoveryId={}",
                        newRev, company, tenant, txId);
                return newRev;
            } catch (DuplicateKeyException race) {
                Document winner = mongoTemplate.findOne(
                        Query.query(Criteria.where("_id").is(sequenceId)),
                        Document.class,
                        mapColl);
                Integer winnerRev = winner != null ? winner.getInteger("revision") : null;
                log.warn("Cross-JVM concurrent allocation for discoveryId={} — wasted revision {}, using {}",
                        txId, newRev, winnerRev);
                return winnerRev != null ? winnerRev : newRev;
            }
        }
    }

    private static String normalise(String value, String fallback) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : fallback;
    }
}
