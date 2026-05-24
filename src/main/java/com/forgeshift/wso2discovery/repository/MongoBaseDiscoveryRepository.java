package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data Mongo implementation of {@link BaseDiscoveryRepository}.
 *
 * Uses {@link MongoTemplate} directly so we can address any collection by
 * name without a per-resource {@code @Document}-annotated class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoBaseDiscoveryRepository implements BaseDiscoveryRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public void upsert(String collectionName, DiscoverySnapshot s) {
        if (s.getId() == null || s.getId().isBlank()) {
            throw new IllegalArgumentException("Snapshot id is required for upsert");
        }
        Update update = new Update()
                .set("companyName", s.getCompanyName())
                .set("wso2Tenant", s.getWso2Tenant())
                .set("discoveryId", s.getDiscoveryId())
                .set("revision", s.getRevision())
                .set("resourceType", s.getResourceType())
                .set("sourceId", s.getSourceId())
                .set("sourceName", s.getSourceName())
                .set("sourceVersion", s.getSourceVersion())
                .set("payload", s.getPayload())
                .set("metadata", s.getMetadata())
                .set("snapshotAt", s.getSnapshotAt() != null ? s.getSnapshotAt() : Instant.now());

        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(s.getId())),
                update,
                collectionName);
    }

    @Override
    public Optional<DiscoverySnapshot> findById(String collectionName, String id) {
        DiscoverySnapshot found = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(id)),
                DiscoverySnapshot.class,
                collectionName);
        return Optional.ofNullable(found);
    }

    @Override
    public List<DiscoverySnapshot> findByDiscoveryId(String collectionName, String discoveryId) {
        return mongoTemplate.find(
                Query.query(Criteria.where("discoveryId").is(discoveryId)),
                DiscoverySnapshot.class,
                collectionName);
    }

    @Override
    public List<DiscoverySnapshot> findByCompanyAndTenant(String collectionName, String companyName, String wso2Tenant) {
        return mongoTemplate.find(
                Query.query(Criteria.where("companyName").is(companyName).and("wso2Tenant").is(wso2Tenant)),
                DiscoverySnapshot.class,
                collectionName);
    }

    @Override
    public long countByDiscoveryId(String collectionName, String discoveryId) {
        return mongoTemplate.count(
                Query.query(Criteria.where("discoveryId").is(discoveryId)),
                collectionName);
    }

    @Override
    public long deleteByDiscoveryId(String collectionName, String discoveryId) {
        return mongoTemplate.remove(
                Query.query(Criteria.where("discoveryId").is(discoveryId)),
                collectionName).getDeletedCount();
    }
}
