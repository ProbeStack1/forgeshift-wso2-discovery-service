package com.forgeshift.wso2discovery.repository;

import com.forgeshift.wso2discovery.domain.Wso2TenantProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface Wso2TenantProfileRepository extends MongoRepository<Wso2TenantProfile, String> {

    Optional<Wso2TenantProfile> findByCompanyNameAndProfileName(String companyName, String profileName);

    /**
     * All profiles for {@code companyName} whose single
     * {@code defaultWso2Tenant} matches the requested tenant, most
     * recently updated first. The token service filters this list by
     * status==ACTIVE (treating null/missing status as ACTIVE for
     * back-compat) and takes the first hit.
     */
    List<Wso2TenantProfile> findByCompanyNameAndDefaultWso2TenantOrderByUpdatedAtDesc(
            String companyName, String defaultWso2Tenant);

    Page<Wso2TenantProfile> findByCompanyName(String companyName, Pageable pageable);
}
