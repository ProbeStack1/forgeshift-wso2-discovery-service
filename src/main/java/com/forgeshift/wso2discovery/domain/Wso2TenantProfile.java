package com.forgeshift.wso2discovery.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Per-tenant WSO2 connection profile stored in the {@code profiles}
 * collection (configurable via {@code forgeshift.discovery.profiles-collection}).
 *
 * <p><b>Schema-shared with the profile-config service.</b> The profile-config
 * service is the writer / schema owner. This service is a reader only. We
 * intentionally do NOT declare a {@code @CompoundIndex} here because:
 * <ul>
 *   <li>The profile-config service owns the uniqueness constraint
 *       {@code (companyName, wso2Tenant, profileName)}.</li>
 *   <li>If both services tried to create competing indexes on boot, MongoDB
 *       would reject the second one with an "Index already exists with a
 *       different name" error.</li>
 * </ul>
 *
 * <p>When multiple profile documents exist for the same tenant, the token
 * service picks the one with {@code status == ACTIVE} that has the most
 * recent {@code updatedAt}.
 *
 * <p><b>Secrets:</b> stored in plain text for the MVP. Masked on read in all
 * REST responses. TODO: encrypt at rest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("profiles")
public class Wso2TenantProfile {

    /** Composite id written by the profile-config service:
     *  {@code <companyName>|<wso2Tenant>|<profileName>}. */
    @Id
    private String id;

    private String companyName;
    private String wso2Tenant;

    /** Name of the profile, e.g. "primary" / "readonly". */
    private String profileName;

    /** WSO2 base URL for this tenant, e.g. https://wso2.example.com:9443. */
    private String wso2BaseUrl;

    /** WSO2 admin username for password-grant. */
    private String username;

    /** WSO2 admin password. */
    private String password;

    /** OAuth2 client_id registered against this WSO2 tenant. */
    private String clientId;

    /** OAuth2 client_secret. */
    private String clientSecret;

    private boolean trustSelfSigned;

    /**
     * Lifecycle status. ACTIVE profiles are eligible for token acquisition;
     * INACTIVE / SUSPENDED profiles are skipped by the resolver. Documents
     * written by older callers without a status field are treated as ACTIVE.
     */
    private String status;

    /** Optional notes for operators. */
    private String notes;

    private Instant createdAt;
    private Instant updatedAt;
}
