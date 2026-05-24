package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.domain.DiscoverySnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for {@link DiscoverySnapshot} documents addressed by
 * collection name at runtime.
 *
 * Storage-specific implementation lives in {@link MongoBaseDiscoveryRepository}.
 */
public interface BaseDiscoveryRepository {

    /** Idempotent upsert keyed by snapshot.id. */
    void upsert(String collectionName, DiscoverySnapshot snapshot);

    Optional<DiscoverySnapshot> findById(String collectionName, String id);

    List<DiscoverySnapshot> findByDiscoveryId(String collectionName, String discoveryId);

    List<DiscoverySnapshot> findByCompanyAndTenant(String collectionName, String companyName, String wso2Tenant);

    long countByDiscoveryId(String collectionName, String discoveryId);

    long deleteByDiscoveryId(String collectionName, String discoveryId);
}
