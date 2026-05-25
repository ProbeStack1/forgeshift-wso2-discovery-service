package com.forgeshift.wso2discovery.service;

import com.forgeshift.wso2discovery.client.Wso2Credentials;
import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.forgeshift.wso2discovery.domain.Wso2TenantProfile;
import com.forgeshift.wso2discovery.repository.Wso2TenantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

/**
 * Looks up per-tenant connection credentials in the {@code wso2_profiles}
 * collection and falls back to the static {@code Wso2Properties} when no
 * profile exists.
 *
 * <p>Profiles are stored one-per-(companyName, profileName) with a
 * {@code tenants} array listing every WSO2 tenant they manage. The resolver
 * finds the right credentials by matching the requested tenant against that
 * array — Mongo's array-equality semantics treat
 * {@code findByCompanyNameAndTenants(c, t)} as "contains t".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2TenantProfileService {

    private final Wso2TenantProfileRepository repository;
    private final Wso2Properties staticProps;

    /**
     * Resolve credentials for ({@code companyName}, {@code wso2Tenant}).
     *
     * Lookup precedence:
     *   1. Profile row in {@code wso2_profiles} whose {@code tenants} list
     *      contains {@code wso2Tenant}, status ACTIVE, most recent update.
     *   2. Static {@code forgeshift.wso2.*} (the single-tenant fallback).
     *
     * When either input is blank, falls straight through to the static path.
     */
    public Wso2Credentials resolve(String companyName, String wso2Tenant) {
        if (StringUtils.hasText(companyName) && StringUtils.hasText(wso2Tenant)) {
            java.util.List<Wso2TenantProfile> all =
                    repository.findByCompanyNameAndTenantsOrderByUpdatedAtDesc(companyName, wso2Tenant);
            Wso2TenantProfile chosen = all.stream()
                    .filter(p -> p.getStatus() == null
                            || "ACTIVE".equalsIgnoreCase(p.getStatus()))
                    .findFirst()
                    .orElse(null);
            if (chosen != null) {
                log.debug("Using profile credentials for ({}, {}) - profileName={} status={}",
                        companyName, wso2Tenant, chosen.getProfileName(), chosen.getStatus());
                return Wso2Credentials.builder()
                        .source("profile")
                        .baseUrl(chosen.getWso2BaseUrl())
                        .username(chosen.getUsername())
                        .password(chosen.getPassword())
                        .clientId(chosen.getClientId())
                        .clientSecret(chosen.getClientSecret())
                        .trustSelfSigned(chosen.isTrustSelfSigned())
                        .build();
            }
        }
        log.debug("Using static credentials (no profile for company={} tenant={})", companyName, wso2Tenant);
        return Wso2Credentials.builder()
                .source("static")
                .baseUrl(staticProps.getBaseUrl())
                .username(staticProps.getUsername())
                .password(staticProps.getPassword())
                .clientId(staticProps.getClientId())
                .clientSecret(staticProps.getClientSecret())
                .trustSelfSigned(staticProps.isTrustSelfSigned())
                .build();
    }

    // -------------------- CRUD for admin endpoints --------------------

    public Wso2TenantProfile save(Wso2TenantProfile p) {
        Instant now = Instant.now();
        if (p.getId() == null || p.getId().isBlank()) {
            p.setId(p.getCompanyName() + "|" + p.getProfileName());
        }
        if (repository.existsById(p.getId())) {
            p.setUpdatedAt(now);
        } else {
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
        }
        return repository.save(p);
    }

    public Optional<Wso2TenantProfile> get(String companyName, String profileName) {
        return repository.findByCompanyNameAndProfileName(companyName, profileName);
    }

    public void delete(String companyName, String profileName) {
        repository.findByCompanyNameAndProfileName(companyName, profileName)
                .ifPresent(p -> repository.deleteById(p.getId()));
    }

    public Wso2TenantProfileRepository repository() {
        return repository;
    }
}
