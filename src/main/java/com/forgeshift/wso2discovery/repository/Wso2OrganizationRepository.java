package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.domain.Wso2OrganizationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface Wso2OrganizationRepository extends MongoRepository<Wso2OrganizationEntity, String> {

    Optional<Wso2OrganizationEntity> findByCompanyNameAndWso2Tenant(String companyName, String wso2Tenant);

    Page<Wso2OrganizationEntity> findByCompanyName(String companyName, Pageable pageable);
}
