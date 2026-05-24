package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.domain.DiscoveryJob;
import com.forgeshift.wso2discovery.domain.DiscoveryState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DiscoveryJobRepository extends MongoRepository<DiscoveryJob, String> {

    Page<DiscoveryJob> findByState(DiscoveryState state, Pageable pageable);

    Page<DiscoveryJob> findByWso2Tenant(String wso2Tenant, Pageable pageable);

    Page<DiscoveryJob> findByCompanyName(String companyName, Pageable pageable);
}
