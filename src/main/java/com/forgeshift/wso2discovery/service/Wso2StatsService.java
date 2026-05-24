package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.ResourceType;
import com.forgeshift.wso2discovery.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Roll-ups over the discovery snapshot collections.
 *
 * WSO2 has no built-in analytics REST API to proxy (Choreo Insights is a
 * separate product), so this is genuinely different from Apigee's
 * {@code /stats/{dimension}} which proxies the Apigee Analytics API.
 *
 * Supported dimensions:
 *   byResourceType - { apis: N, applications: M, ... }   single-key counts
 *   byTenant       - { "<companyName>|<wso2Tenant>": N }  single-key counts
 *   byDiscoveryId  - one Bucket per (discoveryId, revision) with snapshotAt
 *   byTime         - one Bucket per UTC day, summed across all collections
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2StatsService {

    private final MongoTemplate mongoTemplate;
    private final DiscoveryProperties discoveryProps;

    public StatsResponse compute(String dimension, String companyName, String wso2Tenant) {
        if (!StringUtils.hasText(dimension)) {
            throw new IllegalArgumentException("dimension is required");
        }
        switch (dimension) {
            case "byResourceType": return byResourceType(companyName, wso2Tenant);
            case "byTenant":       return byTenant();
            case "byDiscoveryId":  return byDiscoveryId(companyName, wso2Tenant);
            case "byTime":         return byTime(companyName, wso2Tenant);
            default:
                throw new IllegalArgumentException(
                        "Unknown dimension: " + dimension
                                + " (allowed: byResourceType, byTenant, byDiscoveryId, byTime)");
        }
    }

    private StatsResponse byResourceType(String company, String tenant) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        for (ResourceType rt : ResourceType.ALL) {
            String coll = rt.collectionName(discoveryProps.getCollectionPrefix());
            long c = mongoTemplate.count(matchTenant(company, tenant), coll);
            if (c > 0) {
                counts.put(rt.getSlug(), c);
                total += c;
            }
        }
        return base("byResourceType", company, tenant, total)
                .counts(counts)
                .build();
    }

    private StatsResponse byTenant() {
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        for (ResourceType rt : ResourceType.ALL) {
            String coll = rt.collectionName(discoveryProps.getCollectionPrefix());
            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.group("companyName", "wso2Tenant").count().as("count")
            );
            AggregationResults<Document> results;
            try {
                results = mongoTemplate.aggregate(agg, coll, Document.class);
            } catch (Exception e) {
                log.debug("[stats:byTenant] aggregation on {} failed: {}", coll, e.getMessage());
                continue;
            }
            for (Document d : results.getMappedResults()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> id = (Map<String, Object>) d.get("_id");
                if (id == null) continue;
                String key = id.get("companyName") + "|" + id.get("wso2Tenant");
                long c = asLong(d.get("count"), 0);
                counts.merge(key, c, Long::sum);
                total += c;
            }
        }
        return base("byTenant", null, null, total)
                .counts(counts)
                .build();
    }

    private StatsResponse byDiscoveryId(String company, String tenant) {
        Map<String, StatsResponse.Bucket> bucketByDid = new LinkedHashMap<>();
        long total = 0;
        for (ResourceType rt : ResourceType.ALL) {
            String coll = rt.collectionName(discoveryProps.getCollectionPrefix());
            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.match(matchCriteria(company, tenant)),
                    Aggregation.group("discoveryId", "revision")
                            .count().as("count")
                            .max("snapshotAt").as("snapshotAt")
            );
            AggregationResults<Document> results;
            try {
                results = mongoTemplate.aggregate(agg, coll, Document.class);
            } catch (Exception e) {
                log.debug("[stats:byDiscoveryId] aggregation on {} failed: {}", coll, e.getMessage());
                continue;
            }
            for (Document d : results.getMappedResults()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> id = (Map<String, Object>) d.get("_id");
                if (id == null) continue;
                String did = (String) id.get("discoveryId");
                Integer rev = asInt(id.get("revision"));
                if (did == null) continue;
                String key = did;
                String subkey = rev == null ? null : "r" + rev;
                long c = asLong(d.get("count"), 0);
                total += c;

                StatsResponse.Bucket b = bucketByDid.computeIfAbsent(key, k -> StatsResponse.Bucket.builder()
                        .key(key).subkey(subkey).count(0).build());
                b.setCount(b.getCount() + c);
                Instant snapshotAt = asInstant(d.get("snapshotAt"));
                if (snapshotAt != null && (b.getSnapshotAt() == null || snapshotAt.isAfter(b.getSnapshotAt()))) {
                    b.setSnapshotAt(snapshotAt);
                }
            }
        }
        return base("byDiscoveryId", company, tenant, total)
                .buckets(new ArrayList<>(bucketByDid.values()))
                .build();
    }

    private StatsResponse byTime(String company, String tenant) {
        Map<String, Long> daily = new LinkedHashMap<>();
        long total = 0;
        for (ResourceType rt : ResourceType.ALL) {
            String coll = rt.collectionName(discoveryProps.getCollectionPrefix());
            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.match(matchCriteria(company, tenant)),
                    // truncate snapshotAt to UTC day
                    Aggregation.project()
                            .and(org.springframework.data.mongodb.core.aggregation.DateOperators
                                    .DateToString.dateOf("snapshotAt")
                                    .toString("%Y-%m-%d")).as("day"),
                    Aggregation.group("day").count().as("count")
            );
            AggregationResults<Document> results;
            try {
                results = mongoTemplate.aggregate(agg, coll, Document.class);
            } catch (Exception e) {
                log.debug("[stats:byTime] aggregation on {} failed: {}", coll, e.getMessage());
                continue;
            }
            for (Document d : results.getMappedResults()) {
                String day = (String) d.get("_id");
                if (day == null) continue;
                long c = asLong(d.get("count"), 0);
                daily.merge(day, c, Long::sum);
                total += c;
            }
        }
        // sort keys ascending
        Map<String, Long> sorted = new LinkedHashMap<>();
        new java.util.TreeMap<>(daily).forEach(sorted::put);
        return base("byTime", company, tenant, total)
                .counts(sorted)
                .build();
    }

    // ---------------- helpers ----------------

    private StatsResponse.StatsResponseBuilder base(String dimension, String company, String tenant, long total) {
        return StatsResponse.builder()
                .companyName(company)
                .wso2Tenant(tenant)
                .dimension(dimension)
                .timestamp(Instant.now())
                .totalCount(total);
    }

    private static org.springframework.data.mongodb.core.query.Query matchTenant(String company, String tenant) {
        Criteria c = matchCriteria(company, tenant);
        return org.springframework.data.mongodb.core.query.Query.query(c);
    }

    private static Criteria matchCriteria(String company, String tenant) {
        Criteria c = new Criteria();
        List<Criteria> parts = new ArrayList<>();
        if (StringUtils.hasText(company)) parts.add(Criteria.where("companyName").is(company));
        if (StringUtils.hasText(tenant)) parts.add(Criteria.where("wso2Tenant").is(tenant));
        if (parts.isEmpty()) return c;
        if (parts.size() == 1) return parts.get(0);
        return c.andOperator(parts.toArray(new Criteria[0]));
    }

    private static Long asLong(Object o, long fallback) {
        if (o == null) return fallback;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return fallback; }
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return null; }
    }

    private static Instant asInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof java.util.Date d) return d.toInstant();
        try { return Instant.parse(o.toString()); } catch (Exception e) { return null; }
    }
}
