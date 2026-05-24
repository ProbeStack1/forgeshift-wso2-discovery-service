package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.domain.RevisionCounter;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RevisionCounterRepository extends MongoRepository<RevisionCounter, String> {

    Optional<RevisionCounter> findByCompanyNameAndWso2Tenant(String companyName, String wso2Tenant);
}
