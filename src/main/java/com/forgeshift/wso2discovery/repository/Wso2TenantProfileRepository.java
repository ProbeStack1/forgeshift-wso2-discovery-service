package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.domain.Wso2TenantProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface Wso2TenantProfileRepository extends MongoRepository<Wso2TenantProfile, String> {

    /**
     * Legacy single-profile lookup. Returns whichever document Mongo finds first
     * when only one profile per (companyName, wso2Tenant) exists. For the
     * multi-profile shape introduced by the profile-config service, prefer
     * {@link #findByCompanyNameAndWso2TenantOrderByUpdatedAtDesc} and filter
     * by status in the caller (token service).
     */
    Optional<Wso2TenantProfile> findByCompanyNameAndWso2Tenant(String companyName, String wso2Tenant);

    /**
     * All profiles for a tenant, most recently updated first. The token service
     * filters this list by status==ACTIVE (treating null/missing status as
     * ACTIVE for back-compat) and takes the first hit.
     */
    List<Wso2TenantProfile> findByCompanyNameAndWso2TenantOrderByUpdatedAtDesc(String companyName, String wso2Tenant);

    Page<Wso2TenantProfile> findByCompanyName(String companyName, Pageable pageable);
}
