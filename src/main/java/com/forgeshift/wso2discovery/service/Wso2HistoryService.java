package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.domain.RevisionCounter;
import com.forgeshift.wso2discovery.dto.HistoryDetailsResponse;
import com.forgeshift.wso2discovery.dto.HistoryResponse;
import com.forgeshift.wso2discovery.dto.HistoryRevisionsResponse;
import com.forgeshift.wso2discovery.dto.HistorySnapshot;
import com.forgeshift.wso2discovery.repository.RevisionCounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only history of past discoveries.
 *
 * Backs three endpoints:
 *   GET /wso2/history             → list distinct (discoveryId, revision) pairs with per-resource counts
 *   GET /wso2/history/revisions   → list revision numbers + the current counter
 *   GET /wso2/history/details     → fetch the raw DiscoverySnapshot rows for one (resourceType, discoveryId|revision)
 *
 * Aggregates across every {@code discovery_wso2_<resource>} collection so the
 * caller does not have to know which collection holds which type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2HistoryService {

    private final MongoTemplate mongoTemplate;
    private final RevisionCounterRepository revisionCounterRepository;
    private final DiscoveryProperties discoveryProps;

    /**
     * GET /wso2/history.
     *
     * <p>Returns the most recent discovery snapshots for ({@code companyName},
     * {@code wso2Tenant}), grouped by ({@code historyId}, {@code revision})
     * with a per-resource-type {@code summary} map. Shape mirrors the Apigee
     * {@code /history} response.</p>
     *
     * @param companyName           tenant header value, required
     * @param wso2Tenant            WSO2 tenant, required
     * @param resourceType          optional resource-type filter (limits which
     *                              collections are scanned; the summary in the
     *                              response is then restricted to that type)
     * @param requestTransactionId  optional historyId filter from the UI — when
     *                              supplied, only the snapshot for that
     *                              discovery run is returned
     * @param limit                 max snapshots to return (1..1000)
     */
    public HistoryResponse list(String companyName, String wso2Tenant, String resourceType,
                                String requestTransactionId, int limit) {
        long startNanos = System.nanoTime();
        if (!StringUtils.hasText(companyName) || !StringUtils.hasText(wso2Tenant)) {
            throw new IllegalArgumentException("companyName and wso2Tenant are required");
        }
        int cap = Math.max(1, Math.min(limit, 1000));

        List<ResourceType> targets = StringUtils.hasText(resourceType)
                ? List.of(ResourceType.fromSlug(resourceType))
                : ResourceType.ALL;

        // key = "<historyId>|<revision>", value = accumulator. LinkedHashMap to keep insertion order stable for tests.
        Map<String, Accumulator> bucket = new LinkedHashMap<>();

        for (ResourceType rt : targets) {
            String collection = rt.collectionName(discoveryProps.getCollectionPrefix());

            Criteria match = Criteria.where("companyName").is(companyName)
                    .and("wso2Tenant").is(wso2Tenant);
            if (StringUtils.hasText(requestTransactionId)) {
                match = match.and("discoveryId").is(requestTransactionId);
            }
            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.match(match),
                    Aggregation.group("discoveryId", "revision")
                            .count().as("count")
                            .max("snapshotAt").as("snapshotAt")
            );

            AggregationResults<Document> results;
            try {
                results = mongoTemplate.aggregate(agg, collection, Document.class);
            } catch (Exception e) {
                log.debug("[history] aggregation on {} failed: {}", collection, e.getMessage());
                continue;
            }
            for (Document doc : results.getMappedResults()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> id = (Map<String, Object>) doc.get("_id");
                if (id == null) continue;
                String historyId = str(id.get("discoveryId"));
                Integer revision = asInt(id.get("revision"));
                int count = asInt(doc.get("count"), 0);
                Instant snapshotAt = asInstant(doc.get("snapshotAt"));
                if (historyId == null || revision == null) continue;

                String key = historyId + "|" + revision;
                Accumulator acc = bucket.computeIfAbsent(key,
                        k -> new Accumulator(historyId, revision, targets));
                acc.counts.merge(rt.getSlug(), count, Integer::sum);
                if (snapshotAt != null && (acc.fetchedAt == null || snapshotAt.isAfter(acc.fetchedAt))) {
                    acc.fetchedAt = snapshotAt;
                }
            }
        }

        // Sort by revision desc, secondary by fetchedAt desc — matches Apigee /history ordering.
        List<Accumulator> all = new ArrayList<>(bucket.values());
        all.sort(Comparator
                .comparingInt((Accumulator a) -> a.revision).reversed()
                .thenComparing((Accumulator a) -> a.fetchedAt != null ? a.fetchedAt : Instant.EPOCH,
                        Comparator.reverseOrder()));
        if (all.size() > cap) all = all.subList(0, cap);

        List<HistorySnapshot> history = new ArrayList<>(all.size());
        for (Accumulator a : all) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.putAll(a.counts);
            history.add(HistorySnapshot.builder()
                    .historyId(a.historyId)
                    .revision(a.revision)
                    .fetchedAt(a.fetchedAt != null
                            ? OffsetDateTime.ofInstant(a.fetchedAt, ZoneOffset.UTC)
                            : null)
                    .summary(summary)
                    .build());
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        return HistoryResponse.builder()
                .companyName(companyName)
                .wso2Tenant(wso2Tenant)
                .history(history)
                .processingTimeMs(elapsedMs)
                .build();
    }

    /**
     * Mutable accumulator used while folding aggregation results across every
     * resource-type collection into one row per (historyId, revision).
     *
     * <p>{@code counts} is pre-seeded with 0 for every targeted resource type
     * so the JSON summary lists them all (matches the Apigee summary, which
     * always includes the full set of types).</p>
     */
    private static final class Accumulator {
        final String historyId;
        final int revision;
        final Map<String, Integer> counts;
        Instant fetchedAt;

        Accumulator(String historyId, int revision, List<ResourceType> targets) {
            this.historyId = historyId;
            this.revision = revision;
            this.counts = new LinkedHashMap<>();
            for (ResourceType rt : targets) {
                this.counts.put(rt.getSlug(), 0);
            }
        }
    }

    /**
     * GET /wso2/history/revisions.
     *
     * Combines the {@link RevisionCounter} document (gives current + lastUserEmail)
     * with a scan of every per-resource collection to enumerate which revisions
     * actually have data behind them.
     */
    public HistoryRevisionsResponse revisions(String companyName, String wso2Tenant) {
        if (!StringUtils.hasText(companyName) || !StringUtils.hasText(wso2Tenant)) {
            throw new IllegalArgumentException("companyName and wso2Tenant are required");
        }

        Optional<RevisionCounter> counter =
                revisionCounterRepository.findByCompanyNameAndWso2Tenant(companyName, wso2Tenant);

        Set<Integer> revisions = new TreeSet<>();
        for (ResourceType rt : ResourceType.ALL) {
            String collection = rt.collectionName(discoveryProps.getCollectionPrefix());
            try {
                List<?> distinct = mongoTemplate.findDistinct(
                        Query.query(Criteria.where("companyName").is(companyName)
                                .and("wso2Tenant").is(wso2Tenant)),
                        "revision", collection, Integer.class);
                for (Object o : distinct) {
                    Integer r = asInt(o);
                    if (r != null) revisions.add(r);
                }
            } catch (Exception e) {
                log.debug("[history/revisions] distinct on {} failed: {}", collection, e.getMessage());
            }
        }

        return HistoryRevisionsResponse.builder()
                .companyName(companyName)
                .wso2Tenant(wso2Tenant)
                .currentRevision(counter.map(RevisionCounter::getCurrent).orElse(null))
                .lastUserEmail(counter.map(RevisionCounter::getLastUserEmail).orElse(null))
                .lastBumpAt(counter.map(RevisionCounter::getUpdatedAt).orElse(null))
                .revisions(new ArrayList<>(revisions))
                .build();
    }

    /**
     * GET /wso2/history/details.
     *
     * Returns the full DiscoverySnapshot rows for one resource type, filtered
     * by discoveryId and/or revision. Supplying neither returns everything for
     * that resource type under (companyName, wso2Tenant), capped by limit.
     */
    public HistoryDetailsResponse details(String companyName, String wso2Tenant,
                                          String resourceType, String discoveryId,
                                          Integer revision, int limit) {
        if (!StringUtils.hasText(companyName) || !StringUtils.hasText(wso2Tenant)
                || !StringUtils.hasText(resourceType)) {
            throw new IllegalArgumentException("companyName, wso2Tenant, and resourceType are required");
        }
        int cap = Math.max(1, Math.min(limit, 1000));

        ResourceType rt = ResourceType.fromSlug(resourceType);
        String collection = rt.collectionName(discoveryProps.getCollectionPrefix());

        Criteria c = Criteria.where("companyName").is(companyName).and("wso2Tenant").is(wso2Tenant);
        if (StringUtils.hasText(discoveryId)) c = c.and("discoveryId").is(discoveryId);
        if (revision != null) c = c.and("revision").is(revision);

        List<DiscoverySnapshot> items = mongoTemplate.find(
                Query.query(c).limit(cap),
                DiscoverySnapshot.class,
                collection);

        return HistoryDetailsResponse.builder()
                .companyName(companyName)
                .wso2Tenant(wso2Tenant)
                .resourceType(resourceType)
                .discoveryId(discoveryId)
                .revision(revision)
                .collectionName(collection)
                .items(items)
                .totalCount(items.size())
                .build();
    }

    // -------- helpers ---------------------------------------------------

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return null; }
    }

    private static int asInt(Object o, int fallback) {
        Integer v = asInt(o);
        return v != null ? v : fallback;
    }

    private static Instant asInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof java.util.Date d) return d.toInstant();
        try { return Instant.parse(o.toString()); } catch (Exception e) { return null; }
    }
}
