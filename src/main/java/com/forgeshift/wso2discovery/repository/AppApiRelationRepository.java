package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.domain.AppApiRelation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AppApiRelationRepository extends MongoRepository<AppApiRelation, String> {

    List<AppApiRelation> findByCompanyNameAndWso2TenantAndApplicationId(
            String companyName, String wso2Tenant, String applicationId);

    List<AppApiRelation> findByCompanyNameAndWso2TenantAndApiId(
            String companyName, String wso2Tenant, String apiId);

    Page<AppApiRelation> findByCompanyNameAndWso2Tenant(
            String companyName, String wso2Tenant, Pageable pageable);
}
